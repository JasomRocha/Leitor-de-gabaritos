package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.util.*;

public class TesteNormalizacao {
    static {
        System.load("C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll");
    }

    public static void main(String[] args) {
        String inputPath = "C:\\Users\\jasom\\opencv\\testes\\entradas\\tronxura2.jpg";
        String outputDir = "C:\\Users\\jasom\\opencv\\testes\\entradas\\";

        Mat imagem = Imgcodecs.imread(inputPath);
        if (imagem.empty()) {
            System.out.println("Erro ao carregar imagem!");
            return;
        }

        int largura = imagem.cols();
        int altura = imagem.rows();
        int w = 500, h = 500;

        Rect[] regioes = new Rect[]{
                new Rect(0, 0, w, h),
                new Rect(largura - w, 0, w, h),
                new Rect(0, altura - h, w, h),
                new Rect(largura - w, altura - h, w, h)
        };

        List<Point> centrosAncoras = new ArrayList<>();

        for (Rect roi : regioes) {
            Mat regiao = new Mat(imagem, roi);
            Mat gray = new Mat();
            Imgproc.cvtColor(regiao, gray, Imgproc.COLOR_BGR2GRAY);
            Mat thresh = new Mat();
            Imgproc.adaptiveThreshold(gray, thresh, 255,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 15, 10);

            List<MatOfPoint> contornos = new ArrayList<>();
            Imgproc.findContours(thresh.clone(), contornos, new Mat(),
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            for (MatOfPoint contorno : contornos) {
                double area = Imgproc.contourArea(contorno);
                if (area < 100 || area > 5000) continue;

                MatOfPoint2f contorno2f = new MatOfPoint2f(contorno.toArray());
                double perimetro = Imgproc.arcLength(contorno2f, true);
                MatOfPoint2f aprox = new MatOfPoint2f();
                Imgproc.approxPolyDP(contorno2f, aprox, 0.02 * perimetro, true);

                if (aprox.total() == 4) {
                    Rect caixa = Imgproc.boundingRect(new MatOfPoint(aprox.toArray()));
                    Point centro = new Point(caixa.x + roi.x + caixa.width / 2.0,
                            caixa.y + roi.y + caixa.height / 2.0);
                    centrosAncoras.add(centro);
                }
            }
        }

        Imgcodecs.imwrite(outputDir + "debug_ancoras.jpg", imagem);
        System.out.println("🟩 Imagem de debug das âncoras salva em: " + outputDir + "debug_ancoras.jpg");
        System.out.println("🔹 Âncoras detectadas: " + centrosAncoras.size());

        if (centrosAncoras.size() < 4) {
            System.out.println("⚠️ Apenas " + centrosAncoras.size() + " âncoras. Endireitamento ignorado.");
        } else {
            // Ordenar âncoras TL, TR, BR, BL
            centrosAncoras.sort(Comparator.comparingDouble(p -> p.y + p.x));
            Point topLeft = centrosAncoras.get(0);
            Point bottomRight = centrosAncoras.get(centrosAncoras.size() - 1);

            centrosAncoras.sort(Comparator.comparingDouble(p -> p.y - p.x));
            Point topRight = centrosAncoras.get(0);
            Point bottomLeft = centrosAncoras.get(centrosAncoras.size() - 1);

            // Mantém a folha na mesma posição e tamanho da imagem original
            MatOfPoint2f srcPoints = new MatOfPoint2f(topLeft, topRight, bottomRight, bottomLeft);

            // Calcula os destinos “retificados” para cada ponto da folha
            Point dstTopLeft = new Point(topLeft.x, topLeft.y);
            Point dstTopRight = new Point(topRight.x, topLeft.y);
            Point dstBottomRight = new Point(bottomRight.x, bottomLeft.y);
            Point dstBottomLeft = new Point(bottomLeft.x, bottomLeft.y);

            MatOfPoint2f dstPoints = new MatOfPoint2f(dstTopLeft, dstTopRight, dstBottomRight, dstBottomLeft);

            Mat M = Imgproc.getPerspectiveTransform(srcPoints, dstPoints);
            Mat imagemCorrigida = new Mat();

            Imgproc.warpPerspective(
                    imagem,
                    imagemCorrigida,
                    M,
                    new Size(largura, altura),
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_CONSTANT,
                    new Scalar(255, 255, 255) // branco
            );

            Imgcodecs.imwrite(outputDir + "tronxura2_corrigida_mesma_posicao.jpg", imagemCorrigida);
            System.out.println("✅ Imagem endireitada mantendo posição e dimensões originais salva em: "
                    + outputDir + "tronxura2_corrigida_mesma_posicao.jpg");
        }

        System.out.println("✅ Finalizado!");
    }

    private static double distancia(Point p1, Point p2) {
        return Math.hypot(p1.x - p2.x, p1.y - p2.y);
    }
}
