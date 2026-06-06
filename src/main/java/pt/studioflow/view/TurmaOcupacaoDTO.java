package pt.studioflow.view;

public class TurmaOcupacaoDTO {
    private final String nome;
    private final int quantidade;

    public TurmaOcupacaoDTO(String nome, int quantidade) {
        this.nome = nome;
        this.quantidade = quantidade;
    }

    public String getNome() {
        return nome;
    }

    public int getQuantidade() {
        return quantidade;
    }
}
