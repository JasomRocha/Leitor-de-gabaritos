package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static seabdetector.Constants.*;
import static seabdetector.DataModels.*;

public class SaebDetector {

    static {
        System.load(OPENCV_DLL_PATH);
    }

    // 1. CONSTANTE PARA CONTROLE DE LOTES (CUSTOMIZÁVEL)
    private static final int BATCH_SIZE = 100;

    // ESTA CONSTANTE É CRÍTICA: Número de folhas que compõem 1 caderno.
    private static final int FILES_PER_BOOKLET = 5;

    // NOVO: O Pulmão/Buffer de Processamento.
    // Chave: BookletId Único (Ex: "0001_000")
    // Valor: Map<FolhaNome, Map<Questao, Resposta>> -> Mapeia as folhas parciais do caderno
    private static final Map<String, Map<String, Map<String, String>>> pulmaoRespostas = new LinkedHashMap<>();

    // NOVO: Contador que avança APENAS quando um caderno está COMPLETO.
    // Chave: RespondenteID (Ex: "0001")
    // Valor: Próximo índice de caderno a ser usado (Ex: 0, 1, 2...)
    private static final Map<String, Integer> respondenteBookletIndex = new HashMap<>();

    // Mapa FINAL de resultados consolidados (1 linha por caderno completo)
    // Chave: BookletId Único (Ex: "0001_000")
    // Valor: Map<Questao, Resposta> (Todas as respostas consolidadas)
    private static final Map<String, Map<String, String>> finalRespostasPorBooklet = new LinkedHashMap<>();

    // Mapa de dados QR (apenas para metadados de saída)
    private static final Map<String, QrData> dadosQrPorBooklet = new LinkedHashMap<>();


    public static void main(String[] args) {

        File outputDirFile = new File(PATH_OUTPUT_DIR);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }

        List<Alternativa> todasAlternativas = seabdetector.ConfigLoader.loadAlternativas(PATH_CONFIG);
        Map<String, FolhaTemplate> templates = seabdetector.ConfigLoader.loadTemplates(PATH_TEMPLATES);

        if (templates.isEmpty()) {
            System.err.println("ERRO FATAL: Nenhum template carregado. Impossível realizar o alinhamento inicial.");
            return;
        }

        FolhaTemplate templateGenerico = templates.values().iterator().next();

        long totalProcessingTimeMs = 0;
        int processedCount = 0;

        Set<String> todasAsQuestoes = todasAlternativas.stream()
                .map(a -> a.questao)
                .collect(Collectors.toCollection(LinkedHashSet::new));


        Path pastaEntradaPath = Paths.get(PATH_INPUT_DIR);
        List<File> todosOsArquivos = Collections.emptyList();

        // CORREÇÃO: Usando Files.walk() para processar pastas e subpastas (recursivo)
        try (Stream<Path> pathStream = Files.walk(pastaEntradaPath)) {

            todosOsArquivos = pathStream
                    // 1. Filtra para garantir que seja um arquivo regular (e não uma pasta)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String nome = p.getFileName().toString().toLowerCase();
                        // 2. Filtra pelas extensões de imagem: JPG, PNG, TIF, TIFF e JPEG
                        return nome.endsWith(".jpg") || nome.endsWith(".jpeg") || nome.endsWith(".png") || nome.endsWith(".tif") || nome.endsWith(".tiff");
                    })
                    .map(Path::toFile)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            System.err.println("Erro ao listar arquivos na pasta de entrada: " + e.getMessage());
            templates.values().forEach(FolhaTemplate::release);
            return;
        }

        if (todosOsArquivos.isEmpty()) {
            System.out.println("Nenhum arquivo de imagem encontrado em: " + PATH_INPUT_DIR);
            templates.values().forEach(FolhaTemplate::release);
            return;
        }

        // CORREÇÃO: Ordena os arquivos para garantir que a lógica de agregação por bookletId funcione.
        Collections.sort(todosOsArquivos);

        System.out.println("Encontrados " + todosOsArquivos.size() + " arquivos. Processando em lotes de " + BATCH_SIZE + "...");

        // Apenas o contador de arquivos processados, sem uso para a lógica de bookletId
        int successfulProcessedFiles = 0;

        // 4. Loop de Processamento em Lotes (A iteração final agora percorre toda a lista)
        for (int i = 0; i < todosOsArquivos.size(); i += BATCH_SIZE) {
            int batchStart = i;
            int batchEnd = Math.min(i + BATCH_SIZE, todosOsArquivos.size());

            List<File> batchAtual = todosOsArquivos.subList(batchStart, batchEnd);

            System.out.println("\n--- PROCESSANDO LOTE " + (i / BATCH_SIZE + 1) + " (Arquivos " + (batchStart + 1) + " a " + batchEnd + ") ---");

            // 4.1. Loop de processamento de imagem (o loop interno processa 1 por vez)
            for (File arquivoImagem : batchAtual) {

                String nomeArquivoBase = arquivoImagem.getName().substring(0, arquivoImagem.getName().lastIndexOf('.'));
                System.out.println("\n➡ Processando " + arquivoImagem.getName());
                long totalStartTime = System.nanoTime();
                long stepStartTime, stepEndTime;

                Mat imagem = null;
                Mat recorte = null;

                try {
                    // 4.1. Carregar Imagem
                    stepStartTime = System.nanoTime();
                    imagem = Imgcodecs.imread(arquivoImagem.getAbsolutePath());
                    if (imagem.empty()) continue;
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 1. Carregar Imagem:      %d ms", (stepEndTime - stepStartTime) / 1_000_000));

                    // 4.2. Detectar Âncoras
                    stepStartTime = System.nanoTime();
                    List<Point> pontosAncorasBrutos = seabdetector.AnchorDetector.findAnchorPoints(imagem, PATH_OUTPUT_DIR, nomeArquivoBase);
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 2. Detecção de Âncoras:  %d ms", (stepEndTime - stepStartTime) / 1_000_000));

                    if (pontosAncorasBrutos == null) continue;

                    // 4.3. Alinhar a Imagem (Warp)
                    stepStartTime = System.nanoTime();
                    recorte = seabdetector.AnchorDetector.warpImage(imagem, templateGenerico, pontosAncorasBrutos, PATH_OUTPUT_DIR, nomeArquivoBase, true);
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 3. Alinhamento (Warp):   %d ms", (stepEndTime - stepStartTime) / 1_000_000));

                    if (recorte == null) continue;

                    // 4.4. Extrair QR Code
                    stepStartTime = System.nanoTime();
                    QrData dadosQR = seabdetector.QRCodeReader.extractAndParseFromWarped(recorte);
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 4. Extrair QR Code:      %d ms", (stepEndTime - stepStartTime) / 1_000_000));

                    if (dadosQR == null) continue;

                    // 4.5. Puxar Configurações específicas
                    String respondenteID = dadosQR.getRespondenteKey();
                    String folhaNome = dadosQR.folhaNome;
                    FolhaTemplate templateCorreto = templates.get(folhaNome);
                    List<Alternativa> alternativasFolha = todasAlternativas.stream()
                            .filter(a -> a.folha.equals(folhaNome))
                            .collect(Collectors.toList());

                    if (templateCorreto == null || alternativasFolha.isEmpty()) {
                        if (templateCorreto == null) System.err.println("  ⚠ ERRO FATAL: Não há gabarito (template) para '" + folhaNome + "'.");
                        if (alternativasFolha.isEmpty()) System.err.println("  ⚠ ERRO FATAL: Não há perguntas para '" + folhaNome + "' em config.txt.");
                        continue;
                    }

                    // 4.6. Ler Bolhas (OMR)
                    stepStartTime = System.nanoTime();
                    // *** AQUI É A CHAVE: CHAMA O MÉTODO COM OS PARÂMETROS DE DEBUG ***
                    Map<String, String> respostasDaFolha = seabdetector.OmrReader.readBubbles(recorte, alternativasFolha, PATH_OUTPUT_DIR, nomeArquivoBase);
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 5. Ler Bolhas (OMR):     %d ms", (stepEndTime - stepStartTime) / 1_000_000));

                    // 4.7. LÓGICA DO PULMÃO E CONSOLIDAÇÃO

                    // 1. Obtém o índice atual para este respondente (inicia em 0)
                    int currentBookletIndex = respondenteBookletIndex.getOrDefault(respondenteID, 0);
                    // 2. Chave única de AGREGACÃO (o caderno que estamos tentando montar)
                    String bookletId = respondenteID + "_" + String.format("%03d", currentBookletIndex);

                    // 3. Coloca a folha no pulmão (agrupada por folhaNome)
                    Map<String, Map<String, String>> bookletSheets = pulmaoRespostas
                            .computeIfAbsent(bookletId, k -> new LinkedHashMap<>());

                    // Se a folha já foi processada para este caderno, pula (evita duplicação)
                    if (bookletSheets.containsKey(folhaNome)) {
                        System.out.println("  ⚠ Aviso: Folha '" + folhaNome + "' já processada para o Caderno " + bookletId + ". Pulando.");
                        continue;
                    }

                    bookletSheets.put(folhaNome, respostasDaFolha);

                    // 4. Verifica se o caderno está COMPLETO
                    if (bookletSheets.size() == FILES_PER_BOOKLET) {
                        System.out.println("  *** CADERNO COMPLETO DETECTADO: " + bookletId + " ***");

                        // 5. Consolida todas as respostas em um único mapa
                        Map<String, String> consolidatedAnswers = new LinkedHashMap<>();
                        for (Map<String, String> sheetAnswers : bookletSheets.values()) {
                            consolidatedAnswers.putAll(sheetAnswers);
                        }

                        // 6. Move os dados consolidados para o mapa FINAL de saída
                        finalRespostasPorBooklet.put(bookletId, consolidatedAnswers);
                        dadosQrPorBooklet.put(bookletId, dadosQR);

                        // 7. Limpa o pulmão e avança o contador para o próximo caderno
                        pulmaoRespostas.remove(bookletId);
                        respondenteBookletIndex.put(respondenteID, currentBookletIndex + 1);
                    } else {
                        System.out.println("  ... Folha '" + folhaNome + "' adicionada ao pulmão. Faltam " + (FILES_PER_BOOKLET - bookletSheets.size()) + ".");
                        // Se o QR data do respondente não foi salvo para este ID, salve uma cópia
                        dadosQrPorBooklet.putIfAbsent(bookletId, dadosQR);
                    }

                    String vetorRespostas = respostasDaFolha.values().stream().collect(Collectors.joining(","));
                    System.out.println("  ✓ Respostas lidas: " + vetorRespostas);

                    long totalEndTime = System.nanoTime();
                    long totalDurationMs = (totalEndTime - totalStartTime) / 1_000_000;
                    System.out.println(String.format("  ⏱️ --- Tempo Total da Folha: %d ms ---", totalDurationMs));

                    totalProcessingTimeMs += totalDurationMs;
                    processedCount++;
                    successfulProcessedFiles++;

                } catch (Exception e) {
                    System.err.println("  Erro inesperado ao processar folha: " + arquivoImagem.getName() + " - " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    // CRUCIAL: Liberação de memória nativa
                    if (imagem != null) imagem.release();
                    if (recorte != null) recorte.release();
                }
            } // Fim do loop do lote (batchAtual)
        } // Fim do loop de batches

        // 5. ETAPA FINAL: CONCATENAR E ESCREVER O ARQUIVO TXT
        writeOrganizedResults(finalRespostasPorBooklet, dadosQrPorBooklet, todasAsQuestoes);

        // 6. Imprime a Média Final
        printFinalSummary(totalProcessingTimeMs, processedCount);

        // 7. Limpeza
        templates.values().forEach(FolhaTemplate::release);
    }

    // Método modificado para usar o mapa chaveado pelo BookletId
    private static void writeOrganizedResults(Map<String, Map<String, String>> respostasPorBooklet, Map<String, QrData> dadosQrPorBooklet, Set<String> todasAsQuestoes) {
        System.out.println("\n\n--- Gerando arquivo de respostas organizado (" + OUTPUT_TXT_FILE_ORGANIZED + ") ---");
        String caminhoTXT = PATH_OUTPUT_DIR + OUTPUT_TXT_FILE_ORGANIZED;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(caminhoTXT, false))) {

            // 1. Cabeçalho
            StringBuilder header = new StringBuilder();
            header.append("id_instituicao,id_respondente");
            for (String questao : todasAsQuestoes) {
                header.append(",").append(questao);
            }
            bw.write(header.toString());
            bw.newLine();

            // 2. Itera sobre os resultados consolidados (um loop por CADEIRNO/BOOKLET)
            // Agora o mapa contém APENAS cadernos completos, garantindo as 100 linhas corretas.
            for (Map.Entry<String, Map<String, String>> entry : respostasPorBooklet.entrySet()) {

                String bookletId = entry.getKey();
                Map<String, String> respostasTotais = entry.getValue();

                // O QrData é único por bookletId
                QrData dadosQRRef = dadosQrPorBooklet.get(bookletId);

                StringBuilder dataLine = new StringBuilder();

                // id_instituicao e id_respondente vêm do QR Code original
                dataLine.append(dadosQRRef != null ? dadosQRRef.instituicao : "N/A").append(",");
                dataLine.append(dadosQRRef != null ? dadosQRRef.respondente : "N/A"); // Usa o ID original do respondente

                // Adiciona as respostas, na ordem do cabeçalho
                for (String questao : todasAsQuestoes) {
                    // Concatena as respostas de TODAS as folhas deste CADERNO (BookletId)
                    dataLine.append(",").append(respostasTotais.getOrDefault(questao, ""));
                }

                bw.write(dataLine.toString());
                bw.newLine();
            }

        } catch (IOException e) {
            System.err.println("Erro ao salvar respostas organizadas: " + e.getMessage());
        }
    }

    private static void printFinalSummary(long totalProcessingTimeMs, int processedCount) {
        if (processedCount > 0) {
            long averageTime = totalProcessingTimeMs / processedCount;
            System.out.println(String.format("\n\n===== PROCESSAMENTO CONCLUÍDO ====="));
            System.out.println(String.format("  Total de Folhas Processadas: %d", processedCount));
            System.out.println(String.format("  Tempo Total Geral: %d ms", totalProcessingTimeMs));
            System.out.println(String.format("  Tempo Médio por Folha: %d ms", averageTime));
            System.out.println("===================================");
            System.out.println("  Arquivo de Respostas Organizado (1 linha por respondente): " + PATH_OUTPUT_DIR + OUTPUT_TXT_FILE_ORGANIZED);
        } else {
            System.out.println("\nProcessamento concluído. Nenhuma folha foi processada.");
        }
    }
}