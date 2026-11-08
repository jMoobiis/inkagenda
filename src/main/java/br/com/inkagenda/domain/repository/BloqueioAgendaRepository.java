package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.model.BloqueioAgenda;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloqueioAgendaRepository extends JpaRepository<BloqueioAgenda, Long> {

    List<BloqueioAgenda> findByInicioBeforeAndFimAfter(LocalDateTime ate, LocalDateTime de);
}
