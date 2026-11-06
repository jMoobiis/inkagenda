package br.com.inkagenda.domain.model;

import br.com.inkagenda.domain.enums.TipoImagem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ImagemReferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitacao_id")
    private Solicitacao solicitacao;

    @Column(nullable = false)
    private String caminho;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoImagem tipo;

    public ImagemReferencia(Solicitacao solicitacao, String caminho, TipoImagem tipo) {
        this.solicitacao = solicitacao;
        this.caminho = caminho;
        this.tipo = tipo;
    }
}
