package pt.studioflow.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.studioflow.model.DriveVideoDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoogleDriveService {

        @Value("${google.drive.client-id}")
        private String clientId;

        @Value("${google.drive.client-secret}")
        private String clientSecret;

        @Value("${google.drive.refresh-token}")
        private String refreshToken;

        private Drive getDriveService() throws Exception {
                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                // Obtém um novo Access Token usando o Refresh Token
                GoogleTokenResponse response = new GoogleRefreshTokenRequest(
                                httpTransport, jsonFactory, refreshToken, clientId, clientSecret)
                                .execute();

                Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                                .setAccessToken(response.getAccessToken());

                return new Drive.Builder(httpTransport, jsonFactory, credential)
                                .setApplicationName("Obidos Dance")
                                .build();
        }

        public List<DriveVideoDTO> listarVideosDaPasta(String folderId) {
                if (folderId == null || folderId.isEmpty())
                        return new ArrayList<>();

                try {
                        // Limpeza de segurança do ID da pasta
                        if (folderId.contains("?"))
                                folderId = folderId.substring(0, folderId.indexOf("?"));
                        if (folderId.contains("folders/"))
                                folderId = folderId.substring(folderId.lastIndexOf("/") + 1);

                        Drive service = getDriveService();
                        // Filtra por ficheiros de vídeo que não estão no lixo
                        String query = String.format(
                                        "'%s' in parents and mimeType contains 'video/' and trashed = false", folderId);

                        var result = service.files().list()
                                        .setQ(query)
                                        .setFields("files(id, name, thumbnailLink, webViewLink)")
                                        .execute();

                        return result.getFiles().stream()
                                        .map(file -> {
                                                String fileId = file.getId();

                                                // NOVO LINK: Este link é mais "aberto" para miniaturas em aplicações
                                                // web
                                                String thumbUrl = "https://lh3.googleusercontent.com/u/0/d/" + fileId
                                                                + "=w400-h225-p-k-no";

                                                // Se o de cima falhar em alguns casos, este é o plano B (oficial do
                                                // Drive):
                                                // String thumbUrl = "https://drive.google.com/thumbnail?id=" + fileId +
                                                // "&sz=w400";

                                                String embedUrl = "https://drive.google.com/file/d/" + fileId
                                                                + "/preview";

                                                return new DriveVideoDTO(
                                                                file.getName(),
                                                                thumbUrl,
                                                                file.getWebViewLink(),
                                                                embedUrl);
                                        })
                                        .collect(Collectors.toList());

                } catch (Exception e) {
                        System.err.println("Erro ao listar vídeos do Drive: " + e.getMessage());
                        return new ArrayList<>();
                }
        }
}