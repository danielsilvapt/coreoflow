package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serviço para guardar logos de estúdios.
 * Guarda em app.logos.dir (por defeito: src/main/resources/static/images/studios)
 * e serve via /images/studios/** que já está permitido no SecurityConfig.
 */
@Service
public class LogoUploadService {

    @Value("${app.logos.dir:./uploads/logos}")
    private String logosDir;

    /**
     * Guarda o logo em ./uploads/logos/{slug}.ext
     * Servido via spring.web.resources.static-locations=file:./uploads/
     * URL resultante: /logos/{slug}.ext
     */
    public String saveLogo(String slug, InputStream inputStream, String mimeType) throws IOException {
        String ext = switch (mimeType) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/svg+xml" -> ".svg";
            default -> ".png";
        };

        Path dir = Paths.get(logosDir);
        Files.createDirectories(dir);

        String filename = slug + ext;
        Path target = dir.resolve(filename);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);

        return "logos/" + filename;
    }

    /**
     * Sugere cor primária e secundária a partir das cores dominantes do logo.
     * Devolve null se o ficheiro não for uma imagem rasterizável (ex: SVG) ou
     * não tiver cores suficientemente distintas para sugerir.
     */
    public String[] sugerirCores(String logoPath) {
        try {
            String filename = logoPath.substring(logoPath.lastIndexOf('/') + 1);
            Path file = Paths.get(logosDir).resolve(filename);
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                return null;
            }
            return extrairCoresDominantes(img);
        } catch (IOException e) {
            return null;
        }
    }

    private String[] extrairCoresDominantes(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int corFundo = img.getRGB(0, 0);
        int step = Math.max(1, Math.min(width, height) / 150);

        // bucket (RGB quantizado) -> [contagem, somaR, somaG, somaB]
        Map<Integer, int[]> clusters = new HashMap<>();

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int argb = img.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) < 128) {
                    continue; // pixel transparente
                }
                if (distanciaCor(argb, corFundo) < 40) {
                    continue; // parece ser o fundo do logo
                }

                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                int bucket = ((r / 24 * 24) << 16) | ((g / 24 * 24) << 8) | (b / 24 * 24);
                int[] acc = clusters.computeIfAbsent(bucket, k -> new int[4]);
                acc[0]++;
                acc[1] += r;
                acc[2] += g;
                acc[3] += b;
            }
        }

        if (clusters.isEmpty()) {
            return null;
        }

        List<int[]> ordenados = clusters.values().stream()
                .sorted(Comparator.comparingInt((int[] a) -> a[0]).reversed())
                .collect(Collectors.toList());

        int[] primario = ordenados.get(0);
        String hexPrimaria = corMediaHex(primario);

        for (int i = 1; i < ordenados.size(); i++) {
            int[] candidato = ordenados.get(i);
            if (distanciaMedias(primario, candidato) > 60) {
                return new String[] { hexPrimaria, corMediaHex(candidato) };
            }
        }

        // Logo essencialmente monocromático: sugere uma variante mais escura como secundária
        return new String[] { hexPrimaria, corMaisEscuraHex(primario) };
    }

    private double distanciaCor(int argb1, int rgb2) {
        int r1 = (argb1 >> 16) & 0xFF, g1 = (argb1 >> 8) & 0xFF, b1 = argb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
        return Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));
    }

    private double distanciaMedias(int[] a, int[] b) {
        double ar = (double) a[1] / a[0], ag = (double) a[2] / a[0], ab = (double) a[3] / a[0];
        double br = (double) b[1] / b[0], bg = (double) b[2] / b[0], bb = (double) b[3] / b[0];
        return Math.sqrt(Math.pow(ar - br, 2) + Math.pow(ag - bg, 2) + Math.pow(ab - bb, 2));
    }

    private String corMediaHex(int[] acc) {
        int r = acc[1] / acc[0], g = acc[2] / acc[0], b = acc[3] / acc[0];
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private String corMaisEscuraHex(int[] acc) {
        int r = (int) ((acc[1] / acc[0]) * 0.6);
        int g = (int) ((acc[2] / acc[0]) * 0.6);
        int b = (int) ((acc[3] / acc[0]) * 0.6);
        return String.format("#%02X%02X%02X", r, g, b);
    }
}
