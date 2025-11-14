package seabdetector;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.util.*;

import static seabdetector.Constants.*;
import static seabdetector.DataModels.FolhaTemplate;

public class AnchorDetector {

    /**
     * Lógica de ordenação robusta dos 4 pontos de âncora encontrados.
     * Deve retornar a lista na ordem: TL, TR, BL, BR.
     */
    private static List<Point> sortAnchorPoints(List<Rect> ancorasRects) {
        List<Point> srcPointsList = new ArrayList<>();
        for (Rect r : ancorasRects) {
            // Adiciona o centro da âncora como o ponto de origem
            srcPointsList.add(new Point(r.x + r.width / 2.0, r.y + r.height / 2.0));
        }

        // 1. Encontra TL (menor soma x+y) e BR (maior soma x+y)
        Collections.sort(srcPointsList, (p1, p2) -> Double.compare(p1.x + p1.y, p2.x + p2.y));
        Point tl = srcPointsList.get(0); // Top-Left
        Point br = srcPointsList.get(3); // Bottom-Right

        // 2. Separa os dois pontos restantes
        Point p2 = srcPointsList.get(1);
        Point p3 = srcPointsList.get(2);

        Point tr, bl;

        // 3. Determina TR (Top-Right) e BL (Bottom-Left)
        // TR tem a MAIOR diferença (x - y) e BL tem a MENOR diferença (x - y)
        if ((p2.x - p2.y) > (p3.x - p3.y)) {
            tr = p2;
            bl = p3;
        } else {
            tr = p3;
            bl = p2;
        }

        // Retorna na ordem correta: TL, TR, BL, BR
        List<Point> sortedPoints = new ArrayList<>();
        sortedPoints.add(tl);
        sortedPoints.add(tr);
        sortedPoints.add(bl);
        sortedPoints.add(br);
        return sortedPoints;
    }


    /**
     * Detecta as âncoras (marcadores de alinhamento) nas 4 pontas da imagem.
     * @param imagem A imagem original.
     * @param outputDir O diretório para salvar imagens de debug e resultado.
     * @param nomeArquivoBase Nome da folha para nomear arquivos de saída.
     * @return Lista de 4 Pontos ordenados (TL, TR, BL, BR), ou null se falhar.
     */
    public static List<Point> findAnchorPoints(Mat imagem, String outputDir, String nomeArquivoBase) {
        int largura = imagem.cols();
        int altura = imagem.rows();
        int w = ANCHOR_SEARCH_SIZE, h = ANCHOR_SEARCH_SIZE;

        Rect[] regioes = new Rect[]{
                new Rect(0, 0, w, h),               // 0: Superior Esquerdo (TL)
                new Rect(largura - w, 0, w, h),      // 1: Superior Direito (TR)
                new Rect(0, altura - h, w, h),       // 2: Inferior Esquerdo (BL)
                new Rect(largura - w, altura - h, w, h) // 3: Inferior Direito (BR)
        };
        
        String[] regionNames = {"Superior Esquerdo", "Superior Direito", "Inferior Esquerdo", "Inferior Direito"};
        List<Rect> ancorasRects = new ArrayList<>();

        for (int i = 0; i < regioes.length; i++) {
            Rect roi = regioes[i];
            String regionName = regionNames[i];
            
            Mat regiao = null; Mat gray = null; Mat thresh = null; Mat hierarchy = null; Mat debugContornos = null;
            
            try {
                // --- Processamento da Região ---
                regiao = new Mat(imagem, roi);
                gray = new Mat(); thresh = new Mat(); hierarchy = new Mat();
                List<MatOfPoint> contornos = new ArrayList<>();

                Imgproc.cvtColor(regiao, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.adaptiveThreshold(gray, thresh, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                            Imgproc.THRESH_BINARY_INV, ADAPTIVE_THRESH_BLOCK_SIZE, ADAPTIVE_THRESH_C);
                
                // Imgcodecs.imwrite(outputDir + "DEBUG_ANCHOR_TH_" + regionName.replace(" ", "_") + "_" + nomeArquivoBase + ".jpg", thresh);
                
                Imgproc.findContours(thresh.clone(), contornos, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                
                debugContornos = new Mat(regiao.size(), regiao.type(), new Scalar(255, 255, 255));
                Imgproc.drawContours(debugContornos, contornos, -1, COLOR_CONTOUR, 1);
                
                Rect melhorCaixa = null;
                double maxAreaEncontrada = 0;
                
                // System.out.println("    [DEBUG] Região " + regionName + ": " + contornos.size() + " contornos iniciais.");
                
                for (MatOfPoint contorno : contornos) {
                    double area = Imgproc.contourArea(contorno);
                    if (area < ANCHOR_MIN_AREA || area > ANCHOR_MAX_AREA) {
                        contorno.release(); continue;
                    }

                    MatOfPoint2f contorno2f = new MatOfPoint2f(contorno.toArray());
                    MatOfPoint2f aprox = new MatOfPoint2f();
                    double perimetro = Imgproc.arcLength(contorno2f, true);
                    Imgproc.approxPolyDP(contorno2f, aprox, ANCHOR_APPROX_EPSILON * perimetro, true);
                    
                    if (aprox.total() == 4) {
                        MatOfPoint aproxPt = new MatOfPoint(aprox.toArray());
                        Rect caixa = Imgproc.boundingRect(aproxPt);
                        double aspect = (caixa.width > caixa.height) ? 
                                (double)caixa.width / caixa.height : (double)caixa.height / caixa.width;
                        
                        if (aspect <= ANCHOR_ASPECT_TOLERANCE) {
                            if (area > maxAreaEncontrada) {
                                maxAreaEncontrada = area;
                                caixa.x += roi.x; // Adiciona o offset da ROI
                                caixa.y += roi.y;
                                melhorCaixa = caixa;
                            }
                        }
                        aproxPt.release();
                    }
                    contorno.release(); contorno2f.release(); aprox.release();
                }
                
                // Imgcodecs.imwrite(outputDir + "DEBUG_ANCHOR_ALL_CONTOURS_" + regionName.replace(" ", "_") + "_" + nomeArquivoBase + ".jpg", debugContornos);

                if (melhorCaixa != null) {
                    System.out.println("    ✅ Encontrada âncora (" + regionName + ")! Área: " + String.format("%.1f", maxAreaEncontrada));
                    ancorasRects.add(melhorCaixa);
                    // Desenha a âncora na imagem original para o DEBUG de falha
                    Imgproc.rectangle(imagem, new Point(melhorCaixa.x, melhorCaixa.y), 
                                             new Point(melhorCaixa.x + melhorCaixa.width, melhorCaixa.y + melhorCaixa.height), 
                                             COLOR_BLUE, 3);
                } else {
                    System.out.println("    ❌ Nenhuma âncora válida encontrada na região (" + regionName + ").");
                }
            } finally {
                if (regiao != null) regiao.release(); 
                if (gray != null) gray.release(); 
                if (thresh != null) thresh.release(); 
                if (hierarchy != null) hierarchy.release();
                if (debugContornos != null) debugContornos.release();
            }
        }
        
        if (ancorasRects.size() != 4) {
            System.err.println("  ⚠ ERRO FATAL: não foram encontradas 4 âncoras. Não é possível alinhar.");
            Imgcodecs.imwrite(outputDir + OUTPUT_FAIL_PREFIX + nomeArquivoBase + ".jpg", imagem);
            return null;
        }

        // Ordena os pontos e retorna
        return sortAnchorPoints(ancorasRects);
    }
    
    /**
     * Aplica a transformação de perspectiva (Warp) na imagem.
     * Este é o antigo final de detectarETransformarAncoras.
     * @param imagem A imagem de origem.
     * @param template O template de destino para o tamanho e pontos ideais.
     * @param srcPoints Os 4 pontos de âncora encontrados, ordenados (TL, TR, BL, BR).
     * @param outputDir Diretório de saída para debug.
     * @param folha Nome da folha para arquivos de saída.
     * @param isInitialWarp Se true, usa o prefixo de recorte (crop); caso contrário, usa o prefixo de resultado.
     * @return Mat da imagem alinhada, ou null se falhar (embora não deva falhar aqui se os pontos forem válidos).
     */
    public static Mat warpImage(Mat imagem, FolhaTemplate template, List<Point> srcPoints, String outputDir, String folha, boolean isInitialWarp) {
        
        MatOfPoint2f src_points = new MatOfPoint2f(srcPoints.toArray(new Point[0]));
        MatOfPoint2f dst_points = template.idealPoints;
        Mat M = Imgproc.getPerspectiveTransform(src_points, dst_points);
        Mat warpedImage = new Mat();
        Size warpedSize = template.idealSize;
        Scalar fillColor = new Scalar(245, 245, 245);
        
        Imgproc.warpPerspective(imagem, warpedImage, M, warpedSize, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, fillColor);
        
        String prefix = isInitialWarp ? OUTPUT_CROP_PREFIX : OUTPUT_IMAGE_PREFIX;
        //Imgcodecs.imwrite(outputDir + prefix + folha.replace(" ", "") + ".jpg", warpedImage);
        
        System.out.println("  ✓ Imagem alinhada e salva. (Warp Inicial: " + isInitialWarp + ")");
        
        src_points.release();
        M.release();
        return warpedImage;
    }
}