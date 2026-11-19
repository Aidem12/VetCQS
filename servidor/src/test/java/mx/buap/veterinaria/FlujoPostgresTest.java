package mx.buap.veterinaria;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP + PostgreSQL en una base independiente obligatoria; nunca usa la configuración real. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="VET_POSTGRES_TEST",matches="true")
class FlujoPostgresTest {
 static final String schema="vet_test_"+UUID.randomUUID().toString().replace("-","");
 static final String password=UUID.randomUUID().toString();
 static String url(){
  String value=System.getenv("VET_TEST_DB_URL");
  if(value==null || !value.matches("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/vetcqs_test"))
   throw new IllegalStateException("Se requiere una base independiente vetcqs_test en localhost con puerto explícito");
  return value;
 }
 static Connection connection() throws Exception {return DriverManager.getConnection(url(),"vetcqs_test",System.getenv("VET_TEST_DB_PASSWORD"));}
 @DynamicPropertySource static void database(DynamicPropertyRegistry p) throws Exception {
  try(var c=connection();var s=c.createStatement()){
   s.execute("CREATE SCHEMA "+schema);s.execute("SET search_path TO "+schema);
   s.execute(Files.readString(Path.of("../bd/01_modelo_datos.sql")));
   s.execute(Files.readString(Path.of("../bd/03_iteracion_3_4.sql")));
   try(var insert=c.prepareStatement("insert into usuario(nombre,usuario,rol,activo,contrasena_hash) values ('Administrador de prueba','test_admin','ADMINISTRADOR',true,?)")){
    insert.setString(1,new BCryptPasswordEncoder().encode(password));insert.executeUpdate();
   }
  }
  p.add("spring.datasource.url",()->url()+"?currentSchema="+schema);
  p.add("spring.datasource.username",()->"vetcqs_test");p.add("spring.datasource.password",()->System.getenv("VET_TEST_DB_PASSWORD"));
  p.add("app.bootstrap-admin-password",()->"");
 }
 @AfterAll static void limpiar() throws Exception {
  if(!schema.matches("vet_test_[a-f0-9]{32}"))throw new IllegalStateException();
  try(var c=connection();var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
 }
 @LocalServerPort int port;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean mx.buap.veterinaria.config.WebSocketConfig.AvisosCaja avisos;
 @org.springframework.beans.factory.annotation.Autowired org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext web;
 final ObjectMapper json=new ObjectMapper();
 HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
 JsonNode request(HttpClient c,String method,String path,Object body,int expected) throws Exception {
  var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
  var req=method.equals("GET")?b.GET().build():b.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
  var res=c.send(req,HttpResponse.BodyHandlers.ofString());
  assertEquals(expected,res.statusCode(),method+" "+path+" => "+res.body());
  return res.body().isBlank()?json.nullNode():json.readTree(res.body());
 }
 HttpClient login(String user) throws Exception {var c=client();request(c,"POST","/api/auth/login",Map.of("usuario",user,"contrasena",password),200);return c;}
 @Test void personalConsultaPrecuentaAutorizacionCobroYCorte() throws Exception {
  var admin=login("test_admin");
  for(var entry:Map.of("test_caja","CAJERO","test_vet","VETERINARIO").entrySet()){
   request(admin,"POST","/api/empleados",Map.of("nombre",entry.getKey(),"usuario",entry.getKey(),"contrasena",password,"rol",entry.getValue()),201);
  }
  assertEquals(3,request(admin,"GET","/api/empleados",null,200).size());
  var caja=login("test_caja");var vet=login("test_vet");
  var conectado=new java.util.concurrent.CountDownLatch(1);
  org.mockito.Mockito.doAnswer(i -> {i.callRealMethod(); conectado.countDown(); return null;}).when(avisos).afterConnectionEstablished(org.mockito.ArgumentMatchers.any());
  var mensaje=new java.util.concurrent.CompletableFuture<String>();
  var listener=new WebSocket.Listener(){
   public void onOpen(WebSocket w){w.request(1);}
   public java.util.concurrent.CompletionStage<?> onText(WebSocket w,CharSequence data,boolean last){mensaje.complete(data.toString());w.request(1);return null;}
  };
  var ws=caja.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws/caja"),listener).get(5,java.util.concurrent.TimeUnit.SECONDS);
  try {
   assertTrue(conectado.await(5,java.util.concurrent.TimeUnit.SECONDS));
   avisos.avisar("{\"tipo\":\"PRUEBA\"}");
   assertEquals("{\"tipo\":\"PRUEBA\"}",mensaje.get(5,java.util.concurrent.TimeUnit.SECONDS));
   var rechazo=assertThrows(java.util.concurrent.ExecutionException.class,()->vet.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws/caja"),new WebSocket.Listener(){}).get(5,java.util.concurrent.TimeUnit.SECONDS));
   assertInstanceOf(WebSocketHandshakeException.class,rechazo.getCause());
   assertEquals(403,((WebSocketHandshakeException)rechazo.getCause()).getResponse().statusCode());
  } finally { ws.sendClose(WebSocket.NORMAL_CLOSURE,"fin prueba").join(); }
  request(client(),"GET","/api/productos",null,403);
  request(caja,"GET","/api/empleados",null,403); request(vet,"GET","/api/empleados",null,403);
  request(caja,"GET","/api/clinica/duenos",null,403); request(vet,"GET","/api/ventas/pendientes",null,403);
  request(client(),"POST","/api/auth/login",Map.of("usuario","test_admin","contrasena","incorrecta"),401);
  int adminId=request(admin,"GET","/api/auth/sesion",null,200).path("id").asInt();
  assertEquals("CAJERO",request(caja,"GET","/api/auth/sesion",null,200).path("rol").asText());
  int service=request(admin,"POST","/api/productos",Map.of("nombre","Consulta","tipo","SERVICIO","precio",350,"stockActual",0,"stockMinimo",0),200).path("id").asInt();
  int product=request(admin,"POST","/api/productos",Map.of("nombre","Insumo","codigoBarras","TEST001","tipo","PRODUCTO","precio",50,"stockActual",10,"stockMinimo",1),200).path("id").asInt();
  assertEquals(2,request(admin,"GET","/api/productos/catalogo",null,200).size());
  assertEquals(2,request(vet,"GET","/api/productos",null,200).size());
  int owner=request(vet,"POST","/api/clinica/duenos",Map.of("nombre","Tutor prueba","telefono","12345678"),201).path("id").asInt();
  int pet=request(vet,"POST","/api/clinica/duenos/"+owner+"/mascotas",Map.of("nombre","Paciente prueba","especie","Perro","sexo","M","pesoKg",5),201).path("id").asInt();
  int consultation=request(vet,"POST","/api/clinica/consultas",Map.of("mascotaId",pet,"motivo","Revisión","diagnostico","Diagnóstico de prueba","tratamiento","Indicaciones","pesoKg",5,"temperaturaC",38.5),201).path("id").asInt();
  assertEquals(1,request(vet,"GET","/api/clinica/mascotas/"+pet+"/consultas",null,200).size());
  request(vet,"POST","/api/ventas/precuentas",Map.of("consultaId",consultation,"detalles",List.of(Map.of("productoId",service,"cantidad",-1))),400);
  request(vet,"POST","/api/ventas/precuentas",Map.of("consultaId",consultation,"detalles",List.of(Map.of("productoId",service,"cantidad",0.001))),400);
  int sale=request(vet,"POST","/api/ventas/precuentas",Map.of("consultaId",consultation,"detalles",List.of(Map.of("productoId",service,"cantidad",1),Map.of("productoId",product,"cantidad",2))),201).path("id").asInt();
  request(vet,"POST","/api/ventas/precuentas",Map.of("consultaId",consultation,"detalles",List.of(Map.of("productoId",service,"cantidad",1))),409);
  var pending=request(caja,"GET","/api/ventas/pendientes",null,200);
  assertEquals("Paciente prueba",pending.get(0).path("paciente").asText());
  assertEquals(2,pending.get(0).path("detalles").size());
  request(caja,"POST","/api/turnos",Map.of("fondoInicial",100,"denominaciones",Map.of("50",2)),201);
  request(caja,"POST","/api/turnos",Map.of("fondoInicial",0),409);
  request(admin,"POST","/api/turnos",Map.of("fondoInicial",0),409);
  request(admin,"POST","/api/turnos/cierre",Map.of("efectivoContado",100),409);
  request(admin,"POST","/api/ventas/"+sale+"/cobro",List.of(Map.of("forma","EFECTIVO","monto",450,"recibido",450)),403);
  request(caja,"POST","/api/ventas/"+sale+"/autorizar",Map.of("usuarioAdmin","test_vet","claveAdmin",password,"motivo","Prueba","accion","DESCUENTO","importe",50),403);
  var authorized=request(caja,"POST","/api/ventas/"+sale+"/autorizar",Map.of("usuarioAdmin","test_admin","claveAdmin",password,"motivo","Prueba","accion","DESCUENTO","importe",50),200);
  assertEquals(400,authorized.path("total").intValue());
  var paid=request(caja,"POST","/api/ventas/"+sale+"/cobro",List.of(Map.of("forma","EFECTIVO","monto",300,"recibido",300),Map.of("forma","TARJETA","monto",100)),200);
  assertEquals("PAGADA",paid.path("estado").asText());
  assertEquals(8,request(caja,"GET","/api/productos/codigo/TEST001",null,200).path("stockActual").intValue());
  request(caja,"POST","/api/ventas/"+sale+"/cobro",List.of(Map.of("forma","EFECTIVO","monto",400,"recibido",400)),409);
  request(caja,"POST","/api/turnos/salidas",Map.of("concepto","Prueba","monto",50,"usuarioAdmin","test_admin","claveAdmin",password),201);
  var close=request(caja,"POST","/api/turnos/cierre",Map.of("efectivoContado",350,"denominaciones",Map.of("50",7),"tarjetaContada",100,"transferenciaContada",0),200);
  assertEquals(350,close.path("efectivoEsperado").intValue());assertEquals(0,close.path("diferencia").intValue());
  assertEquals(1,request(admin,"GET","/api/turnos/historial",null,200).size());
  request(admin,"GET","/api/turnos/"+close.path("id").asInt()+"/reporte",null,200);
  request(vet,"GET","/api/turnos/historial",null,403);
  // Inventario real de la base aislada: precisión, atribución, historial y kardex.
  request(admin,"POST","/api/inventario/"+product+"/entradas",Map.of("cantidad",2,"usuarioId",adminId,"motivo","Prueba"),201);
  request(admin,"POST","/api/inventario/"+product+"/ajustes",Map.of("cantidad",-2,"usuarioId",adminId,"motivo","Conteo"),201);
  request(admin,"POST","/api/inventario/"+product+"/ajustes",Map.of("cantidad",-99,"usuarioId",adminId,"motivo","No negativo"),409);
  request(admin,"POST","/api/inventario/"+product+"/ajustes",Map.of("cantidad",1,"usuarioId",999,"motivo","No suplantación"),403);
  request(admin,"GET","/api/inventario/"+product+"/movimientos",null,200);
  assertEquals(3,request(admin,"GET","/api/inventario/"+product+"/kardex",null,200).size());
  request(admin,"PUT","/api/productos/"+product,Map.of("nombre","Insumo editado","codigoBarras","TEST001","tipo","PRODUCTO","precio",50,"stockActual",999,"stockMinimo",20),200);
  assertEquals(8,request(admin,"GET","/api/productos/codigo/TEST001",null,200).path("stockActual").intValue());
  assertEquals(1,request(admin,"GET","/api/inventario/alertas",null,200).size());
  request(admin,"POST","/api/inventario/"+product+"/ajustes",Map.of("cantidad",0.001,"usuarioId",adminId,"motivo","Precisión"),400);
  // El administrador usa su identidad en clínica y en su propio turno.
  int adminConsult=request(admin,"POST","/api/clinica/consultas",Map.of("mascotaId",pet,"motivo","Atención administrativa"),201).path("id").asInt();
  try(var c=connection();var s=c.createStatement();var rs=s.executeQuery("select veterinario_id from "+schema+".consulta where id="+adminConsult)) {assertTrue(rs.next());assertEquals(adminId,rs.getInt(1));}
  var adminTurno=request(admin,"POST","/api/turnos",Map.of("fondoInicial",10,"denominaciones",Map.of("0.50",20)),201);
  assertEquals(adminId,adminTurno.path("cajeroId").asInt());
  int firstSale=0, lastSale=0;
  for(String forma:List.of("EFECTIVO","TARJETA","TRANSFERENCIA")) {
   int venta=request(admin,"POST","/api/ventas",Map.of("detalles",List.of(Map.of("productoId",product,"cantidad",1)),"descuento",0),201).path("id").asInt();
   if(firstSale==0)firstSale=venta;lastSale=venta;
   request(admin,"POST","/api/ventas/"+venta+"/cobro",List.of(Map.of("forma",forma,"monto",50,"recibido",50)),200);
  }
  assertEquals(5,request(admin,"GET","/api/productos/codigo/TEST001",null,200).path("stockActual").intValue());
  // Cambio de existencias entre captura y cobro debe revertir pagos y venta.
  int pendiente=request(admin,"POST","/api/ventas",Map.of("detalles",List.of(Map.of("productoId",product,"cantidad",5)),"descuento",0),201).path("id").asInt();
  request(admin,"POST","/api/inventario/"+product+"/ajustes",Map.of("cantidad",-1,"usuarioId",adminId,"motivo","Cambio antes del cobro"),201);
  request(admin,"POST","/api/ventas/"+pendiente+"/cobro",List.of(Map.of("forma","EFECTIVO","monto",250,"recibido",250)),409);
  try(var c=connection();var s=c.createStatement();var rs=s.executeQuery("select count(*) from "+schema+".pago where venta_id="+pendiente)){assertTrue(rs.next());assertEquals(0,rs.getInt(1));}
  assertEquals("ABIERTA",request(admin,"GET","/api/ventas/"+pendiente,null,200).path("estado").asText());
  assertEquals("ADMINISTRADOR",request(admin,"GET","/api/auth/sesion",null,200).path("rol").asText());
  int cancel=request(admin,"POST","/api/ventas",Map.of("detalles",List.of(Map.of("productoId",service,"cantidad",1)),"descuento",0),201).path("id").asInt();
  request(admin,"POST","/api/ventas/"+cancel+"/autorizar",Map.of("usuarioAdmin","test_admin","claveAdmin",password,"motivo","Prueba cancelación","accion","CANCELAR_VENTA"),200);
  request(admin,"POST","/api/ventas/"+cancel+"/cobro",List.of(Map.of("forma","EFECTIVO","monto",350,"recibido",350)),409);
  var adminClose=request(admin,"POST","/api/turnos/cierre",Map.of("efectivoContado",59,"denominaciones",Map.of("1",59),"tarjetaContada",50,"transferenciaContada",50),200);
  assertEquals(-1,adminClose.path("diferencia").intValue());
  assertEquals(1,request(caja,"GET","/api/turnos/historial",null,200).size());
  assertEquals(2,request(admin,"GET","/api/turnos/historial",null,200).size());
  request(caja,"GET","/api/turnos/"+adminClose.path("id").asInt()+"/reporte",null,409);
  var fiscal=new HashMap<String,Object>(Map.of("rfcReceptor","XAXX010101000","razonSocial","Prueba","regimenFiscal","616","codigoPostal","72000","usoCfdi","S01","idempotencia","fase1-factura"));
  var factura=request(admin,"POST","/api/facturas/venta/"+firstSale,fiscal,201);
  assertTrue(factura.path("mock").asBoolean());
  assertEquals(factura.path("id"),request(admin,"POST","/api/facturas/venta/"+firstSale,fiscal,201).path("id"));
  request(admin,"POST","/api/facturas/venta/"+lastSale,fiscal,409);
  fiscal.put("idempotencia","otra-clave"); request(admin,"POST","/api/facturas/venta/"+firstSale,fiscal,409);
  fiscal.put("rfcReceptor","INVALIDO"); request(admin,"POST","/api/facturas/venta/"+lastSale,fiscal,400);
  // Usuario desactivado no puede mantener una sesión previa ni iniciar otra.
  int vetId=request(vet,"GET","/api/auth/sesion",null,200).path("id").asInt();
  request(admin,"PUT","/api/empleados/"+vetId+"/desactivar",Map.of(),200);
  request(vet,"GET","/api/clinica/duenos",null,403);
  request(client(),"POST","/api/auth/login",Map.of("usuario","test_vet","contrasena",password),401);
  request(caja,"POST","/api/auth/logout",Map.of(),204);
  request(caja,"GET","/api/ventas/pendientes",null,403);
  // Ejercitar la expiración real de Tomcat sin esperas de inactividad ni tocar configuración productiva.
  var tomcat=((org.springframework.boot.web.embedded.tomcat.TomcatWebServer)web.getWebServer()).getTomcat();
  int expiradas=0;
  for(var child:tomcat.getHost().findChildren()) {
   if(child instanceof org.apache.catalina.Context context) for(var s:context.getManager().findSessions()) {
    if(Integer.valueOf(adminId).equals(s.getSession().getAttribute("usuarioId"))) {s.expire();expiradas++;}
   }
  }
  assertEquals(1,expiradas);
  request(admin,"GET","/api/productos/catalogo",null,403); request(admin,"GET","/api/auth/sesion",null,401);
 }
}
