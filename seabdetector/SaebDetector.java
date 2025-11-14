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

    public static void main(String[] args) {
        
        // 1. Configuração inicial
        File outputDirFile = new File(PATH_OUTPUT_DIR);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }

        // 2. Carrega todas as configurações
        List<Alternativa> todasAlternativas = ConfigLoader.loadAlternativas(PATH_CONFIG);
        Map<String, FolhaTemplate> templates = ConfigLoader.loadTemplates(PATH_TEMPLATES);

        if (templates.isEmpty()) {
            System.err.println("ERRO FATAL: Nenhum template carregado. Impossível realizar o alinhamento inicial.");
            return;
        }
        FolhaTemplate templateGenerico = templates.values().iterator().next(); 
        
        long totalProcessingTimeMs = 0;
        int processedCount = 0;
        
        Map<String, Map<String, String>> respostasPorRespondente = new TreeMap<>();
        Map<String, QrData> dadosQrPorRespondente = new TreeMap<>();
        
        Set<String> todasAsQuestoes = todasAlternativas.stream()
            .map(a -> a.questao)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        
        // =================================================================
        // 3. FLUXO OTIMIZADO: LISTAGEM E BATCHING
        // =================================================================
        Path pastaEntradaPath = Paths.get(PATH_INPUT_DIR);
        List<File> todosOsArquivos = Collections.emptyList(); // Inicializa

        // 3.1. Lista todas as referências de arquivos (usando Stream para robustez na listagem)
        try (Stream<Path> pathStream = Files.list(pastaEntradaPath)) {
            
            todosOsArquivos = pathStream
                .filter(p -> {
                    String nome = p.getFileName().toString().toLowerCase();
                    return nome.endsWith(".jpg") || nome.endsWith(".png");
                })
                .map(Path::toFile)
                .collect(Collectors.toList()); // Coleta as referências para permitir o batching
            
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
        
        System.out.println("Encontrados " + todosOsArquivos.size() + " arquivos. Processando em lotes de " + BATCH_SIZE + "...");
        
        // 4. Processa em lotes de BATCH_SIZE em BATCH_SIZE
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
                    // 4.1. Carregar Imagem (CARREGA A MATRIZ GRANDE DE PIXELS)
                    stepStartTime = System.nanoTime();
                    imagem = Imgcodecs.imread(arquivoImagem.getAbsolutePath());
                    if (imagem.empty()) continue;
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 1. Carregar Imagem:      %d ms", (stepEndTime - stepStartTime) / 1_000_000));
                    
                    // 4.2. Detectar Âncoras
                    stepStartTime = System.nanoTime();
                    List<Point> pontosAncorasBrutos = AnchorDetector.findAnchorPoints(imagem, PATH_OUTPUT_DIR, nomeArquivoBase);
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 2. Detecção de Âncoras:  %d ms", (stepEndTime - stepStartTime) / 1_000_000));

                    if (pontosAncorasBrutos == null) continue;

                    // 4.3. Alinhar a Imagem (Warp)
                    stepStartTime = System.nanoTime();
                    recorte = AnchorDetector.warpImage(imagem, templateGenerico, pontosAncorasBrutos, PATH_OUTPUT_DIR, nomeArquivoBase, true);
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 3. Alinhamento (Warp):   %d ms", (stepEndTime - stepStartTime) / 1_000_000));
                    
                    if (recorte == null) continue;

                    // 4.4. Extrair QR Code
                    stepStartTime = System.nanoTime();
                    QrData dadosQR = QRCodeReader.extractAndParseFromWarped(recorte);
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
                    Map<String, String> respostasDaFolha = OmrReader.readBubbles(recorte, alternativasFolha);
                    stepEndTime = System.nanoTime();
                    System.out.println(String.format("  [TIMER] 5. Ler Bolhas (OMR):     %d ms", (stepEndTime - stepStartTime) / 1_000_000));
                    
                    // 4.7. Salva o resultado visual
                    String nomeArquivoSaida = OUTPUT_IMAGE_PREFIX + respondenteID + "_" + folhaNome.replace(" ", "") + ".jpg";
                    Imgcodecs.imwrite(PATH_OUTPUT_DIR + nomeArquivoSaida, recorte); 
                    
                    // 4.8. Armazenar resultados consolidados
                    respostasPorRespondente.computeIfAbsent(respondenteID, k -> new LinkedHashMap<>()).putAll(respostasDaFolha);  
                    dadosQrPorRespondente.putIfAbsent(respondenteID, dadosQR);
                    
                    String vetorRespostas = respostasDaFolha.values().stream().collect(Collectors.joining(","));
                    System.out.println("  ✓ Respostas lidas: " + vetorRespostas);
                    
                    long totalEndTime = System.nanoTime();
                    long totalDurationMs = (totalEndTime - totalStartTime) / 1_000_000;
                    System.out.println(String.format("  ⏱️ --- Tempo Total da Folha: %d ms ---", totalDurationMs));
                    
                    totalProcessingTimeMs += totalDurationMs;
                    processedCount++;
                
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
        writeOrganizedResults(respostasPorRespondente, dadosQrPorRespondente, todasAsQuestoes);
        
        // 6. Imprime a Média Final
        printFinalSummary(totalProcessingTimeMs, processedCount);
        
        // 7. Limpeza
        templates.values().forEach(FolhaTemplate::release);
    }
    private static void writeOrganizedResults(Map<String, Map<String, String>> respostasPorRespondente, Map<String, QrData> dadosQrPorRespondente, Set<String> todasAsQuestoes) {
        // ... (Mesma implementação do método writeOrganizedResults) ...
        System.out.println("\n\n--- Gerando arquivo de respostas organizado (" + OUTPUT_TXT_FILE_ORGANIZED + ") ---");
        String caminhoTXT = PATH_OUTPUT_DIR + OUTPUT_TXT_FILE_ORGANIZED;
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(caminhoTXT, false))) {
            
            // Constrói o Cabeçalho Único
            StringBuilder header = new StringBuilder();           
            header.append("id_instituicao,id_respondente");
            for (String questao : todasAsQuestoes) {
                header.append(",").append(questao);
            }
            bw.write(header.toString());
            bw.newLine();
            
            // Itera sobre os respondentes ordenados (pelo ID do respondente)
            for (Map.Entry<String, Map<String, String>> entry : respostasPorRespondente.entrySet()) {
                
                String respondenteID = entry.getKey();
                Map<String, String> respostasTotais = entry.getValue();
                QrData dadosQRRef = dadosQrPorRespondente.get(respondenteID);
                
                StringBuilder dataLine = new StringBuilder();
                                        
                dataLine.append(dadosQRRef != null ? dadosQRRef.instituicao : "N/A").append(",");
                dataLine.append(respondenteID);
                
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