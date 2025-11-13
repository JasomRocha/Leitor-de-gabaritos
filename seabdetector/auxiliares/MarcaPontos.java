package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Point;
import java.awt.image.DataBufferByte;
import org.opencv.objdetect.QRCodeDetector;



public class MarcaPontos {
    static { System.load("C:\\Users\\Jasom\\Desktop\\gits\\Leitor-de-gabaritos\\lib\\opencv_java4100.dll"); }
    public static void main(String[] args) {
        String caminho = "C:\\Users\\Jasom\\Desktop\\gits\\Leitor-de-gabaritos\\testes\\entradas\\entrada_folha1.jpg";
        Mat img = Imgcodecs.imread(caminho);

        if (img.empty()) {
            System.out.println("❌ Erro ao carregar imagem.");
            return;
        }

        System.out.println("Dimensões originais: " + img.width() + "x" + img.height());

        double escala = 0.56; // ajuste de zoom
        Mat imgReduzida = new Mat();
        Imgproc.resize(img, imgReduzida, new Size(img.width() * escala, img.height() * escala));

        // Converter Mat para BufferedImage
        BufferedImage imagem = matToBufferedImage(imgReduzida);

        // Criar janela
        JFrame frame = new JFrame("Clique nas bolhas (ESC para sair)");
        JLabel label = new JLabel(new ImageIcon(imagem));
        frame.getContentPane().add(label, BorderLayout.CENTER);

        List<Point> pontos = new ArrayList<>();

        // Registrar cliques
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int x = (int) (e.getX() / escala);
                int y = (int) (e.getY() / escala);
                pontos.add(new Point(x, y));
                System.out.println("Bolha " + pontos.size() + ": (" + x + ", " + y + ")");
            }
        });

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Esperar ESC para fechar
        while (true) {
            try {
                Thread.sleep(100);
                if ((KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .getActiveWindow() == null)) break;
            } catch (InterruptedException ex) { break; }
        }

        System.out.println("\nTotal de pontos marcados: " + pontos.size());
        for (int i = 0; i < pontos.size(); i++) {
            System.out.println("Bolha " + (i + 1) + ": " + pontos.get(i));
        }
    }

    // Conversão de Mat → BufferedImage
    public static BufferedImage matToBufferedImage(Mat matrix) {
        int tipo = BufferedImage.TYPE_BYTE_GRAY;
        if (matrix.channels() > 1) tipo = BufferedImage.TYPE_3BYTE_BGR;
        int bufferSize = matrix.channels() * matrix.cols() * matrix.rows();
        byte[] b = new byte[bufferSize];
        matrix.get(0, 0, b);
        BufferedImage image = new BufferedImage(matrix.cols(), matrix.rows(), tipo);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }
}

