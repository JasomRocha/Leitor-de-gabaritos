package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static seabdetector.Constants.*;
import static seabdetector.DataModels.Alternativa;

public class OmrReader {

    // Margem de segurança para inclusão de bolhas duplas na lista 'marcadas'.
    private static final double MARGEM_INCLUSAO = 15.0; 
    
    // Novo Limite Absoluto: Se a bolha mais escura estiver abaixo de 180.0 (um cinza claro),
    // consideramos que há ALGUMA marcação, mesmo que o contraste relativo seja nulo.
    private static final double MINIMA_ABSOLUTA_MARCADA = 180.0; 

    public static Map<String, String> readBubbles(Mat recorte, List<Alternativa> alternativasFolha, String debugOutputPath, String baseFileName) {

        Mat cinza = null;
        try {
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

                // 1. Calcula a média de intensidade para cada bolha e encontra o min/max
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

                // 2. Determina quais bolhas estão marcadas (LÓGICA SIMPLIFICADA E ROBUSTA)
                
                double diferencaTotal = maxMedia - minMedia;
                
                // Critério de ativação: 
                // A marcação é ativada SE: 
                // 1) Há contraste forte (diferencaTotal > 25.0) OU
                // 2) A bolha mais escura é, de forma absoluta, escura o suficiente (minMedia < 180.0)
                boolean ativarDeteccao = (diferencaTotal > RELATIVE_MARK_THRESHOLD) || (minMedia < MINIMA_ABSOLUTA_MARCADA);
                
                if (ativarDeteccao) {
                    // Define o limiar de inclusão com base na minMedia + margem.
                    double limiarInclusivo = minMedia + MARGEM_INCLUSAO;

                    for (Alternativa alt : lista) {
                        // Se a média da bolha for menor ou igual ao limiar de inclusão (mais escura), ela é marcada.
                        if (medias.get(alt) <= limiarInclusivo) {
                            marcadas.add(alt);
                        }
                    }
                }

                // 3. Marca visualmente a resposta
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

                // 4. Determina a resposta final (Decisão de Ambiguide Simples e Correta)
                String respostaFinal;
                if (marcadas.isEmpty()) respostaFinal = ""; // Somente se nada passou na ativação
                else if (marcadas.size() > 1) respostaFinal = "?"; // Dupla marcação
                else respostaFinal = seabdetector.Constants.traduzAlternativa(marcadas.get(0).opcao);

                respostasDaFolha.put(questao, respostaFinal);
            }

            // 5. SALVA A IMAGEM DE DEBUG
            //if (debugOutputPath != null && baseFileName != null) {
               // String debugFileName = debugOutputPath + File.separator + baseFileName + "_omr_debug.jpg";
                //Imgcodecs.imwrite(debugFileName, recorte);
                //System.out.println("  [DEBUG] Imagem OMR salva em: " + debugFileName);
            //}
            
            return respostasDaFolha;

        } finally {
            if (cinza != null) cinza.release();
        }
    }
}