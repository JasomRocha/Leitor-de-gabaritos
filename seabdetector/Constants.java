package seabdetector;

import org.opencv.core.Scalar;
import java.io.File;

public class Constants {
    
    public static final String OPENCV_DLL_PATH = System.getProperty("opencv.dll.path", "C:\\Users\\Jasom\\Desktop\\gits\\Leitor-de-gabaritos\\lib\\opencv_java4100.dll");
    public static final String OPENCV_DLL_PATH_HOME = System.getProperty("opencv.dll.path", "C:\\Users\\jasom\\OneDrive\\Documentos\\NetBeansProjects\\SeabDetector\\src\\lib\\opencv_java4120.dll");
    public static final String S = File.separator;
    
    // --- Caminhos de Arquivo ---
    public static final String PATH_CONFIG = "testes" + S + "config.txt";
    public static final String PATH_TEMPLATES = "testes" + S + "templates.txt";
    public static final String PATH_INPUT_DIR = "testes" + S + "entradas" + S + "entradas_novos_templates" + S;
    public static final String PATH_OUTPUT_DIR = "testes" + S + "saidas" + S + "saidas_qr" + S;
    public static final String OUTPUT_TXT_FILE_ORGANIZED = "respostas_organizadas.txt";
    public static final String OUTPUT_IMAGE_PREFIX = "resultado_";
    public static final String OUTPUT_FAIL_PREFIX = "falha_";
    public static final String OUTPUT_CROP_PREFIX = "recorte_";
    
    // --- Parâmetros de Detecção de Âncora ---
    public static final int ANCHOR_SEARCH_SIZE = 120;
    public static final double ANCHOR_MIN_AREA = 300.0;
    public static final double ANCHOR_MAX_AREA = 500.0;
    public static final double ANCHOR_APPROX_EPSILON = 0.08;
    public static final int ADAPTIVE_THRESH_BLOCK_SIZE = 15;
    public static final int ADAPTIVE_THRESH_C = 10;
    public static final double ANCHOR_ASPECT_TOLERANCE = 1.2;
    
    // --- Parâmetros de Detecção de Bolha (OMR) ---
    public static final int BUBBLE_RADIUS = 10;
    public static final double RELATIVE_MARK_THRESHOLD = 25.0;
    
    // --- Cores (para debug) ---
    public static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);
    public static final Scalar COLOR_RED = new Scalar(0, 0, 255);
    public static final Scalar COLOR_BLUE = new Scalar(255, 0, 0);
    public static final Scalar COLOR_CONTOUR = new Scalar(0, 0, 255); 

    // O tradutor de alternativas deve ser um método estático em uma classe de utilidade ou aqui.
    public static String traduzAlternativa(String alt) {
        switch (alt.toUpperCase()) {
            case "S": return "Sim";
            case "N": return "Nao";
            case "NQN": return "Nunca ou quase nunca";
            case "DVQ": return "De vez em quando";
            case "SQS": return "Sempre ou quase sempre";
            case "NU": return "Não uso meu tempo pra isso";
            case "M1H": return "Menos de 1 hora";
            case "E12H": return "Entre 1 e 2 horas";
            case "M2H": return "Mais de 2 horas";
            case "TE": return "Todos eles";
            case "AMP": return "A maior parte deles";
            case "PD": return "Poucos deles";
            case "ND": return "Nenhum deles";
            case "CT": return "Concordo totalmente";
            case "NEN": return "Nenhum";
            case "CNC": return "Concordo";
            case "DSC": return "Discordo";
            case "DT": return "Discordo totalmente";
            default: return alt;
        }
    }
}