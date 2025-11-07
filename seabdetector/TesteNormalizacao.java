package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class TesteNormalizacao {

    static {
        System.load("C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll");
    }

    public static void main(String[] args) {
        String caminhoImagem = "C:\\Users\\jasom\\opencv\\testes\\entradas\\entrada_folha1.jpg";
        String caminhoSaida = "C:\\Users\\jasom\\opencv\\testes\\saida_normalizada.jpg";

        // Coordenadas detectadas na folha escaneada
        Point tl = new Point(60, 62);    // canto superior esquerdo
        Point tr = new Point(730, 62);   // canto superior direito
        Point bl = new Point(60, 1089);  // canto inferior esquerdo
        Point br = new Point(730, 1089); // canto inferior direito

        // Coordenadas padrão da folha normalizada (tamanho de referência)
        int larguraPadrao = 850;
        int alturaPadrao = 1168;
        Point dst_tl = new Point(0, 0);
        Point dst_tr = new Point(larguraPadrao, 0);
        Point dst_bl = new Point(0, alturaPadrao);
        Point dst_br = new Point(larguraPadrao, alturaPadrao);

        // Ler imagem original
        Mat src = Imgcodecs.imread(caminhoImagem);
        if (src.empty()) {
            System.out.println("❌ Erro ao carregar a imagem: " + caminhoImagem);
            return;
        }

        // Transformação perspectiva
        MatOfPoint2f srcPts = new MatOfPoint2f(tl, tr, bl, br);
        MatOfPoint2f dstPts = new MatOfPoint2f(dst_tl, dst_tr, dst_bl, dst_br);
        Mat transform = Imgproc.getPerspectiveTransform(srcPts, dstPts);

        Mat dst = new Mat();
        Imgproc.warpPerspective(src, dst, transform, new Size(larguraPadrao, alturaPadrao));

        // Coordenadas das bolhas da folha 1 (exemplo)
        Point[] bolhas = new Point[] {
            new Point(165, 125), // Questão 1A
            new Point(165, 148), // Questão 1B
            new Point(165, 170), // Questão 1C
            new Point(165, 225), // Questão 2A
            new Point(165, 248)  // Questão 2B
        };

        // Transformar as bolhas
        MatOfPoint2f bolhasPts = new MatOfPoint2f(bolhas);
        MatOfPoint2f bolhasTransformadas = new MatOfPoint2f();
        Core.perspectiveTransform(bolhasPts, bolhasTransformadas, transform);
        Point[] bolhasNorm = bolhasTransformadas.toArray();

        // Desenhar círculos verdes nas bolhas normalizadas
        for (Point p : bolhasNorm) {
            Imgproc.circle(dst, p, 10, new Scalar(0, 255, 0), 2);
        }

        // Salvar imagem de saída
        Imgcodecs.imwrite(caminhoSaida, dst);
        System.out.println("✅ Imagem normalizada gerada: " + caminhoSaida);
    }
}
