package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
}
