package br.com.inkagenda.infra;

import br.com.inkagenda.domain.model.UsuarioAdmin;
import br.com.inkagenda.domain.repository.UsuarioAdminRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garante o usuario admin em todos os profiles, com credenciais vindas da configuracao. Sem senha
 * configurada, gera uma aleatoria a cada inicio e a mostra uma vez no console.
 */
@Component
@Order(1)
public class AdminInitializer implements ApplicationRunner {

    private final UsuarioAdminRepository admins;
    private final PasswordEncoder codificador;
    private final String email;
    private final String senhaConfigurada;

    public AdminInitializer(UsuarioAdminRepository admins, PasswordEncoder codificador,
            @Value("${inkagenda.admin.email:admin@inkagenda.local}") String email,
            @Value("${inkagenda.admin.senha:}") String senhaConfigurada) {
        this.admins = admins;
        this.codificador = codificador;
        this.email = email;
        this.senhaConfigurada = senhaConfigurada;
    }

    @Override
    public void run(ApplicationArguments args) {
        String senha = senhaConfigurada;
        if (senha.isBlank()) {
            senha = senhaAleatoria();
            System.out.println("InkAgenda: senha gerada para " + email + ": " + senha);
        }
        String hash = codificador.encode(senha);
        UsuarioAdmin admin = admins.findByEmail(email).orElseGet(() -> new UsuarioAdmin(email, hash));
        admin.setSenhaHash(hash);
        admins.save(admin);
    }

    private static String senhaAleatoria() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
