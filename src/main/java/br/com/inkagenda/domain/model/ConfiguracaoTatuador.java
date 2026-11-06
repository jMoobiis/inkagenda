package br.com.inkagenda.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoTatuador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String enderecoEstudio;
    private String chavePix;

    @Column(nullable = false)
    private LocalTime aberturaExpediente = LocalTime.of(10, 0);

    @Column(nullable = false)
    private LocalTime fimExpediente = LocalTime.of(18, 0);

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dias_atendimento")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> diasAtendimento = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    private int intervaloLimpezaMinutos = 30;
    private int percentualSinal = 30;
    private int diasSemRetorno = 3;
    private int antecedenciaMinimaHoras = 48;
    private int prazoPreReservaHoras = 24;
    private int validadeAnamneseHoras = 72;
}
