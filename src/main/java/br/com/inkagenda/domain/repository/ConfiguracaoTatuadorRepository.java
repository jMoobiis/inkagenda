package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoTatuadorRepository extends JpaRepository<ConfiguracaoTatuador, Long> {

    Optional<ConfiguracaoTatuador> findFirstByOrderByIdAsc();
}
