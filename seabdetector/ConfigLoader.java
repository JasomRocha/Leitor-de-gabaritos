package seabdetector;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static seabdetector.DataModels.*;

public class ConfigLoader {

    public static List<Alternativa> loadAlternativas(String caminhoConfig) {
        List<Alternativa> lista = new ArrayList<>();
        String folhaAtual = "Desconhecida";
        try (BufferedReader br = new BufferedReader(new FileReader(caminhoConfig))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[FOLHA")) {
                    folhaAtual = line.replace("[", "").replace("]", "").trim();
                    continue;
                }
                String[] partes = line.split(";");
                if (partes.length == 4) {
                    lista.add(new Alternativa(folhaAtual, partes[0], partes[1],
                            Integer.parseInt(partes[2]), Integer.parseInt(partes[3])));
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler configuração: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Erro de formato de número em config.txt: " + e.getMessage());
        }
        return lista;
    }

    public static Map<String, FolhaTemplate> loadTemplates(String caminhoTemplates) {
        Map<String, FolhaTemplate> templates = new HashMap<>();
        String folhaAtual = null;
        try (BufferedReader br = new BufferedReader(new FileReader(caminhoTemplates))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[FOLHA")) {
                    folhaAtual = line.replace("[", "").replace("]", "").trim();
                    continue;
                }
                if (folhaAtual != null && line.startsWith("DADOS: ")) {
                    String[] partes = line.substring(7).split(";");
                    if (partes.length == 6) {
                        int w = Integer.parseInt(partes[0].trim());
                        int h = Integer.parseInt(partes[1].trim());
                        String[] tl = partes[2].trim().split(",");
                        String[] tr = partes[3].trim().split(",");
                        String[] bl = partes[4].trim().split(",");
                        String[] brCoords = partes[5].trim().split(",");
                        if (tl.length != 2 || tr.length != 2 || bl.length != 2 || brCoords.length != 2) {
                            System.err.println("⚠ Erro de formato de coordenada para " + folhaAtual);
                            continue;
                        }
                        Point pt_tl = new Point(Integer.parseInt(tl[0].trim()), Integer.parseInt(tl[1].trim()));
                        Point pt_tr = new Point(Integer.parseInt(tr[0].trim()), Integer.parseInt(tr[1].trim()));
                        Point pt_bl = new Point(Integer.parseInt(bl[0].trim()), Integer.parseInt(bl[1].trim()));
                        Point pt_br = new Point(Integer.parseInt(brCoords[0].trim()), Integer.parseInt(brCoords[1].trim()));
                        Size idealSize = new Size(w, h);
                        MatOfPoint2f idealPoints = new MatOfPoint2f(pt_tl, pt_tr, pt_bl, pt_br);
                        templates.put(folhaAtual, new FolhaTemplate(idealSize, idealPoints));
                    } else {
                        System.err.println("⚠ Aviso: Linha 'DADOS' mal formatada para " + folhaAtual + ". Esperados 6 campos, encontrados " + partes.length);
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erro ao ler templates: " + e.getMessage());
        }
        return templates;
    }
}