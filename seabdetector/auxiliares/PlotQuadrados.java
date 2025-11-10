package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class PlotQuadrados {

    static {
        System.load("C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll");
    }

    static class Alternativa {
        String questao;
        String opcao;
        int x, y;

        public Alternativa(String questao, String opcao, int x, int y) {
            this.questao = questao;
            this.opcao = opcao;
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) {
        String caminhoImagem = "C:\\Users\\jasom\\opencv\\testes\\saidas\\folha_recortada.jpg";
        Mat imagem = Imgcodecs.imread(caminhoImagem);

        if (imagem.empty()) {
            System.out.println("❌ Erro ao carregar a imagem: " + caminhoImagem);
            return;
        }

        List<Alternativa> alternativas = new ArrayList<>();

// Questão 22 (TE / AMP / PD / ND)
alternativas.add(new Alternativa("22a","TE",437,227));
alternativas.add(new Alternativa("22a","AMP",517,227));
alternativas.add(new Alternativa("22a","PD",587,227));
alternativas.add(new Alternativa("22a","ND",661,227));

alternativas.add(new Alternativa("22b","TE",437,269));
alternativas.add(new Alternativa("22b","AMP",514,269));
alternativas.add(new Alternativa("22b","PD",587,269));
alternativas.add(new Alternativa("22b","ND",661,269));

alternativas.add(new Alternativa("22c","TE",437,312));
alternativas.add(new Alternativa("22c","AMP",514,312));
alternativas.add(new Alternativa("22c","PD",587,312));
alternativas.add(new Alternativa("22c","ND",661,312));

alternativas.add(new Alternativa("22d","TE",437,351));
alternativas.add(new Alternativa("22d","AMP",514,351));
alternativas.add(new Alternativa("22d","PD",587,351));
alternativas.add(new Alternativa("22d","ND",661,351));

alternativas.add(new Alternativa("22e","TE",437,385));
alternativas.add(new Alternativa("22e","AMP",514,385));
alternativas.add(new Alternativa("22e","PD",587,385));
alternativas.add(new Alternativa("22e","ND",661,385));

alternativas.add(new Alternativa("22f","TE",437,424));
alternativas.add(new Alternativa("22f","AMP",514,424));
alternativas.add(new Alternativa("22f","PD",587,424));
alternativas.add(new Alternativa("22f","ND",661,424));

alternativas.add(new Alternativa("22g","TE",437,464));
alternativas.add(new Alternativa("22g","AMP",514,464));
alternativas.add(new Alternativa("22g","PD",587,464));
alternativas.add(new Alternativa("22g","ND",661,464));

alternativas.add(new Alternativa("22h","TE",437,503));
alternativas.add(new Alternativa("22h","AMP",514,503));
alternativas.add(new Alternativa("22h","PD",587,503));
alternativas.add(new Alternativa("22h","ND",661,503));

// Questão 23 (CT / C / D / DT)
alternativas.add(new Alternativa("23a","CT",400,678));
alternativas.add(new Alternativa("23a","C",482,678));
alternativas.add(new Alternativa("23a","D",562,678));
alternativas.add(new Alternativa("23a","DT",650,678));

alternativas.add(new Alternativa("23b","CT",400,716));
alternativas.add(new Alternativa("23b","C",482,716));
alternativas.add(new Alternativa("23b","D",562,716));
alternativas.add(new Alternativa("23b","DT",650,716));

alternativas.add(new Alternativa("23c","CT",400,758));
alternativas.add(new Alternativa("23c","C",482,758));
alternativas.add(new Alternativa("23c","D",562,758));
alternativas.add(new Alternativa("23c","DT",650,758));

alternativas.add(new Alternativa("23d","CT",400,794));
alternativas.add(new Alternativa("23d","C",482,794));
alternativas.add(new Alternativa("23d","D",562,794));
alternativas.add(new Alternativa("23d","DT",650,794));

alternativas.add(new Alternativa("23e","CT",400,830));
alternativas.add(new Alternativa("23e","C",482,830));
alternativas.add(new Alternativa("23e","D",562,830));
alternativas.add(new Alternativa("23e","DT",650,830));

alternativas.add(new Alternativa("23f","CT",400,864));
alternativas.add(new Alternativa("23f","C",482,864));
alternativas.add(new Alternativa("23f","D",562,864));
alternativas.add(new Alternativa("23f","DT",650,864));

alternativas.add(new Alternativa("23g","CT",400,901));
alternativas.add(new Alternativa("23g","C",482,901));
alternativas.add(new Alternativa("23g","D",562,901));
alternativas.add(new Alternativa("23g","DT",650,901));

alternativas.add(new Alternativa("23h","CT",400,941));
alternativas.add(new Alternativa("23h","C",482,941));
alternativas.add(new Alternativa("23h","D",562,941));
alternativas.add(new Alternativa("23h","DT",650,941));

alternativas.add(new Alternativa("23i","CT",400,982));
alternativas.add(new Alternativa("23i","C",482,982));
alternativas.add(new Alternativa("23i","D",562,982));
alternativas.add(new Alternativa("23i","DT",650,982));

        // Desenhar quadrados
        int tamanho = 10;
        for (Alternativa alt : alternativas) {
            int x0 = Math.max(alt.x - tamanho, 0);
            int y0 = Math.max(alt.y - tamanho, 0);
            int largura = Math.min(tamanho * 2, imagem.width() - x0);
            int altura = Math.min(tamanho * 2, imagem.height() - y0);

            Imgproc.rectangle(imagem,
                    new Point(x0, y0),
                    new Point(x0 + largura, y0 + altura),
                    new Scalar(0, 255, 0), 1);
        }

        String caminhoSaida = "C:\\Users\\jasom\\opencv\\testes\\folha3_quadrados.jpg";
        Imgcodecs.imwrite(caminhoSaida, imagem);
        System.out.println("✅ Imagem com quadrados salva: " + caminhoSaida);
    }
}
