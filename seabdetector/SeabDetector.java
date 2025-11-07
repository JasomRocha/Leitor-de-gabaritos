package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class SeabDetector {

    static {
        System.load("C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll");
    }

    static class Alternativa {

        String folha;
        String questao;
        String opcao;
        int x, y;

        public Alternativa(String folha, String questao, String opcao, int x, int y) {
            this.folha = folha;
            this.questao = questao;
            this.opcao = opcao;
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) {
        String caminhoConfig = "C:\\Users\\jasom\\opencv\\testes\\config.txt";

        // Ler todas as alternativas do TXT
        List<Alternativa> todasAlternativas = lerConfiguracao(caminhoConfig);

        // Descobrir quais folhas existem no TXT
        Set<String> folhas = todasAlternativas.stream()
                .map(a -> a.folha)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String folha : folhas) {
            System.out.println("➡ Processando " + folha);

            // Filtrar alternativas da folha atual
            List<Alternativa> alternativasFolha = todasAlternativas.stream()
                    .filter(a -> a.folha.equals(folha))
                    .collect(Collectors.toList());

            // Nome da imagem da folha
            String caminhoImagem = "C:\\Users\\jasom\\opencv\\testes\\entradas\\entrada_"
                    + folha.toLowerCase().replace(" ", "") + ".jpg";
            Mat imagem = Imgcodecs.imread(caminhoImagem);

            if (imagem.empty()) {
                System.out.println("Erro ao carregar a imagem: " + caminhoImagem);
                continue;
            }

            // Converter para cinza
            Mat cinza = new Mat();
            Imgproc.cvtColor(imagem, cinza, Imgproc.COLOR_BGR2GRAY);

            Map<String, List<Alternativa>> porQuestao = new LinkedHashMap<>();
            for (Alternativa a : alternativasFolha) {
                porQuestao.computeIfAbsent(a.questao, k -> new ArrayList<>()).add(a);
            }

            Map<String, String> respostas = new LinkedHashMap<>();
            List<Double> intensidadesMarcadas = new ArrayList<>();
            List<Double> intensidadesNaoMarcadas = new ArrayList<>();

            for (String questao : porQuestao.keySet()) {
                List<Alternativa> lista = porQuestao.get(questao);
                List<Alternativa> marcadas = new ArrayList<>();
                int tamanho = 10; // raio da bolha

                for (Alternativa alt : lista) {
                    int x0 = Math.max(alt.x - tamanho, 0);
                    int y0 = Math.max(alt.y - tamanho, 0);
                    int largura = Math.min(tamanho * 2, cinza.width() - x0);
                    int altura = Math.min(tamanho * 2, cinza.height() - y0);

                    Rect regiao = new Rect(x0, y0, largura, altura);
                    Mat sub = new Mat(cinza, regiao);
                    Scalar media = Core.mean(sub);

                    // Definir se marcada ou não
                    boolean marcada = media.val[0] < 200;
                    if (marcada) {
                        marcadas.add(alt);
                        intensidadesMarcadas.add(media.val[0]);
                    } else {
                        intensidadesNaoMarcadas.add(media.val[0]);
                    }

                    // Desenhar quadrado
                    Scalar cor = marcada ? new Scalar(0, 255, 0) : new Scalar(0, 0, 255); // verde ou vermelho
                    Imgproc.rectangle(imagem,
                            new Point(x0, y0),
                            new Point(x0 + largura, y0 + altura),
                            cor, 2); // espessura 2
                }

                // Definir resposta final
                String respostaFinal;
                if (marcadas.isEmpty()) {
                    respostaFinal = ""; // nenhuma bolha marcada
                } else if (marcadas.size() > 1) {
                    respostaFinal = "?"; // múltiplas bolhas marcadas
                } else {
                    respostaFinal = traduzAlternativa(marcadas.get(0).opcao);
                }

                respostas.put(questao, respostaFinal);
            }

            // Salvar imagem colorida com quadrados
            String caminhoSaidaImagem = "C:\\Users\\jasom\\opencv\\testes\\resultado_"
                    + folha.toLowerCase().replace(" ", "") + ".jpg";
            Imgcodecs.imwrite(caminhoSaidaImagem, imagem);
          
            // Salvar respostas
            String caminhoTXT = "C:\\Users\\jasom\\opencv\\testes\\respostas_brutas.txt";
            salvarRespostas(respostas, caminhoTXT);
            String vetorRespostas = respostas.values().stream()
                    .collect(Collectors.joining(","));
            System.out.println(vetorRespostas);
        }
    }

    private static String traduzAlternativa(String alt) {
        switch (alt.toUpperCase()) {
            case "S":
                return "Sim";
            case "N":
                return "Nao";
            case "NQN":
                return "Nunca ou quase nunca";
            case "DVQ":
                return "De vez em quando";
            case "SQS":
                return "Sempre ou quase sempre";
            case "NU":
                return "Não uso meu tempo pra isso";
            case "M1H":
                return "Menos de 1 hora";
            case "E12H":
                return "Entre 1 e 2 horas";
            case "M2H":
                return "Mais de 2 horas";
            case "TE":
                return "Todos eles";
            case "AMP":
                return "A maior parte deles";
            case "PD":
                return "Poucos deles";
            case "ND":
                return "Nenhum deles";
            case "CT":
                return "Concordo totalmente";
            case "C":
                return "Concordo";
            case "D":
                return "Discordo";
            case "DT":
                return "Discordo totalmente";
            default:
                return alt;
        }
    }

    private static List<Alternativa> lerConfiguracao(String caminhoConfig) {
        List<Alternativa> lista = new ArrayList<>();
        String folhaAtual = "Desconhecida";

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoConfig))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

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
            System.out.println("Erro ao ler configuração: " + e.getMessage());
        }

        return lista;
    }

    private static void salvarRespostas(Map<String, String> respostas, String caminho) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(caminho, true))) {
            for (var entry : respostas.entrySet()) {
                bw.write(entry.getKey() + ": " + entry.getValue());
                bw.newLine();
            }
      
        } catch (IOException e) {
            System.out.println("Erro ao salvar respostas: " + e.getMessage());
        }
    }
}
