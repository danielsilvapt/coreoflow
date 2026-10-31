package pt.studioflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = "pt.studioflow.repository")
@EntityScan(basePackages = "pt.studioflow.model")
@PWA(name = "CoreoFlow", shortName = "CoreoFlow", iconPath = "icons/icon.png", backgroundColor = "#ffffff00", themeColor = "#2F6FD6")
@Theme("coreoflow")
public class CadenzioApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(CadenzioApplication.class, args);
    }
}
