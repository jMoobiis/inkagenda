package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.model.Solicitacao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    Optional<Solicitacao> findByTokenAgendamento(String tokenAgendamento);

    List<Solicitacao> findByStatusOrderByCriadaEmDesc(StatusSolicitacao status);
}
