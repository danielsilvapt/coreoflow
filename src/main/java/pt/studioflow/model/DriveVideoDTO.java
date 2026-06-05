package pt.studioflow.model; 

public class DriveVideoDTO {
    private String nome;
    private String thumbnailUrl;
    private String webViewUrl;
    private String embedUrl;

    public DriveVideoDTO(String nome, String thumbnailUrl, String webViewUrl, String embedUrl) {
        this.nome = nome;
        this.thumbnailUrl = thumbnailUrl;
        this.webViewUrl = webViewUrl;
        this.embedUrl = embedUrl;
    }

    // Getters
    public String getNome() { return nome; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getWebViewUrl() { return webViewUrl; }
    public String getEmbedUrl() { return embedUrl; }
}