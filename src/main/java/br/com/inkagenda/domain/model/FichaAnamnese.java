package br.com.inkagenda.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FichaAnamnese {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "sessao_id", unique = true)
    private Sessao sessao;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    private String alergias;
    private String medicamentos;
    private String problemasCicatrizacao;
    private boolean diabetes;
    private boolean gestacao;

    @Column(length = 2000)
    private String observacoes;

    private boolean consentimentoDadosSaude;
    private LocalDateTime consentimentoEm;
    private LocalDateTime preenchidaEm;
}
