package seabdetector;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;
import java.util.Comparator;

public class DataModels {

    public static class Alternativa {
        public String folha, questao, opcao;
        public int x, y;

        public Alternativa(String f, String q, String o, int x, int y) {
            this.folha = f; this.questao = q; this.opcao = o; this.x = x; this.y = y;
        }
    }

    public static class FolhaTemplate {
        public Size idealSize;
        public MatOfPoint2f idealPoints;

        public FolhaTemplate(Size size, MatOfPoint2f points) {
            this.idealSize = size;
            this.idealPoints = points;
        }
        public void release() {
            if (idealPoints != null) idealPoints.release();
        }
    }

    public static class QrData {
        public String instituicao, respondente, folhaNome, tipoProva, ano, qrTextoCompleto;

        public QrData(String i, String r, String f, String t, String a, String full) {
            this.instituicao = i; this.respondente = r; this.folhaNome = f;
            this.tipoProva = t; this.ano = a; this.qrTextoCompleto = full;
        }
        
        @Override
        public String toString() {
            return "Instituicao: " + instituicao + ", Respondente: " + respondente +
                   ", Folha: " + folhaNome + ", Tipo: " + tipoProva + ", Ano: " + ano;
        }
        
        public String getRespondenteKey() {
            return respondente;
        }
    }
}