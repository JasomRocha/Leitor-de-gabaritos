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
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

public class TestadorQRCode {

    static {
        // Certifique-se de que este caminho está correto para a sua DLL
        System.load("C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll");
    }

    public static void main(String[] args) {
        // Caminho da imagem contendo o QR Code
        // ATENÇÃO: Verifique se este caminho aponta para a sua imagem!
        String imagePath = "C:\\Users\\jasom\\opencv\\testes\\entradas\\entradas_novos_templates\\entrada_folha1.jpg";

        // Caminho base para salvar os arquivos de debug
        String outputDir = "C:\\Users\\jasom\\opencv\\testes\\saidas\\saidas_qr\\";
        new File(outputDir).mkdirs();

        // Cores (para debug)
        final Scalar COLOR_BLUE = new Scalar(255, 0, 0);

        // Carrega a imagem com o OpenCV
        Mat image = Imgcodecs.imread(imagePath);
        if (image.empty()) {
            System.out.println("❌ Erro: não foi possível carregar a imagem.");
            return;
        }

        System.out.println("🔍 Tentando localizar QR Code no CANTO INFERIOR DIREITO (270x270px)...");

        int ladoBusca = 250; 
        int margemExtra = 20; // 250 + 20 = 270px de busca
        boolean encontrado = false;

        // Usamos apenas um recorte de 270x270 para focar na área correta.
        int w_recorte = ladoBusca + margemExtra;
        int h_recorte = ladoBusca + margemExtra;

        // --- CÁLCULO PARA O CANTO INFERIOR DIREITO ---
        int x = Math.max(image.width() - w_recorte, 0); 
        int y = Math.max(image.height() - h_recorte, 0); 

        // Garante que o recorte não exceda os limites da imagem
        int w = Math.min(w_recorte, image.width() - x);
        int h = Math.min(h_recorte, image.height() - y);

        Rect rect = new Rect(x, y, w, h);
        Mat qrRecortado = null;
        Mat enlarged = null;
        Mat gray = null;
        MatOfByte mob = null;
        Mat thresholded = null; // Nova Mat para binarização

        try {
            // DEBUG: Plota a área de busca na imagem original (Clone)
            Mat debugImage = image.clone();
            Imgproc.rectangle(debugImage, 
                new Point(rect.x, rect.y), 
                new Point(rect.x + rect.width, rect.y + rect.height), 
                new Scalar(255, 0, 0), 5); // Cor azul grossa
            Imgcodecs.imwrite(outputDir + "DEBUG_QR_BUSCA_FINAL.jpg", debugImage);
            debugImage.release();

            // 1. Recorta a ROI
            qrRecortado = new Mat(image, rect);
            Imgcodecs.imwrite(outputDir + "DEBUG_QR_RECORTE_FINAL.jpg", qrRecortado);

            // 2. Amplia 5x o QR para melhorar leitura
            enlarged = new Mat();
            Imgproc.resize(qrRecortado, enlarged,
                    new Size(qrRecortado.width() * 5, qrRecortado.height() * 5),
                    0, 0, Imgproc.INTER_CUBIC);

            // 3. Converte para escala de cinza e aplica Gaussian Blur
            gray = new Mat();
            Imgproc.cvtColor(enlarged, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, gray, new Size(3, 3), 0);
            
            // 4. Binarização OBRIGATÓRIA para Leitores (ADICIONADO)
            thresholded = new Mat();
            // Tenta limiarização simples para transformar em P&B puro (ideal para QR)
            Imgproc.threshold(gray, thresholded, 150, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU); 
            
            Imgcodecs.imwrite(outputDir + "DEBUG_QR_DECODE_FINAL.jpg", thresholded); // Salva a versão P&B

            // 5. Tenta decodificar o QR
            mob = new MatOfByte();
            Imgcodecs.imencode(".png", thresholded, mob); // Usamos a imagem binarizada
            
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(mob.toArray()));
            LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(bitmap);

            System.out.println("✅ QR Code detectado!");
            System.out.println("📦 Conteúdo: " + result.getText());
            encontrado = true;

        } catch (NotFoundException e) {
            System.out.println("❌ Nenhum QR encontrado no recorte.");
        } catch (Exception e) {
            System.out.println("❌ Erro inesperado durante a decodificação: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Libera recursos do OpenCV
            if (qrRecortado != null) qrRecortado.release();
            if (enlarged != null) enlarged.release();
            if (gray != null) gray.release();
            if (thresholded != null) thresholded.release(); // Libera novo recurso
            if (mob != null) mob.release();
        }

        if (!encontrado) {
            System.out.println("\n⚠ Nenhum QR Code detectado. Verifique os arquivos DEBUG_QR_* no diretório de saída.");
        }
    }
}