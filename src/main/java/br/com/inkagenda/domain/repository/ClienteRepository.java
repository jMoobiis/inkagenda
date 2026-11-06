package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.model.Cliente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByWhatsapp(String whatsapp);
}
