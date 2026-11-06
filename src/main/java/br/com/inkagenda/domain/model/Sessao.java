package br.com.inkagenda.domain.model;

import br.com.inkagenda.domain.enums.StatusSessao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Sessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "solicitacao_id")
    private Solicitacao solicitacao;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusSessao status = StatusSessao.PRE_RESERVADA;

    private LocalDateTime expiraEm;

    private LocalDate dataPagamento;
    private BigDecimal valorRecebido;
    private BigDecimal valorAAbater;
    private String comprovante;

    private boolean presencaConfirmada;
}
