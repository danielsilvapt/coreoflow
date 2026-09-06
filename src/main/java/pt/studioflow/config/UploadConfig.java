package pt.studioflow.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

/**
 * Limites de upload definidos em código (não em {@code application.properties}),
 * porque o ficheiro de configuração de produção não os traz e ficava com o
 * default do Spring Boot de 1 MB — o que fazia falhar o envio de vídeos das
 * aulas e de fotos de alunos com {@code MaxUploadSizeExceededException} / 413.
 *
 * <p>Um bean {@link MultipartConfigElement} sobrepõe-se ao que o
 * {@code MultipartAutoConfiguration} deriva das propriedades.</p>
 */
@Configuration
public class UploadConfig {

    /** Vídeos das aulas chegam a algumas centenas de MB. */
    private static final DataSize MAX = DataSize.ofMegabytes(512);

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(MAX);
        factory.setMaxRequestSize(MAX);
        factory.setFileSizeThreshold(DataSize.ofKilobytes(0));
        return factory.createMultipartConfig();
    }

    /**
     * Sem isto, quando um upload excede o limite o Tomcat só "engole" 2 MB do
     * corpo antes de cortar a ligação, dando um erro pouco claro no browser em
     * vez de um 413 limpo.
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatMaxSwallowSize() {
        return factory -> factory.addConnectorCustomizers(
                (Connector connector) -> connector.setProperty("maxSwallowSize", "-1"));
    }
}
