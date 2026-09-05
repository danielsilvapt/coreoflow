package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Armazenamento de vídeos das aulas no Cloudflare R2 (bucket único da
 * plataforma, compatível com a API S3). Ao contrário do
 * {@link GoogleDriveService} (legado, credenciais por conta Google
 * partilhada/por estúdio), aqui não há configuração por estúdio: é
 * infraestrutura da própria CoreoFlow.
 */
@Service
public class R2StorageService {

    @Value("${r2.account-id}")
    private String accountId;

    @Value("${r2.access-key-id}")
    private String accessKeyId;

    @Value("${r2.secret-access-key}")
    private String secretAccessKey;

    @Value("${r2.bucket}")
    private String bucket;

    private URI endpoint() {
        return URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
    }

    private StaticCredentialsProvider credenciais() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }

    private S3Client client() {
        return S3Client.builder()
                .endpointOverride(endpoint())
                .region(Region.of("auto"))
                .credentialsProvider(credenciais())
                .build();
    }

    /** Faz upload de um vídeo para o bucket, na chave indicada. */
    public void upload(String chave, String mimeType, InputStream conteudo, long tamanhoBytes) {
        try (S3Client s3 = client()) {
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(chave).contentType(mimeType).build(),
                    RequestBody.fromInputStream(conteudo, tamanhoBytes));
        }
    }

    /** Apaga um vídeo do bucket (ex: quando o professor o remove). */
    public void apagar(String chave) {
        try (S3Client s3 = client()) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(chave).build());
        }
    }

    /**
     * Gera um link temporário e assinado para ver o vídeo (reprodução) — o
     * bucket é privado, por isso os alunos não acedem diretamente pela chave.
     */
    public String gerarUrlTemporario(String chave, Duration validade) {
        return gerarUrlAssinado(chave, validade, null);
    }

    /** Como {@link #gerarUrlTemporario}, mas força o download em vez de reproduzir inline. */
    public String gerarUrlDownload(String chave, String nomeFicheiro, Duration validade) {
        String nome = nomeFicheiro != null ? nomeFicheiro.replace("\"", "") : "video.mp4";
        return gerarUrlAssinado(chave, validade, "attachment; filename=\"" + nome + "\"");
    }

    private String gerarUrlAssinado(String chave, Duration validade, String contentDisposition) {
        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(endpoint())
                .region(Region.of("auto"))
                .credentialsProvider(credenciais())
                .build()) {
            GetObjectRequest.Builder getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(chave);
            if (contentDisposition != null) {
                getObjectRequest.responseContentDisposition(contentDisposition);
            }
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(validade)
                    .getObjectRequest(getObjectRequest.build())
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        }
    }
}
