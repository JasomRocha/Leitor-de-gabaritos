package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;
import java.util.stream.Collectors;
import java.io.File;

/**
 * Aplicação principal para Reconhecimento Óptico de Marcas (OMR) e leitura de QR Code.
 * O fluxo de trabalho é:
 * 1. Ler o QR Code da imagem bruta para identificar a folha.
 * 2. Encontrar as 4 âncoras de alinhamento.
 * 3. "Desentortar" (alinhar) a imagem usando transformação de perspectiva.
 * 4. Ler as bolhas de resposta na imagem alinhada.
 * 5. Salvar os resultados.
 */
public class SaebDetector {

    private static final String OPENCV_DLL_PATH = System.getProperty("opencv.dll.path", "C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll");
    
    private static final String S = File.separator;
    private static final String PATH_CONFIG = "testes" + S + "config.txt";
    private static final String PATH_TEMPLATES = "testes" + S + "templates.txt";
    private static final String PATH_INPUT_DIR = "testes" + S + "entradas" + S + "entradas_qr" + S;
    private static final String PATH_OUTPUT_DIR = "testes" + S + "saidas" + S + "saidas_qr" + S;
    private static final String OUTPUT_TXT_FILE_ORGANIZED = "respostas_organizadas.txt"; 
    private static final String OUTPUT_IMAGE_PREFIX = "resultado_";
    private static final String OUTPUT_FAIL_PREFIX = "falha_";
    private static final String OUTPUT_CROP_PREFIX = "recorte_";
    
    // --- Parâmetros de Detecção de Âncora ---
    private static final int ANCHOR_SEARCH_SIZE = 200;
    private static final double ANCHOR_MIN_AREA = 300.0;
    private static final double ANCHOR_MAX_AREA = 5000.0;
    private static final double ANCHOR_APPROX_EPSILON = 0.02;
    private static final int ADAPTIVE_THRESH_BLOCK_SIZE = 15;
    private static final int ADAPTIVE_THRESH_C = 10;
    private static final double ANCHOR_ASPECT_TOLERANCE = 1.2;
    
    // --- Parâmetros de Detecção de Bolha ---
    private static final int BUBBLE_RADIUS = 10;
    private static final double RELATIVE_MARK_THRESHOLD = 25.0;
    
    // --- Cores (para debug) ---
    private static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);
    private static final Scalar COLOR_RED = new Scalar(0, 0, 255);
    private static final Scalar COLOR_BLUE = new Scalar(255, 0, 0);

    static {
        System.load(OPENCV_DLL_PATH);
    }

    // --- Classes Internas ---
    static class Alternativa {
        String folha, questao, opcao;
        int x, y;
        public Alternativa(String f, String q, String o, int x, int y) {
            this.folha = f; this.questao = q; this.opcao = o; this.x = x; this.y = y;
        }
    }
    static class FolhaTemplate {
        Size idealSize;
        MatOfPoint2f idealPoints;
        public FolhaTemplate(Size size, MatOfPoint2f points) {
            this.idealSize = size;
            this.idealPoints = points;
        }
        public void release() {
            if (idealPoints != null) idealPoints.release();
        }
    }
    static class QrData {
        String instituicao, respondente, folhaNome, tipoProva, ano, qrTextoCompleto;
        public QrData(String i, String r, String f, String t, String a, String full) {
            this.instituicao = i; this.respondente = r; this.folhaNome = f;
            this.tipoProva = t; this.ano = a; this.qrTextoCompleto = full;
        }
        @Override
        public String toString() {
            return "Instituicao: " + instituicao + ", Respondente: " + respondente + 
                   ", Folha: " + folhaNome + ", Tipo: " + tipoProva + ", Ano: " + ano;
        }
        // Retorna a chave de ordenação: RESPONDENTE_FOLHA (ex: 0001_01)
        public String getSortingKey() {
            // Extrai o número da folha, garantindo que "FOLHA 1" venha antes de "FOLHA 10"
            String numStr = folhaNome.replaceAll("[^0-9]", "");
            int num = 0;
            try {
                num = Integer.parseInt(numStr);
            } catch (NumberFormatException ignored) {}
            // Formato '0001_01', '0001_02' para ordenação correta
            return respondente + "_" + String.format("%02d", num);
        }
        public String getRespondenteKey() {
             return respondente;
        }
    }


    // --------------------------------------------------------------------------------
    // 🔹 MÉTODO PRINCIPAL
    // --------------------------------------------------------------------------------

    public static void main(String[] args) {
        File outputDirFile = new File(PATH_OUTPUT_DIR);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }

        // 1. Carrega todas as configurações uma vez (Leitura de Disco)
        List<Alternativa> todasAlternativas = lerConfiguracao(PATH_CONFIG);
        Map<String, FolhaTemplate> templates = lerTemplates(PATH_TEMPLATES);

        // 2. Lista todos os arquivos de imagem na pasta de entrada
        File pastaEntrada = new File(PATH_INPUT_DIR);
        File[] arquivos = pastaEntrada.listFiles((dir, nome) -> nome.toLowerCase().endsWith(".jpg") || nome.toLowerCase().endsWith(".png"));

        if (arquivos == null || arquivos.length == 0) {
            System.out.println("Nenhum arquivo de imagem encontrado em: " + PATH_INPUT_DIR);
            return;
        }
        
        System.out.println("Encontrados " + arquivos.length + " arquivos de imagem para processar...");

        long totalProcessingTimeMs = 0;
        int processedCount = 0;
        long stepStartTime, stepEndTime;
        
        Map<String, Map<String, String>> respostasPorRespondente = new TreeMap<>();
        Map<String, QrData> dadosQrPorRespondente = new TreeMap<>();
        
        // Obtém o conjunto único de questões para o cabeçalho (em ordem)
        Set<String> todasAsQuestoes = todasAlternativas.stream()
            .map(a -> a.questao)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        
        // 3. Processa cada arquivo de imagem
        for (File arquivoImagem : arquivos) {
            System.out.println("\n➡ Processando " + arquivoImagem.getName());
            long totalStartTime = System.nanoTime(); 
            
            Mat imagem = null;
            Mat recorte = null;
            Mat cinza = null;
            
            try {
                // --- 1. Carregar Imagem (Leitura de Disco) ---
                stepStartTime = System.nanoTime();
                imagem = Imgcodecs.imread(arquivoImagem.getAbsolutePath());
                if (imagem.empty()) {
                    System.err.println("  Erro ao carregar a imagem: " + arquivoImagem.getName());
                    continue;
                }
                stepEndTime = System.nanoTime();
                System.out.println(String.format("  [TIMER] 1. Carregar Imagem:    %d ms", (stepEndTime - stepStartTime) / 1_000_000));
                
                // --- 2. Extrair QR Code ---
                stepStartTime = System.nanoTime();
                String qrDataBruta = extrairQRCodeDaImagemBruta(imagem);
                QrData dadosQR = null;
                if (qrDataBruta != null) {
                    dadosQR = parseQrCode(qrDataBruta);
                }
                stepEndTime = System.nanoTime();
                System.out.println(String.format("  [TIMER] 2. Extrair QR Code:    %d ms", (stepEndTime - stepStartTime) / 1_000_000));

                if (dadosQR == null) {
                    System.err.println("  ⚠ ERRO FATAL: Não foi possível ler ou decodificar o QR Code.");
                    continue; 
                }
                
                // --- 3. Puxar Configurações ---
                String respondenteID = dadosQR.getRespondenteKey();
                String folhaNome = dadosQR.folhaNome;
                FolhaTemplate template = templates.get(folhaNome);
                
                if (template == null) {
                    System.err.println("  ⚠ ERRO FATAL: O QR Code indica '" + folhaNome + "', mas não há gabarito (template) para ela em templates.txt.");
                    continue;
                }
                List<Alternativa> alternativasFolha = todasAlternativas.stream()
                        .filter(a -> a.folha.equals(folhaNome))
                        .collect(Collectors.toList());
                if (alternativasFolha.isEmpty()) {
                    System.err.println("  ⚠ ERRO FATAL: O QR Code indica '" + folhaNome + "', mas não há perguntas para ela em config.txt.");
                    continue;
                }
                
                // --- 4. Detectar Âncoras e Alinhar ---
                stepStartTime = System.nanoTime();
                recorte = detectarETransformarAncoras(imagem, template, PATH_OUTPUT_DIR, folhaNome);
                stepEndTime = System.nanoTime();
                System.out.println(String.format("  [TIMER] 3. Alinhar (Âncoras): %d ms", (stepEndTime - stepStartTime) / 1_000_000));

                if (recorte == null) {
                    System.err.println("  ⚠ ERRO FATAL: Não foi possível alinhar a folha (âncoras não encontradas).");
                    continue;
                }

                // --- 5. Ler Bolhas (OMR) ---
                stepStartTime = System.nanoTime();
                cinza = new Mat();
                Imgproc.cvtColor(recorte, cinza, Imgproc.COLOR_BGR2GRAY);
                Map<String, List<Alternativa>> porQuestao = new LinkedHashMap<>(); 
                for (Alternativa a : alternativasFolha) {
                    porQuestao.computeIfAbsent(a.questao, k -> new ArrayList<>()).add(a);
                }
                
                Map<String, String> respostasDaFolha = new LinkedHashMap<>();
                
                for (String questao : porQuestao.keySet()) {
                    List<Alternativa> lista = porQuestao.get(questao);
                    List<Alternativa> marcadas = new ArrayList<>();
                    Map<Alternativa, Double> medias = new HashMap<>();
                    double minMedia = 255.0, maxMedia = 0.0;
                    for (Alternativa alt : lista) {
                        int x = alt.x;
                        int y = alt.y;
                        int x0 = Math.max(x - BUBBLE_RADIUS, 0);
                        int y0 = Math.max(y - BUBBLE_RADIUS, 0);
                        int larguraRecorte = Math.min(BUBBLE_RADIUS * 2, cinza.width() - x0);
                        int alturaRecorte = Math.min(BUBBLE_RADIUS * 2, cinza.height() - y0);
                        Rect regiao = new Rect(x0, y0, larguraRecorte, alturaRecorte);
                        Mat sub = new Mat(cinza, regiao);
                        Scalar mediaScalar = Core.mean(sub);
                        double media = mediaScalar.val[0];
                        medias.put(alt, media);
                        minMedia = Math.min(minMedia, media);
                        maxMedia = Math.max(maxMedia, media);
                        sub.release();
                    }
                    boolean algoMarcado = (maxMedia - minMedia) > RELATIVE_MARK_THRESHOLD;
                    if (algoMarcado) {
                        double limiarMarcado = minMedia + (RELATIVE_MARK_THRESHOLD / 2.0);
                        for (Alternativa alt : lista) {
                            if (medias.get(alt) <= limiarMarcado) {
                                marcadas.add(alt);
                            }
                        }
                    }
                    for (Alternativa alt : lista) {
                        int x = alt.x;
                        int y = alt.y;
                        int x0 = Math.max(x - BUBBLE_RADIUS, 0);
                        int y0 = Math.max(y - BUBBLE_RADIUS, 0);
                        int larguraRecorte = Math.min(BUBBLE_RADIUS * 2, cinza.width() - x0);
                        int alturaRecorte = Math.min(BUBBLE_RADIUS * 2, cinza.height() - y0);
                        Scalar cor = marcadas.contains(alt) ? COLOR_GREEN : COLOR_RED;
                        Imgproc.rectangle(recorte, new Point(x0, y0), new Point(x0 + larguraRecorte, y0 + alturaRecorte), cor, 2);
                    }
                    String respostaFinal;
                    if (marcadas.isEmpty()) respostaFinal = "";
                    else if (marcadas.size() > 1) respostaFinal = "?";
                    else respostaFinal = traduzAlternativa(marcadas.get(0).opcao);
                    
                    // Armazena a resposta no mapa desta folha
                    respostasDaFolha.put(questao, respostaFinal);
                }
                                
                respostasPorRespondente.computeIfAbsent(respondenteID, k -> new LinkedHashMap<>()).putAll(respostasDaFolha);               
                dadosQrPorRespondente.putIfAbsent(respondenteID, dadosQR);
                
                stepEndTime = System.nanoTime();
                System.out.println(String.format("  [TIMER] 4. Ler Bolhas (OMR):    %d ms", (stepEndTime - stepStartTime) / 1_000_000));
                
                // --- 6. Salva o resultado visual (Escrita de Disco OBRIGATÓRIA) ---
                stepStartTime = System.nanoTime();
                String nomeArquivoSaida = OUTPUT_IMAGE_PREFIX + respondenteID + "_" + folhaNome.replace(" ", "") + ".jpg";
                Imgcodecs.imwrite(PATH_OUTPUT_DIR + nomeArquivoSaida, recorte);
                
                stepEndTime = System.nanoTime();
                System.out.println(String.format("  [TIMER] 5. Salvar Saídas:      %d ms", (stepEndTime - stepStartTime) / 1_000_000));
                
                String vetorRespostas = respostasDaFolha.values().stream().collect(Collectors.joining(","));
                System.out.println("  ✓ Respostas lidas: " + vetorRespostas);
                
                // --- 7. Tempo Total da Folha ---
                long totalEndTime = System.nanoTime();
                long totalDurationMs = (totalEndTime - totalStartTime) / 1_000_000;
                System.out.println(String.format("  ⏱️ --- Tempo Total da Folha: %d ms ---", totalDurationMs));
                
                totalProcessingTimeMs += totalDurationMs;
                processedCount++;
            
            } catch (Exception e) {
                System.err.println("  Erro inesperado ao processar folha: " + arquivoImagem.getName() + " - " + e.getMessage());
            } finally {
                if (imagem != null) imagem.release();
                if (recorte != null) recorte.release();
                if (cinza != null) cinza.release();
            }
        } 
        
        // --------------------------------------------------------------------------------
        // 🔹 ETAPA FINAL: CONCATENAR E ESCREVER O ARQUIVO TXT (UMA LINHA POR RESPONDENTE)
        // --------------------------------------------------------------------------------
        
        System.out.println("\n\n--- Gerando arquivo de respostas organizado (" + OUTPUT_TXT_FILE_ORGANIZED + ") ---");
        String caminhoTXT = PATH_OUTPUT_DIR + OUTPUT_TXT_FILE_ORGANIZED;
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(caminhoTXT, false))) {
            
            // 1. Constrói o Cabeçalho Único
            StringBuilder header = new StringBuilder();           
            header.append("id_instituicao,id_respondente");
            for (String questao : todasAsQuestoes) {
                header.append(",").append(questao);
            }
            bw.write(header.toString());
            bw.newLine();
            
            // 2. Itera sobre os respondentes ordenados (pelo ID do respondente)
            for (Map.Entry<String, Map<String, String>> entry : respostasPorRespondente.entrySet()) {
                
                String respondenteID = entry.getKey();
                Map<String, String> respostasTotais = entry.getValue();
                QrData dadosQRRef = dadosQrPorRespondente.get(respondenteID);
                
                // Inicializa a linha de dados
                StringBuilder dataLine = new StringBuilder();
                              
                dataLine.append(dadosQRRef != null ? dadosQRRef.instituicao : "N/A").append(",");
                dataLine.append(respondenteID);
                
                // Adiciona as respostas, na ordem do cabeçalho
                for (String questao : todasAsQuestoes) {
                    // Se a questão não foi encontrada em nenhuma das folhas do respondente (improvável se o config estiver certo), retorna ""
                    dataLine.append(",").append(respostasTotais.getOrDefault(questao, "")); 
                }
                
                bw.write(dataLine.toString());
                bw.newLine();
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao salvar respostas organizadas: " + e.getMessage());
        }
                  
        // --- 8. Imprime a Média Final ---
        if (processedCount > 0) {
            long averageTime = totalProcessingTimeMs / processedCount;
            System.out.println(String.format("\n\n===== PROCESSAMENTO CONCLUÍDO ====="));
            System.out.println(String.format("  Total de Folhas Processadas: %d", processedCount));
            System.out.println(String.format("  Tempo Total Geral: %d ms", totalProcessingTimeMs));
            System.out.println(String.format("  Tempo Médio por Folha: %d ms", averageTime));
            System.out.println("===================================");
            System.out.println("  Arquivo de Respostas Organizado (1 linha por respondente): " + caminhoTXT);
        } else {
            System.out.println("\nProcessamento concluído. Nenhuma folha foi processada.");
        }
        
        templates.values().forEach(FolhaTemplate::release);
    }
    
    // --- FUNÇÕES AUXILIARES ---

    private static String extrairQRCodeDaImagemBruta(Mat image) {
        System.out.println("  🔎 Tentando localizar QR Code na imagem bruta...");
        int[] tamanhos = {250, 150}; 
        int margemExtra = 20;
        
        for (int recorteLado : tamanhos) {
            int x = Math.max((int) image.width() - recorteLado - margemExtra, 0);
            int y = 0;
            int w = Math.min(recorteLado + margemExtra, image.width() - x);
            int h = Math.min(recorteLado + margemExtra, image.height() - y);
            Rect rect = new Rect(x, y, w, h);
            Mat qrRecortado = null;
            Mat enlarged = null;
            Mat gray = null;
            MatOfByte mob = new MatOfByte();
            
            try {
                qrRecortado = new Mat(image, rect);
                enlarged = new Mat();
                Imgproc.resize(qrRecortado, enlarged,
                        new Size(qrRecortado.width() * 5, qrRecortado.height() * 5),
                        0, 0, Imgproc.INTER_CUBIC);
                gray = new Mat();
                Imgproc.cvtColor(enlarged, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.GaussianBlur(gray, gray, new Size(3, 3), 0);
                Imgcodecs.imencode(".png", gray, mob);
                InputStream is = new ByteArrayInputStream(mob.toArray());
                BufferedImage bufferedImage = ImageIO.read(is);
                is.close();
                LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                Result result = new MultiFormatReader().decode(bitmap);
                
                System.out.println("  ✅ QR Code detectado com recorte de " + recorteLado + "px!");
                return result.getText();

            } catch (NotFoundException e) {
                System.out.println("    ...Nenhum QR encontrado no recorte de " + recorteLado + "px.");
            } catch (Exception e) {
                System.err.println("    ❌ Erro (Exception) ao ler QR Code: " + e.getMessage());
            } finally {
                if (qrRecortado != null) qrRecortado.release();
                if (enlarged != null) enlarged.release();
                if (gray != null) gray.release();
                if (mob != null) mob.release();
            }
        }
        return null; 
    }

    private static QrData parseQrCode(String qrTexto) {
        if (qrTexto == null || qrTexto.length() < 12) {  
            System.err.println("  ⚠ Erro de QR Code: texto é nulo ou curto. ('" + qrTexto + "')");
            return null;
        }
        try {          
            String instituicao = qrTexto.substring(0, 5);
            String respondente = qrTexto.substring(5, 9);
            String folhaNumStr = qrTexto.substring(9, 11);
            String folhaNome = "FOLHA " + Integer.parseInt(folhaNumStr);
            String tipoProva = qrTexto.substring(11, 12);
            String ano = qrTexto.substring(12, 16);
            
            QrData dados = new QrData(instituicao, respondente, folhaNome, tipoProva, ano, qrTexto);
            System.out.println("  ✓ QR Code decodificado: " + dados);
            return dados;

        } catch (Exception e) {
            System.err.println("  ⚠ Erro ao decodificar o texto do QR Code: " + e.getMessage());
            return null;
        }
    }
    
    private static Mat detectarETransformarAncoras(Mat imagem, FolhaTemplate template, String outputDir, String folha) {
        int largura = imagem.cols();
        int altura = imagem.rows();
        int w = ANCHOR_SEARCH_SIZE, h = ANCHOR_SEARCH_SIZE;

        Rect[] regioes = new Rect[]{
                new Rect(0, 0, w, h), 
                new Rect(largura - w, 0, w, h), 
                new Rect(0, altura - h, w, h), 
                new Rect(largura - w, altura - h, w, h)
        };
        
        String[] regionNames = {"Superior Esquerdo", "Superior Direito", "Inferior Esquerdo", "Inferior Direito"};
        List<Rect> ancorasRects = new ArrayList<>();

        for (int i = 0; i < regioes.length; i++) {
            Rect roi = regioes[i];
            String regionName = regionNames[i];
            
            Mat regiao = new Mat(imagem, roi);  
            Mat gray = new Mat();
            Mat thresh = new Mat();
            Mat hierarchy = new Mat();
            List<MatOfPoint> contornos = new ArrayList<>();

            Imgproc.cvtColor(regiao, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.adaptiveThreshold(gray, thresh, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY_INV, ADAPTIVE_THRESH_BLOCK_SIZE, ADAPTIVE_THRESH_C);
            
            Imgproc.findContours(thresh.clone(), contornos, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            
            Rect melhorCaixa = null;
            double maxAreaEncontrada = 0;
            
            for (MatOfPoint contorno : contornos) {
                double area = Imgproc.contourArea(contorno);
                if (area < ANCHOR_MIN_AREA || area > ANCHOR_MAX_AREA) {
                    contorno.release();
                    continue;
                }
                MatOfPoint2f contorno2f = new MatOfPoint2f(contorno.toArray());
                MatOfPoint2f aprox = new MatOfPoint2f();
                double perimetro = Imgproc.arcLength(contorno2f, true);
                Imgproc.approxPolyDP(contorno2f, aprox, ANCHOR_APPROX_EPSILON * perimetro, true);
                
                if (aprox.total() == 4) {
                    MatOfPoint aproxPt = new MatOfPoint(aprox.toArray());
                    Rect caixa = Imgproc.boundingRect(aproxPt);
                    double aspect = (caixa.width > caixa.height) ? 
                                     (double)caixa.width / caixa.height : 
                                     (double)caixa.height / caixa.width;
                                         
                    if (aspect > ANCHOR_ASPECT_TOLERANCE) {
                        aproxPt.release(); contorno.release(); contorno2f.release(); aprox.release();
                        continue;
                    }
                    
                    if (area > maxAreaEncontrada) {
                        maxAreaEncontrada = area;
                        caixa.x += roi.x;  
                        caixa.y += roi.y;
                        melhorCaixa = caixa;
                    }
                    aproxPt.release();
                }
                contorno.release(); contorno2f.release(); aprox.release();
            }
            
            if (melhorCaixa != null) {
                System.out.println("    ✅ Encontrada âncora (" + regionName + ")! Área: " + String.format("%.1f", maxAreaEncontrada));
                ancorasRects.add(melhorCaixa);
                Imgproc.rectangle(imagem, new Point(melhorCaixa.x, melhorCaixa.y), 
                                     new Point(melhorCaixa.x + melhorCaixa.width, melhorCaixa.y + melhorCaixa.height), 
                                     COLOR_BLUE, 3);
            }
            
            regiao.release(); gray.release(); thresh.release(); hierarchy.release();
        }
        
        System.out.println("  🏁 Detecção de âncoras finalizada. Total: " + ancorasRects.size());
        if (ancorasRects.size() != 4) {
            System.err.println("  ⚠ ERRO FATAL: não foram encontradas 4 âncoras.");
            Imgcodecs.imwrite(outputDir + OUTPUT_FAIL_PREFIX + folha + ".jpg", imagem);
            return null;
        }

        List<Point> srcPointsList = new ArrayList<>();
        for (Rect r : ancorasRects) {
            srcPointsList.add(new Point(r.x + r.width / 2.0, r.y + r.height / 2.0));
        }
        Collections.sort(srcPointsList, (p1, p2) -> Double.compare(p1.x + p1.y, p2.x + p2.y));
        Point tl = srcPointsList.get(0);
        Point br = srcPointsList.get(3);
        Point p2 = srcPointsList.get(1);
        Point p3 = srcPointsList.get(2);
        Point tr, bl;
        if (p2.x > p3.x) { tr = p2; bl = p3; } else { tr = p3; bl = p2; }
        MatOfPoint2f src_points = new MatOfPoint2f(tl, tr, bl, br);
        MatOfPoint2f dst_points = template.idealPoints;
        Mat M = Imgproc.getPerspectiveTransform(src_points, dst_points);
        Mat warpedImage = new Mat();
        Size warpedSize = template.idealSize;
        Scalar fillColor = new Scalar(245, 245, 245);
        Imgproc.warpPerspective(imagem, warpedImage, M, warpedSize, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, fillColor);
        
        Imgcodecs.imwrite(outputDir + OUTPUT_CROP_PREFIX + folha + ".jpg", warpedImage);
        System.out.println("  ✓ Imagem desentortada e salva.");
        src_points.release();
        M.release();
        return warpedImage;
    }
    
    
    private static Map<String, FolhaTemplate> lerTemplates(String caminhoTemplates) {
        Map<String, FolhaTemplate> templates = new HashMap<>();
        String folhaAtual = null;
        try (BufferedReader br = new BufferedReader(new FileReader(caminhoTemplates))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[FOLHA")) {
                    folhaAtual = line.replace("[", "").replace("]", "").trim();
                    continue;
                }
                if (folhaAtual != null && line.startsWith("DADOS: ")) {
                    String[] partes = line.substring(7).split(";");
                    if (partes.length == 6) {
                        int w = Integer.parseInt(partes[0].trim());
                        int h = Integer.parseInt(partes[1].trim());
                        String[] tl = partes[2].trim().split(",");
                        String[] tr = partes[3].trim().split(",");
                        String[] bl = partes[4].trim().split(",");
                        String[] brCoords = partes[5].trim().split(",");
                        if (tl.length != 2 || tr.length != 2 || bl.length != 2 || brCoords.length != 2) {
                            System.err.println("⚠ Erro de formato de coordenada para " + folhaAtual);
                            continue;
                        }
                        Point pt_tl = new Point(Integer.parseInt(tl[0].trim()), Integer.parseInt(tl[1].trim()));
                        Point pt_tr = new Point(Integer.parseInt(tr[0].trim()), Integer.parseInt(tr[1].trim()));
                        Point pt_bl = new Point(Integer.parseInt(bl[0].trim()), Integer.parseInt(bl[1].trim()));
                        Point pt_br = new Point(Integer.parseInt(brCoords[0].trim()), Integer.parseInt(brCoords[1].trim()));
                        Size idealSize = new Size(w, h);
                        MatOfPoint2f idealPoints = new MatOfPoint2f(pt_tl, pt_tr, pt_bl, pt_br);
                        templates.put(folhaAtual, new FolhaTemplate(idealSize, idealPoints));
                    } else {
                        System.err.println("⚠ Aviso: Linha 'DADOS' mal formatada para " + folhaAtual + ". Esperados 6 campos, encontrados " + partes.length);
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erro ao ler templates: " + e.getMessage());
        }
        return templates;
    }
    
    
    private static List<Alternativa> lerConfiguracao(String caminhoConfig) {
        List<Alternativa> lista = new ArrayList<>();
        String folhaAtual = "Desconhecida";
        try (BufferedReader br = new BufferedReader(new FileReader(caminhoConfig))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[FOLHA")) {
                    folhaAtual = line.replace("[", "").replace("]", "").trim();
                    continue;
                }
                String[] partes = line.split(";");
                if (partes.length == 4) {
                    lista.add(new Alternativa(folhaAtual, partes[0], partes[1],
                            Integer.parseInt(partes[2]), Integer.parseInt(partes[3])));
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler configuração: " + e.getMessage());
        }
        return lista;
    }

   /**
     * Traduz a opção de entrada
     */
    private static String traduzAlternativa(String alt) {
        switch (alt.toUpperCase()) {
            case "S": return "Sim";
            case "N": return "Nao";
            case "NQN": return "Nunca ou quase nunca";
            case "DVQ": return "De vez em quando";
            case "SQS": return "Sempre ou quase sempre";
            case "NU": return "Não uso meu tempo pra isso";
            case "M1H": return "Menos de 1 hora";
            case "E12H": return "Entre 1 e 2 horas";
            case "M2H": return "Mais de 2 horas";
            case "TE": return "Todos eles";
            case "AMP": return "A maior parte deles";
            case "PD": return "Poucos deles";
            case "ND": return "Nenhum deles";
            case "CT": return "Concordo totalmente";
            case "NEN": return "Nenhum";
            case "CNC": return "Concordo";
            case "DSC": return "Discordo";
            case "DT": return "Discordo totalmente";
            default: return alt;
        }
    }
}