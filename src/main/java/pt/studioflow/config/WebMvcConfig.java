package pt.studioflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Serve ficheiros estáticos externos (logos dos estúdios) via /logos/**
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.logos.dir:./uploads/logos}")
    private String logosDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(logosDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/logos/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
