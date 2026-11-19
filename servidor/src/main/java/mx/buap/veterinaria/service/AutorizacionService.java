package mx.buap.veterinaria.service;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.seguridad.LimiteIntentos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Autorizaciones de administrador (descuentos, cancelaciones, salidas, devoluciones) con límite de intentos. */
@Service
public class AutorizacionService {
 private final UsuarioRepository usuarios; private final PasswordEncoder encoder; private final BitacoraRepository bitacora;
 private LimiteIntentos limite;
 public AutorizacionService(UsuarioRepository u,PasswordEncoder e,BitacoraRepository b){usuarios=u;encoder=e;bitacora=b;}
 @Autowired(required=false) public void setLimite(LimiteIntentos limite){this.limite=limite;}
 public Usuario validar(String usuario,String clave){
  String llave = LimiteIntentos.clave("autorizacion", usuario, null);
  if (limite != null && limite.segundosBloqueado(llave) > 0)
   throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Demasiadas autorizaciones fallidas para ese administrador. Espere 5 minutos.");
  var admin = usuarios.findByUsuarioAndActivoTrue(usuario).filter(u -> u.getRol()==Usuario.Rol.ADMINISTRADOR)
   .filter(u -> clave != null && encoder.matches(clave,u.getContrasenaHash()));
  if (admin.isEmpty()) {
   if (limite != null && limite.fallo(llave))
    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Demasiadas autorizaciones fallidas para ese administrador. Espere 5 minutos.");
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Credenciales de administrador incorrectas");
  }
  if (limite != null) limite.exito(llave);
  return admin.get();
 }
 public void registrar(Integer operador,Integer admin,String accion,String tabla,Integer registro,String detalle){
  bitacora.save(new Bitacora(operador,admin,accion,tabla,registro,detalle));
 }
}
