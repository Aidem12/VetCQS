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

/** HTTP real + PostgreSQL real, en un esquema temporal sin modificar datos de la clínica. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="VET_POSTGRES_TEST",matches="true")
class FlujoPostgresTest {
 static final String schema="vet_test_"+UUID.randomUUID().toString().replace("-","");
 static final String password=UUID.randomUUID().toString();
 static String url(){return System.getenv().getOrDefault("VET_TEST_DB_URL","jdbc:postgresql://localhost:5432/veterinaria");}
 static Connection connection() throws Exception {return DriverManager.getConnection(url(),"veterinaria",System.getenv("VET_TEST_DB_PASSWORD"));}
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
  p.add("spring.datasource.username",()->"veterinaria");p.add("spring.datasource.password",()->System.getenv("VET_TEST_DB_PASSWORD"));
  p.add("app.bootstrap-admin-password",()->"");
 }
 @AfterAll static void limpiar() throws Exception {
  if(!schema.matches("vet_test_[a-f0-9]{32}"))throw new IllegalStateException();
  try(var c=connection();var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
 }
 @LocalServerPort int port;
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
  assertEquals("CAJERO",request(caja,"GET","/api/auth/sesion",null,200).path("rol").asText());
  int service=request(admin,"POST","/api/productos",Map.of("nombre","Consulta","tipo","SERVICIO","precio",350,"stockActual",0,"stockMinimo",0),200).path("id").asInt();
  int product=request(admin,"POST","/api/productos",Map.of("nombre","Insumo","codigoBarras","TEST001","tipo","PRODUCTO","precio",50,"stockActual",10,"stockMinimo",1),200).path("id").asInt();
  assertEquals(2,request(admin,"GET","/api/productos/catalogo",null,200).size());
  assertEquals(2,request(vet,"GET","/api/productos",null,200).size());
  int owner=request(vet,"POST","/api/clinica/duenos",Map.of("nombre","Tutor prueba","telefono","12345678"),201).path("id").asInt();
  int pet=request(vet,"POST","/api/clinica/duenos/"+owner+"/mascotas",Map.of("nombre","Paciente prueba","especie","Perro","sexo","M","pesoKg",5),201).path("id").asInt();
  int consultation=request(vet,"POST","/api/clinica/consultas",Map.of("mascotaId",pet,"motivo","Revisión","diagnostico","Diagnóstico de prueba","tratamiento","Indicaciones","pesoKg",5,"temperaturaC",38.5),201).path("id").asInt();
  assertEquals(1,request(vet,"GET","/api/clinica/mascotas/"+pet+"/consultas",null,200).size());
  int sale=request(vet,"POST","/api/ventas/precuentas",Map.of("consultaId",consultation,"detalles",List.of(Map.of("productoId",service,"cantidad",1),Map.of("productoId",product,"cantidad",2))),201).path("id").asInt();
  var pending=request(caja,"GET","/api/ventas/pendientes",null,200);
  assertEquals("Paciente prueba",pending.get(0).path("paciente").asText());
  assertEquals(2,pending.get(0).path("detalles").size());
  request(caja,"POST","/api/turnos",Map.of("fondoInicial",100,"denominaciones",Map.of("50",2)),201);
  request(caja,"POST","/api/turnos",Map.of("fondoInicial",0),409);
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
  request(caja,"POST","/api/auth/logout",Map.of(),204);
  request(caja,"GET","/api/ventas/pendientes",null,403);
 }
}
