package br.com.inkagenda.infra;

import br.com.inkagenda.domain.repository.UsuarioAdminRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Sessao com login em /login, /admin/** protegido e CSRF ativo em todas as rotas. */
@Configuration
public class SegurancaConfig {

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/admin", "/admin/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/"))
                .build();
    }

    @Bean
    public PasswordEncoder codificadorSenha() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService usuarios(UsuarioAdminRepository admins) {
        return email -> admins.findByEmail(email)
                .map(admin -> User.withUsername(admin.getEmail())
                        .password(admin.getSenhaHash())
                        .roles("ADMIN")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
