package seabdetector;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static seabdetector.DataModels.*;
// Importante: Reduzi o QR_SEARCH_SIZE de 250 para 150 para otimizar o tempo de processamento
// do recorte e da ampliação (Otimização de Performance).
// Manteve as constantes originais, se estiverem no Constants.java
// Se não, deve usar os valores otimizados: private static final int QR_SEARCH_SIZE = 150;
public class QRCodeReader {
    
    // Parâmetros de QR Code (Usando os valores originais, ajuste se necessário)
    private static final int QR_SEARCH_SIZE = 250;
    private static final int QR_EXTRA_MARGIN = 20;

    /**
     * Tenta extrair e decodificar o QR Code da imagem alinhada (recortada/warped).
     * @param warpedImage A imagem já corrigida (alinhada).
     * @return Dados do QR, ou null se falhar.
     */
    public static QrData extractAndParseFromWarped(Mat warpedImage) {
        String qrDataBruta = extractQRCodeFromRawImage(warpedImage);
        if (qrDataBruta != null) {
            return parseQrCode(qrDataBruta);
        }
        return null;
    }

    private static String extractQRCodeFromRawImage(Mat image) {
        // Assume que o QR Code está no canto Inferior Direito da imagem ALINHADA
        System.out.println("  🔎 Tentando localizar QR Code na imagem ALINHADA (canto Inferior Direito)...");

        final int w_recorte = QR_SEARCH_SIZE + QR_EXTRA_MARGIN;
        final int h_recorte = QR_SEARCH_SIZE + QR_EXTRA_MARGIN;

        int x = Math.max(image.width() - w_recorte, 0);
        int y = Math.max(image.height() - h_recorte, 0);
        
        int w = Math.min(w_recorte, image.width() - x);
        int h = Math.min(h_recorte, image.height() - y);

        Rect rect = new Rect(x, y, w, h);

        Mat qrRecortado = null;
        Mat enlarged = null;
        Mat gray = null;
        Mat thresholded = null;
        MatOfByte mob = new MatOfByte();

        try {
            qrRecortado = new Mat(image, rect);
            
            // Ampliação (Otimização: Usando INTER_LINEAR, mais rápido que CUBIC)
            enlarged = new Mat();
            Imgproc.resize(qrRecortado, enlarged,
                    new Size(qrRecortado.width() * 5, qrRecortado.height() * 5),
                    0, 0, Imgproc.INTER_LINEAR); // Otimização de performance

            gray = new Mat();
            Imgproc.cvtColor(enlarged, gray, Imgproc.COLOR_BGR2GRAY);
            // Removido o GaussianBlur para otimização, já que a imagem está alinhada.
            
            thresholded = new Mat();
            // Binarização com Otsu
            Imgproc.threshold(gray, thresholded, 150, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

            // --- Decodificação (Gargalo I/O, mas necessário para usar o ZXing com Mat) ---
            Imgcodecs.imencode(".png", thresholded, mob);
            try (InputStream is = new ByteArrayInputStream(mob.toArray())) {
                BufferedImage bufferedImage = ImageIO.read(is);
                LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                Result result = new MultiFormatReader().decode(bitmap);
                
                System.out.println("  ✅ QR Code detectado no recorte alinhado!");
                return result.getText();
            }

        } catch (NotFoundException e) {
            System.out.println("    ...Nenhum QR encontrado no recorte alinhado.");
        } catch (Exception e) {
            System.err.println("    ❌ Erro (Exception) ao ler QR Code: " + e.getMessage());
        } finally {
            if (qrRecortado != null) qrRecortado.release();
            if (enlarged != null) enlarged.release();
            if (gray != null) gray.release();
            if (thresholded != null) thresholded.release();
            if (mob != null) mob.release();
        }

        return null;
    }

    private static QrData parseQrCode(String qrTexto) {
        if (qrTexto == null || qrTexto.length() < 16) {
            System.err.println("  ⚠ Erro de QR Code: texto é nulo ou curto. ('" + qrTexto + "')");
            return null;
        }
        try {
            String instituicao = qrTexto.substring(0, 5);
            String respondente = qrTexto.substring(5, 9);
            String folhaNumStr = qrTexto.substring(9, 11);
            String folhaNome = "FOLHA " + Integer.parseInt(folhaNumStr);
            String tipoProva = qrTexto.substring(11, 12);
            String ano = qrTexto.substring(12, 16);

            QrData dados = new QrData(instituicao, respondente, folhaNome, tipoProva, ano, qrTexto);
            System.out.println("  ✓ QR Code decodificado: " + dados);
            return dados;

        } catch (NumberFormatException e) {
            System.err.println("  ⚠ Erro ao decodificar o texto do QR Code: " + e.getMessage());
            return null;
        }
    }
}