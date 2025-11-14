package seabdetector;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;
import java.util.stream.Collectors;

import static seabdetector.Constants.*;
import static seabdetector.DataModels.Alternativa;

public class OmrReader {

    /**
     * Lê as bolhas na imagem alinhada e marca visualmente a resposta.
     * @param recorte A imagem da folha já alinhada (warped).
     * @param alternativasFolha A lista de Alternativas para esta folha.
     * @return Um mapa com Questão -> Resposta (ex: "Q1" -> "Sim").
     */
    public static Map<String, String> readBubbles(Mat recorte, List<Alternativa> alternativasFolha) {
        
        Mat cinza = new Mat();
        Imgproc.cvtColor(recorte, cinza, Imgproc.COLOR_BGR2GRAY);
        
        // Agrupa as alternativas por questão
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
            
            // 2. Determina quais bolhas estão marcadas (relativo à questão)
            boolean algoMarcado = (maxMedia - minMedia) > RELATIVE_MARK_THRESHOLD;
            if (algoMarcado) {
                // O limiar é o valor mais escuro (minMedia) + uma margem de tolerância
                double limiarMarcado = minMedia + (RELATIVE_MARK_THRESHOLD / 2.0); 
                for (Alternativa alt : lista) {
                    if (medias.get(alt) <= limiarMarcado) {
                        marcadas.add(alt);
                    }
                }
            }
            
            // 3. Marca visualmente a resposta na imagem colorida (recorte)
            for (Alternativa alt : lista) {
                int x = alt.x;
                int y = alt.y;
                int x0 = Math.max(x - BUBBLE_RADIUS, 0);
                int y0 = Math.max(y - BUBBLE_RADIUS, 0);
                int larguraRecorte = Math.min(BUBBLE_RADIUS * 2, cinza.width() - x0);
                int alturaRecorte = Math.min(BUBBLE_RADIUS * 2, cinza.height() - y0);
                Scalar cor = marcadas.contains(alt) ? COLOR_GREEN : COLOR_RED;
                
                // Desenha o retângulo na imagem alinhada (recorte)
                Imgproc.rectangle(recorte, new Point(x0, y0), new Point(x0 + larguraRecorte, y0 + alturaRecorte), cor, 2);
            }

            // 4. Determina a resposta final (vazia, única ou múltipla)
            String respostaFinal;
            if (marcadas.isEmpty()) respostaFinal = "";
            else if (marcadas.size() > 1) respostaFinal = "?"; // Múltipla marcação
            else respostaFinal = Constants.traduzAlternativa(marcadas.get(0).opcao);
            
            respostasDaFolha.put(questao, respostaFinal);
        }
        
        cinza.release();
        return respostasDaFolha;
    }
}