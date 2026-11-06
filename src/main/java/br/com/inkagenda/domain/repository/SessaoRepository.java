package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.model.Sessao;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {

    List<Sessao> findByStatusInAndInicioBeforeAndFimAfter(Collection<StatusSessao> status,
            LocalDateTime ate, LocalDateTime de);

    List<Sessao> findByStatusAndExpiraEmBefore(StatusSessao status, LocalDateTime momento);

    List<Sessao> findByStatusAndInicioBetweenOrderByInicio(StatusSessao status, LocalDateTime de,
            LocalDateTime ate);
}
