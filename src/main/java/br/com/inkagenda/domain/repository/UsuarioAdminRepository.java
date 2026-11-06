package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.model.UsuarioAdmin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioAdminRepository extends JpaRepository<UsuarioAdmin, Long> {

    Optional<UsuarioAdmin> findByEmail(String email);
}
