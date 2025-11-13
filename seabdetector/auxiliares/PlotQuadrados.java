package seabdetector;



import org.opencv.core.*;

import org.opencv.imgcodecs.Imgcodecs;

import org.opencv.imgproc.Imgproc;



import java.util.ArrayList;

import java.util.List;



public class PlotQuadrados {



static {

// Certifique-se de que este caminho está correto para a sua DLL

// Se a DLL estiver no java.library.path, use System.loadLibrary("opencv_java4120");

System.load("C:\\Users\\jasom\\opencv\\build\\java\\x64\\opencv_java4120.dll");

}



static class Alternativa {

String questao;

String opcao;

int x, y; // Coordenadas do CENTRO da bolha na IMAGEM ESCANEADA



public Alternativa(String questao, String opcao, int x, int y) {

this.questao = questao;

this.opcao = opcao;

this.x = x;

this.y = y;

}

}



public static void main(String[] args) {

// Use o caminho da sua folha escaneada original

String caminhoImagem = "C:\\Users\\jasom\\OneDrive\\Documentos\\NetBeansProjects\\SeabDetector\\testes\\entradas\\entradas_qr_finais\\entrada_folha1.jpg";

Mat imagem = Imgcodecs.imread(caminhoImagem);



if (imagem.empty()) {

System.out.println("❌ Erro ao carregar a imagem: " + caminhoImagem);

return;

}



// --- COORDENADAS DO SCANNER (CENTROS DAS BOLHAS) ---

List<Alternativa> alternativas = new ArrayList<>();



// Questão 1 (Múltipla Escolha)

alternativas.add(new Alternativa("Q1", "A", 44, 142));

alternativas.add(new Alternativa("Q1", "B", 44, 164));

alternativas.add(new Alternativa("Q1", "C", 44, 188));



// Questão 2 (Múltipla Escolha)

alternativas.add(new Alternativa("Q2", "A", 44, 256));

alternativas.add(new Alternativa("Q2", "B", 44, 280));

alternativas.add(new Alternativa("Q2", "C", 44, 304));

alternativas.add(new Alternativa("Q2", "D", 44, 328));

alternativas.add(new Alternativa("Q2", "E", 44, 352));

alternativas.add(new Alternativa("Q2", "F", 44, 374));



// Questão 3 (Múltipla Escolha)

alternativas.add(new Alternativa("Q3", "A", 44, 430));

alternativas.add(new Alternativa("Q3", "B", 44, 456));

alternativas.add(new Alternativa("Q3", "C", 44, 478));

alternativas.add(new Alternativa("Q3", "D", 44, 504));



// Questão 4 (Múltipla Escolha)

alternativas.add(new Alternativa("Q4", "A", 44, 566));

alternativas.add(new Alternativa("Q4", "B", 44, 592));

alternativas.add(new Alternativa("Q4", "C", 44, 616));

alternativas.add(new Alternativa("Q4", "D", 44, 640));

alternativas.add(new Alternativa("Q4", "E", 44, 660));

alternativas.add(new Alternativa("Q4", "F", 44, 682));



// Questão 5 (Matriz Sim/Não)

alternativas.add(new Alternativa("Q5A", "Nao", 426, 794));

alternativas.add(new Alternativa("Q5A", "Sim", 512, 794));

alternativas.add(new Alternativa("Q5B", "Nao", 426, 826));

alternativas.add(new Alternativa("Q5B", "Sim", 512, 826));

alternativas.add(new Alternativa("Q5C", "Nao", 426, 858));

alternativas.add(new Alternativa("Q5C", "Sim", 512, 858));



// Questão 6 (Múltipla Escolha)

alternativas.add(new Alternativa("Q6", "A", 44, 936));

alternativas.add(new Alternativa("Q6", "B", 44, 958));

alternativas.add(new Alternativa("Q6", "C", 44, 980));

alternativas.add(new Alternativa("Q6", "D", 44, 1004));



// --- PLOTAGEM ---



// Tamanho da área de busca (ex: 20x20 pixels ao redor do centro)

int tamanho_caixa = 10;



for (Alternativa alt : alternativas) {

// Calcula as coordenadas do canto superior esquerdo (x0, y0)

int x0 = alt.x - tamanho_caixa;

int y0 = alt.y - tamanho_caixa;



// Calcula as coordenadas do canto inferior direito (x1, y1)

int x1 = alt.x + tamanho_caixa;

int y1 = alt.y + tamanho_caixa;



Imgproc.rectangle(imagem,

new Point(x0, y0),

new Point(x1, y1),

new Scalar(0, 255, 0), // Cor Verde (BGR)

2); // Espessura da linha

}



String caminhoSaida = "C:\\Users\\jasom\\OneDrive\\Documentos\\NetBeansProjects\\SeabDetector\\testes\\entradas\\folha1_quadrados_saida.jpg";

Imgcodecs.imwrite(caminhoSaida, imagem);

System.out.println("✅ Imagem com quadrados salva: " + caminhoSaida);

}

}