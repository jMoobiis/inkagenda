package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.model.FichaAnamnese;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FichaAnamneseRepository extends JpaRepository<FichaAnamnese, Long> {

    Optional<FichaAnamnese> findByToken(String token);

    Optional<FichaAnamnese> findBySessaoId(Long sessaoId);
}
