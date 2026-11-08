package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.model.ImagemReferencia;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagemReferenciaRepository extends JpaRepository<ImagemReferencia, Long> {

    Optional<ImagemReferencia> findByIdAndSolicitacaoId(Long id, Long solicitacaoId);
}
