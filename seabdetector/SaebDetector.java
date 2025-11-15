package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
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
        // Assumindo que OPENCV_DLL_PATH está definida em Constants.java para carregar a DLL
        System.load(OPENCV_DLL_PATH_HOME); 
    }

    // 1. CONSTANTE PARA CONTROLE DE LOTES (CUSTOMIZÁVEL)
    private static final int BATCH_SIZE = 100;

    // ESTA CONSTANTE É CRÍTICA: Número de folhas que compõem 1 caderno.
    private static final int FILES_PER_BOOKLET = 5;

    // NOVO: Pulmão/Buffer de Processamento (Para agregar folhas por caderno)
    private static final Map<String, Map<String, Map<String, String>>> pulmaoRespostas = new LinkedHashMap<>();

    // NOVO: Contador que avança APENHAS quando um caderno está COMPLETO.
    private final static Map<String, Integer> respondenteBookletIndex = new HashMap<>();

    // Mapa FINAL de resultados consolidados (1 linha por caderno completo)
    private static final Map<String, Map<String, String>> finalRespostasPorBooklet = new LinkedHashMap<>();

    // Mapa de dados QR (apenas para metadados de saída)
    private static final Map<String, QrData> dadosQrPorBooklet = new LinkedHashMap<>();


    public static void main(String[] args) {

        File outputDirFile = new File(PATH_OUTPUT_DIR);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }

        List<Alternativa> todasAlternativas = ConfigLoader.loadAlternativas(PATH_CONFIG);
        Map<String, FolhaTemplate> templates = ConfigLoader.loadTemplates(PATH_TEMPLATES);

        if (templates.isEmpty()) {
            System.err.println("ERRO FATAL: Nenhum template carregado. Impossível realizar o alinhamento inicial.");
            return;
        }

        // Template genérico para o primeiro alinhamento (que nos dá o recorte)
        FolhaTemplate templateGenerico = templates.values().iterator().next(); 

        long totalProcessingTimeMs = 0;
        int processedCount = 0;

        Set<String> todasAsQuestoes = todasAlternativas.stream()
                .map(a -> a.questao)
                .collect(Collectors.toCollection(LinkedHashSet::new));


        Path pastaEntradaPath = Paths.get(PATH_INPUT_DIR);
        List<File> todosOsArquivos = Collections.emptyList();

        try (Stream<Path> pathStream = Files.walk(pastaEntradaPath)) {

            todosOsArquivos = pathStream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String nome = p.getFileName().toString().toLowerCase();
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
            //System.out.println("Nenhum arquivo de imagem encontrado em: " + PATH_INPUT_DIR);
            templates.values().forEach(FolhaTemplate::release);
            return;
        }

        Collections.sort(todosOsArquivos);

        System.out.printf("Encontrados %d arquivos. Processando em lotes de %d...\n", todosOsArquivos.size(), BATCH_SIZE);

        for (int i = 0; i < todosOsArquivos.size(); i += BATCH_SIZE) {
            int batchStart = i;
            int batchEnd = Math.min(i + BATCH_SIZE, todosOsArquivos.size());

            List<File> batchAtual = todosOsArquivos.subList(batchStart, batchEnd);

            System.out.printf("\n--- PROCESSANDO LOTE %d (Arquivos %d a %d) ---\n", (i / BATCH_SIZE + 1), (batchStart + 1), batchEnd);

            for (File arquivoImagem : batchAtual) {

                String nomeArquivoBase = arquivoImagem.getName().substring(0, arquivoImagem.getName().lastIndexOf('.'));
                System.out.printf("\n➡ Processando %s\n", arquivoImagem.getName());
                long totalStartTime = System.nanoTime();
                long stepStartTime, stepEndTime;

                Mat imagem = null; // Imagem bruta original (base)
                Mat imagemParaProcessamento = null; // Imagem bruta final (0° ou 180°), usada para Warp
                Mat recorteFinal = null; // Imagem alinhada final para OMR
                
                QrData dadosQR = null;

                try {
                    // 4.1. Carregar Imagem Bruta
                    stepStartTime = System.nanoTime();
                    imagem = Imgcodecs.imread(arquivoImagem.getAbsolutePath());
                    if (imagem.empty()) continue;
                    stepEndTime = System.nanoTime();
                    //System.out.printf("  [TIMER] 1. Carregar Imagem:      %d ms\n", (stepEndTime - stepStartTime) / 1_000_000);
                    
                    // --- 4.2. Detecção de Orientação na Imagem Bruta ---
                    
                    // TENTATIVA 1: Orientação 0° (Bruta)
                    stepStartTime = System.nanoTime();
                    dadosQR = QRCodeReader.extractAndParseFromRawImage(imagem, PATH_OUTPUT_DIR, nomeArquivoBase); 
                    
                    if (dadosQR == null) {
                        //System.out.println("  ⚠ QR Code não lido na orientação 0° BRUTA. Tentando rotação de 180°...");
                        
                        // Rotaciona a IMAGEM BRUTA
                        Mat imagemRotacionada = rotate180(imagem);
                        
                        // TENTATIVA 2: Orientação 180° (Rotacionada Bruta)
                        dadosQR = QRCodeReader.extractAndParseFromRawImage(imagemRotacionada, PATH_OUTPUT_DIR, nomeArquivoBase + "_ROTATED");
                        
                        // Define qual imagem bruta será usada para o Warp (Rotacionada)
                        imagemParaProcessamento = imagemRotacionada;
                        
                        // Libera a imagem original se a rotacionada for usada, ou vice-versa
                        if (dadosQR == null) {
                            imagemRotacionada.release(); // Rotacionada falhou, libera.
                        } else {
                            imagem.release(); // Se rotacionada funcionou, libera a original.
                        }
                    } else {
                        // Orientação 0° funcionou.
                        imagemParaProcessamento = imagem;
                    }
                    
                    stepEndTime = System.nanoTime();
                    //System.out.printf("  [TIMER] 2. Detecção QR (Total):  %d ms\n", (stepEndTime - stepStartTime) / 1_000_000);
                    
                    // Fim da detecção de orientação
                    if (dadosQR == null) {
                        System.err.println("  ⚠ ERRO FATAL: Não foi possível ler o QR Code em nenhuma orientação. Folha descartada.");
                        continue;
                    }

                    // 4.3. Alinhar a Imagem (Warp) NA ORIENTAÇÃO CORRETA
                    stepStartTime = System.nanoTime();
                    
                    // 1. Detecção de âncoras na imagem bruta com orientação correta
                    List<Point> pontosAncorasBrutos = AnchorDetector.findAnchorPoints(imagemParaProcessamento, PATH_OUTPUT_DIR, nomeArquivoBase);
                    
                    if (pontosAncorasBrutos == null) {
                         System.err.println("  ⚠ ERRO FATAL: Âncoras não encontradas na imagem após correção de orientação.");
                         continue;
                    }

                    // 2. Warp: Cria o recorte alinhado usando a imagem com orientação correta
                    recorteFinal = AnchorDetector.warpImage(imagemParaProcessamento, templateGenerico, pontosAncorasBrutos, PATH_OUTPUT_DIR, nomeArquivoBase, true);

                    stepEndTime = System.nanoTime();
                    //System.out.printf("  [TIMER] 3. Alinhamento (Warp):   %d ms\n", (stepEndTime - stepStartTime) / 1_000_000);

                    if (recorteFinal == null) continue;

                    // 4.4. Puxar Configurações específicas e OMR
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

                    // 4.5. Ler Bolhas (OMR)
                    stepStartTime = System.nanoTime();
                    Map<String, String> respostasDaFolha = OmrReader.readBubbles(recorteFinal, alternativasFolha, PATH_OUTPUT_DIR, nomeArquivoBase);
                    stepEndTime = System.nanoTime();
                    //System.out.printf("  [TIMER] 5. Ler Bolhas (OMR):     %d ms\n", (stepEndTime - stepStartTime) / 1_000_000);

                    // 4.6. LÓGICA DO PULMÃO E CONSOLIDAÇÃO
                    
                    // Salva o resultado visual
                    //String nomeArquivoSaida = OUTPUT_IMAGE_PREFIX + respondenteID + "_" + folhaNome.replace(" ", "") + ".jpg";
                    //Imgcodecs.imwrite(PATH_OUTPUT_DIR + nomeArquivoSaida, recorteFinal); 
                    
                    // Lógica do Pulmão/Consolidação 
                    int currentBookletIndex = respondenteBookletIndex.getOrDefault(respondenteID, 0);
                    String bookletId = respondenteID + "_" + String.format("%03d", currentBookletIndex);
                    Map<String, Map<String, String>> bookletSheets = pulmaoRespostas.computeIfAbsent(bookletId, k -> new LinkedHashMap<>());

                    if (bookletSheets.containsKey(folhaNome)) {
                        //System.out.printf("  ⚠ Aviso: Folha '%s' já processada para o Caderno %s. Pulando.\n", folhaNome, bookletId);
                        continue;
                    }

                    bookletSheets.put(folhaNome, respostasDaFolha);

                    if (bookletSheets.size() == FILES_PER_BOOKLET) {
                        //System.out.printf("  *** CADERNO COMPLETO DETECTADO: %s ***\n", bookletId);
                        Map<String, String> consolidatedAnswers = new LinkedHashMap<>();
                        for (Map<String, String> sheetAnswers : bookletSheets.values()) {
                            consolidatedAnswers.putAll(sheetAnswers);
                        }

                        finalRespostasPorBooklet.put(bookletId, consolidatedAnswers);
                        dadosQrPorBooklet.put(bookletId, dadosQR);
                        pulmaoRespostas.remove(bookletId);
                        respondenteBookletIndex.put(respondenteID, currentBookletIndex + 1);
                    } else {
                        //System.out.printf("  [DEB] Folha '%s' adicionada. Faltam %d.\n", folhaNome, (FILES_PER_BOOKLET - bookletSheets.size()));
                        dadosQrPorBooklet.putIfAbsent(bookletId, dadosQR);
                    }


                    String vetorRespostas = respostasDaFolha.values().stream().collect(Collectors.joining(","));
                    //System.out.printf("  ✓ Respostas lidas: %s\n", vetorRespostas);

                    long totalEndTime = System.nanoTime();
                    long totalDurationMs = (totalEndTime - totalStartTime) / 1_000_000;
                    //System.out.printf("  ⏱️ --- Tempo Total da Folha: %d ms ---\n", totalDurationMs);

                    totalProcessingTimeMs += totalDurationMs;
                    processedCount++;
                } catch (Exception e) {
                    System.err.printf("  ❌ Erro inesperado ao processar folha: %s - %s\n", arquivoImagem.getName(), e.getMessage());
                    e.printStackTrace();
                } finally {
                    // Liberação final das matrizes
                    // Libera 'imagem' ou 'imagemParaProcessamento' (que não é o mesmo objeto que 'imagem' se tiver sido rotacionado)
                    if (imagem != null && imagem.empty() == false && imagemParaProcessamento != imagem) imagem.release();
                    if (imagemParaProcessamento != null) imagemParaProcessamento.release();
                    if (recorteFinal != null) recorteFinal.release(); 
                }
            }
        } 

        writeOrganizedResults(finalRespostasPorBooklet, dadosQrPorBooklet, todasAsQuestoes);
        printFinalSummary(totalProcessingTimeMs, processedCount);
        templates.values().forEach(FolhaTemplate::release);
    }
    
    /**
     * Rotaciona a Mat em 180 graus.
     * @param src A Mat de origem.
     * @return A Mat rotacionada (NOVA MAT).
     */
    private static Mat rotate180(Mat src) {
        Mat dst = new Mat();
        Core.flip(src, dst, -1); 
        return dst;
    }


    private static void writeOrganizedResults(Map<String, Map<String, String>> respostasPorBooklet, Map<String, QrData> dadosQrPorBooklet, Set<String> todasAsQuestoes) {
        //System.out.println("\n\n--- Gerando arquivo de respostas organizado (" + OUTPUT_TXT_FILE_ORGANIZED + ") ---");
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

            // 2. Itera sobre os resultados consolidados
            for (Map.Entry<String, Map<String, String>> entry : respostasPorBooklet.entrySet()) {

                String bookletId = entry.getKey();
                Map<String, String> respostasTotais = entry.getValue();

                QrData dadosQRRef = dadosQrPorBooklet.get(bookletId);

                StringBuilder dataLine = new StringBuilder();

                dataLine.append(dadosQRRef != null ? dadosQRRef.instituicao : "N/A").append(",");
                dataLine.append(dadosQRRef != null ? dadosQRRef.respondente : "N/A"); // Usa o ID original do respondente

                // Adiciona as respostas, na ordem do cabeçalho
                for (String questao : todasAsQuestoes) {
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
            System.out.printf("  Total de Folhas Processadas: %d\n", processedCount);
            System.out.printf("  Tempo Total Geral: %d ms\n", totalProcessingTimeMs);
            System.out.printf("  Tempo Médio por Folha: %d ms\n", averageTime);
            System.out.println("===================================");
            System.out.printf("  Arquivo de Respostas Organizado (1 linha por respondente): %s\n", PATH_OUTPUT_DIR + OUTPUT_TXT_FILE_ORGANIZED);
        } else {
            System.out.println("\nProcessamento concluído. Nenhuma folha foi processada.");
        }
    }
}