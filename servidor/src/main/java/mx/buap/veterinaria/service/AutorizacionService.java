package mx.buap.veterinaria.service;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.model.*;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class AutorizacionService {
 private final UsuarioRepository usuarios; private final PasswordEncoder encoder; private final BitacoraRepository bitacora;
 public AutorizacionService(UsuarioRepository u,PasswordEncoder e,BitacoraRepository b){usuarios=u;encoder=e;bitacora=b;}
 public Usuario validar(String usuario,String clave){
  return usuarios.findByUsuarioAndActivoTrue(usuario).filter(u -> u.getRol()==Usuario.Rol.ADMINISTRADOR)
   .filter(u -> encoder.matches(clave,u.getContrasenaHash()))
   .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,"Credenciales de administrador incorrectas"));
 }
 public void registrar(Integer operador,Integer admin,String accion,String tabla,Integer registro,String detalle){
  bitacora.save(new Bitacora(operador,admin,accion,tabla,registro,detalle));
 }
}
