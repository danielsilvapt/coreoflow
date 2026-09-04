package pt.studioflow.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Envio de emails em segundo plano.
 *
 * O envio de email (SMTP) nunca deve bloquear uma ação do utilizador (ex.: gravar um
 * aluno, marcar uma aula experimental, etc.). Se o servidor de email estiver em baixo,
 * mal configurado ou apenas lento, a UI não pode "congelar" à espera da resposta do SMTP.
 * O EmailService usa este executor (via @Async) para despachar os emails sem bloquear a
 * thread do pedido.
 */
@Configuration
@EnableAsync
public class AsyncEmailConfig implements AsyncConfigurer {

    public static final String EMAIL_EXECUTOR = "emailExecutor";

    @Bean(name = EMAIL_EXECUTOR)
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return emailExecutor();
    }
}
