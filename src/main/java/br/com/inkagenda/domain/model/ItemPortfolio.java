package br.com.inkagenda.domain.model;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ItemPortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstiloTatuagem estilo;

    private String tagDestaque;

    @Column(nullable = false)
    private String caminhoImagem;

    private int displayOrder;
    private boolean ativo = true;
    private boolean fixadoHero;
}
