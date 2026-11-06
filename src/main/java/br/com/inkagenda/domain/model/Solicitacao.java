package br.com.inkagenda.domain.model;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Solicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private Integer tamanhoCm;
    private String regiaoCorpo;

    @Enumerated(EnumType.STRING)
    private EstiloTatuagem estilo;

    private boolean cobertura;

    @Column(length = 2000)
    private String descricao;

    private String preferenciaDias;
    private boolean aceitePrivacidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusSolicitacao status = StatusSolicitacao.NOVA;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    @OneToMany(mappedBy = "solicitacao", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ImagemReferencia> imagens = new ArrayList<>();

    private BigDecimal valorTotal;
    private Integer duracaoEstimadaHoras;
    private BigDecimal valorSinal;

    @Column(unique = true)
    private String tokenAgendamento;

    private LocalDateTime orcadaEm;
}
