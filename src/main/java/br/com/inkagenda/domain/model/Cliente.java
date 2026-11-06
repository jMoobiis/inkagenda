package br.com.inkagenda.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Somente digitos, com DDD e sem o codigo do pais. */
    @Column(nullable = false, unique = true, length = 11)
    private String whatsapp;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    public Cliente(String nome, String whatsapp, LocalDateTime criadoEm) {
        this.nome = nome;
        this.whatsapp = whatsapp;
        this.criadoEm = criadoEm;
    }
}
