package mx.buap.veterinaria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import mx.buap.veterinaria.model.Usuario;
import mx.buap.veterinaria.repository.UsuarioRepository;

@Component
public class BootstrapAdmin implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String password;
    private final String username;
    private final String name;

    public BootstrapAdmin(UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin-password:}") String password,
            @Value("${app.bootstrap-admin-username:admin}") String username,
            @Value("${app.bootstrap-admin-name:Administrador}") String name) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.password = password;
        this.username = username;
        this.name = name;
    }

    @Override
    public void run(String... args) {
        if (password == null || password.length() < 8) return;
        Usuario admin = usuarios.findByUsuario(username).orElseGet(Usuario::new);
        admin.setNombre(name);
        admin.setUsuario(username);
        admin.setRol(Usuario.Rol.ADMINISTRADOR);
        admin.setActivo(true);
        admin.setContrasenaHash(encoder.encode(password));
        usuarios.save(admin);
    }
}
