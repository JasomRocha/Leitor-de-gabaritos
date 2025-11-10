package SeabDetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class TestadorQRCode {

    static {
        System.load("C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll");
    }

    public static void main(String[] args) {
        // Caminho da imagem contendo o QR Code
        String imagePath = "C:\\Users\\jasom\\opencv\\testes\\entradas\\entradas_qr\\entrada_folha2.jpg";

        // Caminho base para salvar os arquivos de debug
        String outputDir = "C:\\Users\\jasom\\opencv\\testes\\saidas\\saidas_qr\\";
        new File(outputDir).mkdirs();

        // Carrega a imagem com o OpenCV
        Mat image = Imgcodecs.imread(imagePath);
        if (image.empty()) {
            System.out.println("❌ Erro: não foi possível carregar a imagem.");
            return;
        }

        System.out.println("🔍 Tentando localizar QR Code com múltiplos tamanhos de recorte...");

        // Tamanhos possíveis para testar
        int[] tamanhos = {250, 150};
        int margemExtra = 20;
        boolean encontrado = false;

        for (int recorteLado : tamanhos) {
            System.out.println("\n➡ Tentando com recorte de " + recorteLado + "px...");

            // Calcula coordenadas do canto superior direito
            int x = Math.max((int) image.width() - recorteLado - margemExtra, 0);
            int y = 0;
            int w = Math.min(recorteLado + margemExtra, image.width() - x);
            int h = Math.min(recorteLado + margemExtra, image.height() - y);

            Rect rect = new Rect(x, y, w, h);
            Mat qrRecortado = new Mat(image, rect);

            // Amplia 5x o QR para melhorar leitura
            Mat enlarged = new Mat();
            Imgproc.resize(qrRecortado, enlarged,
                    new Size(qrRecortado.width() * 5, qrRecortado.height() * 5),
                    0, 0, Imgproc.INTER_CUBIC);

            // Converte para escala de cinza
            Mat gray = new Mat();
            Imgproc.cvtColor(enlarged, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, gray, new Size(3, 3), 0);

            // Salva imagem temporária
            String tempPath = outputDir + "qr_" + recorteLado + "px.png";
            Imgcodecs.imwrite(tempPath, gray);

            // Tenta decodificar o QR
            try {
                BufferedImage bufferedImage = ImageIO.read(new File(tempPath));
                LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                Result result = new MultiFormatReader().decode(bitmap);

                System.out.println("✅ QR Code detectado com recorte de " + recorteLado + "px!");
                System.out.println("📦 Conteúdo: " + result.getText());
                encontrado = true;
                break;

            } catch (NotFoundException e) {
                System.out.println("❌ Nenhum QR encontrado nesse recorte.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!encontrado) {
            System.out.println("\n⚠ Nenhum QR Code detectado em nenhum dos tamanhos testados.");
        }
    }
}
