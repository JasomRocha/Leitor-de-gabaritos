package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class SaebSimples {

    // ---------------------------------------------------------------------------
    // 🔹 CONFIGURAÇÕES GERAIS
    // ---------------------------------------------------------------------------
    private static final String OPENCV_DLL_PATH = "C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll";
    private static final String PATH_CONFIG = "testes\\config.txt";
    private static final String PATH_TEMPLATES = "testes\\templates.txt";
    private static final String PATH_INPUT_DIR = "testes\\entradas\\entradas_problematicas\\";
    private static final String PATH_OUTPUT_DIR = "testes\\saidas\\saidas simples\\";
    private static final String OUTPUT_TXT_FILE_ORGANIZED = "respostas_organizadas_teste.txt"; 

    private static final int ANCHOR_SEARCH_SIZE = 250;
    private static final double ANCHOR_MIN_AREA = 260.0;
    private static final double ANCHOR_MAX_AREA = 5000.0;
    private static final double ANCHOR_APPROX_EPSILON = 0.02;
    private static final double ANCHOR_ASPECT_TOLERANCE = 1.2;

    private static final int ADAPTIVE_THRESH_BLOCK_SIZE = 15;
    private static final int ADAPTIVE_THRESH_C = 10;

    private static final int BUBBLE_RADIUS = 10;
    private static final double RELATIVE_MARK_THRESHOLD = 25.0;

    private static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);
    private static final Scalar COLOR_RED = new Scalar(0, 0, 255);

    static {
        System.load(OPENCV_DLL_PATH);
        new File(PATH_OUTPUT_DIR).mkdirs();
    }

    // ---------------------------------------------------------------------------
    // ESTRUTURAS DE DADOS
    // ---------------------------------------------------------------------------

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
            this.idealSize = size; this.idealPoints = points;
        }
    }

    // ---------------------------------------------------------------------------
    // 🔹 MÉTODO PRINCIPAL
    // ---------------------------------------------------------------------------

    public static void main(String[] args) {
        long inicioTotal = System.currentTimeMillis();

        // Variáveis fixas, pois o código não lê QR Code.
        final String ID_INST = "00002"; 
        final String ID_RESP = "0001"; 

        // Coletoras de tempo
        long totalProcessingTimeMs = 0;
        int processedCount = 0;
        long stepStartTime, stepEndTime;

        // Carrega configurações e templates apenas uma vez
        List<Alternativa> todasAlternativas = lerConfiguracao(PATH_CONFIG);
        Map<String, FolhaTemplate> templates = lerTemplates(PATH_TEMPLATES);
        Map<String, String> todasAsRespostasDoTeste = new LinkedHashMap<>(); 
        
        // Coleta todos os nomes de folha da configuração, garantindo a ordem
        Set<String> folhas = todasAlternativas.stream()
                .map(a -> a.folha)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        
        // Coleta todas as questões únicas, garantindo a ordem para o cabeçalho
        Set<String> todasAsQuestoes = todasAlternativas.stream()
            .map(a -> a.questao)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // Início do processamento de cada folha individualmente
        for (String folha : folhas) {
            System.out.println("\n➡ Processando " + folha);
            long totalStartTime = System.currentTimeMillis();

            FolhaTemplate template = templates.get(folha);
            if (template == null) {
                System.out.println("⚠ Template não encontrado para " + folha);
                continue;
            }

            List<Alternativa> alternativasFolha = todasAlternativas.stream()
                    .filter(a -> a.folha.equals(folha))
                    .collect(Collectors.toList());

            // --- 1. Carregar Imagem ---
            stepStartTime = System.currentTimeMillis();
            String caminhoImagem = PATH_INPUT_DIR + "entrada_" + folha.toLowerCase().replace(" ", "") + ".jpg";
            Mat imagem = Imgcodecs.imread(caminhoImagem);
            if (imagem.empty()) {
                System.out.println("⚠ Erro ao carregar: " + caminhoImagem);
                continue;
            }
            stepEndTime = System.currentTimeMillis();
            System.out.println(String.format("  [TIMER] 1. Carregar Imagem:    %d ms", stepEndTime - stepStartTime));

            // --- 2. Detectar Âncoras e Alinhar ---
            stepStartTime = System.currentTimeMillis();
            Mat recorte = detectarETransformarAncoras(imagem, template);
            stepEndTime = System.currentTimeMillis();
            System.out.println(String.format("  [TIMER] 2. Alinhar (Âncoras): %d ms", stepEndTime - stepStartTime));

            if (recorte == null) {
                System.out.println("⚠ Falha na detecção de âncoras (" + folha + ")");
                imagem.release();
                continue;
            }

            // --- 3. Ler Bolhas (OMR) ---
            stepStartTime = System.currentTimeMillis();
            Mat cinza = new Mat();
            Imgproc.cvtColor(recorte, cinza, Imgproc.COLOR_BGR2GRAY);

            Map<String, List<Alternativa>> porQuestao = alternativasFolha.stream()
                    .collect(Collectors.groupingBy(a -> a.questao, LinkedHashMap::new, Collectors.toList()));

            Map<String, String> respostas = new LinkedHashMap<>(); // Respostas desta folha

            for (var entry : porQuestao.entrySet()) {
                List<Alternativa> lista = entry.getValue();
                List<Alternativa> marcadas = new ArrayList<>();
                Map<Alternativa, Double> medias = new HashMap<>();

                double minMedia = 255, maxMedia = 0;
                for (Alternativa alt : lista) {
                    int x0 = Math.max(alt.x - BUBBLE_RADIUS, 0);
                    int y0 = Math.max(alt.y - BUBBLE_RADIUS, 0);
                    int largura = Math.min(BUBBLE_RADIUS * 2, cinza.width() - x0);
                    int altura = Math.min(BUBBLE_RADIUS * 2, cinza.height() - y0);

                    Mat sub = new Mat(cinza, new Rect(x0, y0, largura, altura));
                    double media = Core.mean(sub).val[0];
                    medias.put(alt, media);
                    minMedia = Math.min(minMedia, media);
                    maxMedia = Math.max(maxMedia, media);
                    sub.release();
                }

                boolean algoMarcado = (maxMedia - minMedia) > RELATIVE_MARK_THRESHOLD;
                if (algoMarcado) {
                    double limiar = minMedia + (RELATIVE_MARK_THRESHOLD / 2.0);
                    for (Alternativa alt : lista) {
                        if (medias.get(alt) <= limiar) marcadas.add(alt);
                    }
                }

                String respostaFinal;
                if (marcadas.isEmpty()) respostaFinal = "";
                else if (marcadas.size() > 1) respostaFinal = "?";
                else respostaFinal = traduzAlternativa(marcadas.get(0).opcao);

                respostas.put(entry.getKey(), respostaFinal);

                for (Alternativa alt : lista) {
                    int x0 = Math.max(alt.x - BUBBLE_RADIUS, 0);
                    int y0 = Math.max(alt.y - BUBBLE_RADIUS, 0);
                    int largura = Math.min(BUBBLE_RADIUS * 2, cinza.width() - x0);
                    int altura = Math.min(BUBBLE_RADIUS * 2, cinza.height() - y0);
                    Scalar cor = marcadas.contains(alt) ? COLOR_GREEN : COLOR_RED;
                    Imgproc.rectangle(recorte, new Point(x0, y0), new Point(x0 + largura, y0 + altura), cor, 2);
                }
            }
            stepEndTime = System.currentTimeMillis();
            System.out.println(String.format("  [TIMER] 3. Ler Bolhas (OMR):    %d ms", stepEndTime - stepStartTime));

            // --- 4. Salva Imagem ---
            stepStartTime = System.currentTimeMillis();
            Imgcodecs.imwrite(PATH_OUTPUT_DIR + "resultado_" + folha + ".jpg", recorte);
            stepEndTime = System.currentTimeMillis();
            System.out.println(String.format("  [TIMER] 4. Salvar Saídas (Img): %d ms", stepEndTime - stepStartTime));
            
            // Consolida e libera recursos
            todasAsRespostasDoTeste.putAll(respostas);
            imagem.release();
            recorte.release();
            cinza.release();

            // --- 5. Tempo Total da Folha ---
            long totalEndTime = System.currentTimeMillis();
            long totalDurationMs = totalEndTime - totalStartTime;
            System.out.println(String.format("  ⏱️ --- Tempo Total da Folha: %d ms ---", totalDurationMs));
            
            totalProcessingTimeMs += totalDurationMs;
            processedCount++;
        }
        
        // ---------------------------------------------------------------------------
        // 🔹 ESCRITA ORGANIZADA (UMA LINHA CONCATENADA)
        // ---------------------------------------------------------------------------
        
        String caminhoTXTOrganizado = PATH_OUTPUT_DIR + OUTPUT_TXT_FILE_ORGANIZED;
        System.out.println("\n--- Gerando arquivo de respostas organizado (" + OUTPUT_TXT_FILE_ORGANIZED + ") ---");
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(caminhoTXTOrganizado, false))) {
            
            // 1. Constrói o Cabeçalho Único
            StringBuilder header = new StringBuilder();
            header.append("id_instituicao,id_respondente");
            for (String questao : todasAsQuestoes) {
                header.append(",").append(questao);
            }
            bw.write(header.toString());
            bw.newLine();
            
            // 2. Escreve a ÚNICA linha de dados
            StringBuilder dataLine = new StringBuilder();
            dataLine.append(ID_INST).append(",");
            dataLine.append(ID_RESP);
            
            for (String questao : todasAsQuestoes) {
                dataLine.append(",").append(todasAsRespostasDoTeste.getOrDefault(questao, "")); 
            }
            
            bw.write(dataLine.toString());
            bw.newLine();
            
        } catch (IOException e) {
            System.out.println("Erro ao salvar resultados organizados: " + e.getMessage());
        }

        // ---------------------------------------------------------------------------
        // 🔹 SUMÁRIO FINAL
        // ---------------------------------------------------------------------------
        long fimTotal = System.currentTimeMillis();
        long tempoTotalGeral = fimTotal - inicioTotal;
        long averageTime = processedCount > 0 ? totalProcessingTimeMs / processedCount : 0;
        
        System.out.println("\n\n===== PROCESSAMENTO CONCLUÍDO =====");
        System.out.println(String.format("  Total de Folhas Processadas: %d", processedCount));
        System.out.println(String.format("  Tempo Total Geral: %d ms", tempoTotalGeral));
        System.out.println(String.format("  Tempo Médio por Folha: %d ms", averageTime));
        System.out.println("===================================");
    }

    // -----------------------------------------------------
    // FUNÇÕES AUXILIARES (mantidas inalteradas)
    //------------------------------------------------------

    private static Mat detectarETransformarAncoras(Mat imagem, FolhaTemplate template) {
        int largura = imagem.cols(), altura = imagem.rows();
        int w = ANCHOR_SEARCH_SIZE, h = ANCHOR_SEARCH_SIZE;

        Rect[] regioes = {
                new Rect(0, 0, w, h),
                new Rect(largura - w, 0, w, h),
                new Rect(0, altura - h, w, h),
                new Rect(largura - w, altura - h, w, h)
        };

        List<Point> pontos = new ArrayList<>();
        for (Rect roi : regioes) {
            Mat regiao = new Mat(imagem, roi);
            Mat gray = new Mat();
            Imgproc.cvtColor(regiao, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.adaptiveThreshold(gray, gray, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY_INV, ADAPTIVE_THRESH_BLOCK_SIZE, ADAPTIVE_THRESH_C);

            List<MatOfPoint> contornos = new ArrayList<>();
            Imgproc.findContours(gray.clone(), contornos, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            contornos.stream()
                    .filter(c -> {
                        double area = Imgproc.contourArea(c);
                        return area >= ANCHOR_MIN_AREA && area <= ANCHOR_MAX_AREA;
                    })
                    .map(c -> {
                        MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
                        MatOfPoint2f aprox = new MatOfPoint2f();
                        Imgproc.approxPolyDP(c2f, aprox, ANCHOR_APPROX_EPSILON * Imgproc.arcLength(c2f, true), true);
                        if (aprox.total() == 4) {
                            Rect caixa = Imgproc.boundingRect(new MatOfPoint(aprox.toArray()));
                            double aspect = Math.max((double) caixa.width / caixa.height, (double) caixa.height / caixa.width);
                            if (aspect <= ANCHOR_ASPECT_TOLERANCE) {
                                return new Point(roi.x + caixa.x + caixa.width / 2.0, roi.y + caixa.y + caixa.height / 2.0);
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(pontos::add);

            regiao.release(); gray.release();
        }

        if (pontos.size() != 4) return null;

        pontos.sort(Comparator.comparingDouble(p -> p.x + p.y));
        Point tl = pontos.get(0), br = pontos.get(3);
        Point p2 = pontos.get(1), p3 = pontos.get(2);
        Point tr = (p2.x > p3.x) ? p2 : p3;
        Point bl = (p2.x > p3.x) ? p3 : p2;

        Mat M = Imgproc.getPerspectiveTransform(new MatOfPoint2f(tl, tr, bl, br), template.idealPoints);
        Mat warp = new Mat();
        Imgproc.warpPerspective(imagem, warp, M, template.idealSize, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(245,245,245));

        M.release();
        return warp;
    }

    private static Map<String, FolhaTemplate> lerTemplates(String caminho) {
        Map<String, FolhaTemplate> templates = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(caminho))) {
            String line, folhaAtual = null;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[FOLHA")) {
                    folhaAtual = line.replace("[", "").replace("]", "").trim();
                    continue;
                }
                if (folhaAtual != null && line.startsWith("DADOS: ")) {
                    String[] p = line.substring(7).split(";");
                    if (p.length == 6) {
                        Size size = new Size(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
                        Point tl = strToPoint(p[2]), tr = strToPoint(p[3]), bl = strToPoint(p[4]), brP = strToPoint(p[5]);
                        templates.put(folhaAtual, new FolhaTemplate(size, new MatOfPoint2f(tl, tr, bl, brP)));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao ler templates: " + e.getMessage());
        }
        return templates;
    }

    private static List<Alternativa> lerConfiguracao(String caminho) {
        List<Alternativa> lista = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(caminho))) {
            String line, folhaAtual = "Desconhecida";
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[FOLHA")) {
                    folhaAtual = line.replace("[", "").replace("]", "").trim();
                    continue;
                }
                String[] partes = line.split(";");
                if (partes.length == 4)
                    lista.add(new Alternativa(folhaAtual, partes[0], partes[1],
                            Integer.parseInt(partes[2]), Integer.parseInt(partes[3])));
            }
        } catch (Exception e) {
            System.out.println("Erro ao ler config: " + e.getMessage());
        }
        return lista;
    }

    private static Point strToPoint(String s) {
        String[] xy = s.trim().split(",");
        return new Point(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
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