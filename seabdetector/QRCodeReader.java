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
import java.io.File;

import static seabdetector.DataModels.*;

public class QRCodeReader {
    
    // Parâmetros de QR Code (Usando os valores originais)
    private static final int QR_SEARCH_SIZE = 300;
    private static final int QR_EXTRA_MARGIN = 20;

    // Método que executa o Pré-processamento (Suavização/Contraste) e a Decodificação Lenta (ZXing)
    private static String detectAndDecode(Mat image, String debugOutputPath, String baseFileName) throws Exception, NotFoundException {
        
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
        Mat smoothed = null; 
        Mat adjusted = null; 
        Mat thresholded = null;
        MatOfByte mob = null;
        
        try {
            // 1. Recorte
            qrRecortado = new Mat(image, rect);
            
            // 2. Ampliação
            enlarged = new Mat();
            Imgproc.resize(qrRecortado, enlarged,
                    new Size(qrRecortado.width() * 5, qrRecortado.height() * 5),
                    0, 0, Imgproc.INTER_LINEAR); 

            // 3. Converte para Cinza e Processamento Avançado (Recuperação de Dano)
            gray = new Mat();
            Imgproc.cvtColor(enlarged, gray, Imgproc.COLOR_BGR2GRAY);
            
            // --- OTIMIZAÇÃO: Suavização Bilateral e Ajuste de Contraste ---
            smoothed = new Mat();
            Imgproc.bilateralFilter(gray, smoothed, 15, 75, 75); 

            adjusted = new Mat();
            // Aumenta Contraste (1.5) e Reduz Brilho (-30)
            Core.convertScaleAbs(smoothed, adjusted, 1.5, -30); 
            // -------------------------------------------------------------
            
            // 4. Binarização Final (Otsu)
            thresholded = new Mat();
            Imgproc.threshold(adjusted, thresholded, 150, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
            
            // --- DEBUG VISUAL: Imagem Binarizada ---
            if (debugOutputPath != null && baseFileName != null) {
                Imgcodecs.imwrite(debugOutputPath + File.separator + baseFileName + "_QR_3_Binarizado.jpg", thresholded);
            }

            // 5. Decodificação (ZXing)
            mob = new MatOfByte();
            Imgcodecs.imencode(".png", thresholded, mob);
            
            try (InputStream is = new ByteArrayInputStream(mob.toArray())) {
                BufferedImage bufferedImage = ImageIO.read(is);
                LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source)); 
                
                Result result = new MultiFormatReader().decode(bitmap); 
                
                return result.getText();
            }

        } finally {
            // Liberação de recursos nativos
            if (qrRecortado != null) qrRecortado.release();
            if (enlarged != null) enlarged.release();
            if (gray != null) gray.release();
            if (smoothed != null) smoothed.release();
            if (adjusted != null) adjusted.release();
            if (thresholded != null) thresholded.release();
            if (mob != null) mob.release();
        }
    }


    /**
     * Tenta extrair e decodificar o QR Code na imagem BRUTA (0° ou 180°).
     * Este método é chamado duas vezes no fluxo de orientação do SaebDetector.
     * @param rawImage A imagem bruta (imagem) ou a imagem rotacionada (imagemRotacionada).
     * @return Dados do QR, ou null se falhar.
     */
    public static QrData extractAndParseFromRawImage(Mat rawImage, String debugOutputPath, String baseFileName) {
        long startTime = System.nanoTime();
        String qrDataBruta = null;
        
        try {
            qrDataBruta = detectAndDecode(rawImage, debugOutputPath, baseFileName);
            
            if (qrDataBruta != null) {
                // System.out.printf("  [DEB] Decodificação ZXing CONCLUÍDA. Tempo: %d ms\n", (System.nanoTime() - startTime) / 1_000_000);
                return parseQrCode(qrDataBruta);
            }
        } catch (NotFoundException e) {
            // Ignora NotFoundException, pois é uma falha esperada na Decodificação
        } catch (Exception e) {
            System.err.printf("  ❌ ERRO INESPERADO no processamento do QR Code: %s\n", e.getMessage());
        }
        return null;
    }
    
    // Método anterior, mantido por compatibilidade de chamada na seção OMR
    public static QrData extractAndParseFromWarped(Mat warpedImage, String debugOutputPath, String baseFileName) {
         // Chamamos a nova lógica unificada.
         return extractAndParseFromRawImage(warpedImage, debugOutputPath, baseFileName);
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
            // System.out.println("  ✓ QR Code decodificado: " + dados);
            return dados;

        } catch (NumberFormatException e) {
            System.err.printf("  ⚠ Erro ao decodificar o texto do QR Code: %s\n", e.getMessage());
            return null;
        }
    }
}