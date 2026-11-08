package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.model.ConfiguracaoVitrine;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoVitrineRepository extends JpaRepository<ConfiguracaoVitrine, Long> {

    Optional<ConfiguracaoVitrine> findFirstByOrderByIdAsc();
}
