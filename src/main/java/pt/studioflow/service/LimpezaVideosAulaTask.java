package pt.studioflow.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.studioflow.model.VideoAula;
import pt.studioflow.repository.VideoAulaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Apaga automaticamente os vídeos de aulas de anos letivos anteriores (do
 * bucket R2 e o registo na BD) assim que muda o ano letivo — mantém sempre
 * disponível apenas o ano letivo corrente (setembro a agosto).
 */
@Component
public class LimpezaVideosAulaTask {

    private final VideoAulaRepository videoAulaRepo;
    private final R2StorageService storageService;

    public LimpezaVideosAulaTask(VideoAulaRepository videoAulaRepo, R2StorageService storageService) {
        this.videoAulaRepo = videoAulaRepo;
        this.storageService = storageService;
    }

    // Todos os dias às 4h — idempotente, não faz nada se não houver vídeos de anos letivos anteriores
    @Scheduled(cron = "0 0 4 * * *")
    public void limparVideosDeAnosLetivosAnteriores() {
        List<VideoAula> antigos = videoAulaRepo.findByDataBefore(inicioAnoLetivoAtual());
        for (VideoAula v : antigos) {
            try {
                storageService.apagar(v.getChaveArmazenamento());
            } catch (Exception e) {
                System.err.println("Erro ao apagar vídeo " + v.getId() + " do storage: " + e.getMessage());
            }
            videoAulaRepo.delete(v);
        }
        if (!antigos.isEmpty()) {
            System.out.println("Limpeza de vídeos das aulas: removidos " + antigos.size()
                    + " vídeo(s) de anos letivos anteriores.");
        }
    }

    private LocalDate inicioAnoLetivoAtual() {
        LocalDate hoje = LocalDate.now();
        int anoInicio = hoje.getMonthValue() >= 9 ? hoje.getYear() : hoje.getYear() - 1;
        return LocalDate.of(anoInicio, 9, 1);
    }
}
