package mx.buap.veterinaria;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verificación integral contra PostgreSQL real en un esquema temporal de la base independiente vetcqs_test:
 * migraciones sobre datos previos, agenda (traslapes y reservas simultáneas), expediente (PDF y adjuntos),
 * farmacia (lotes, FEFO, caducados), devoluciones, indicadores conciliados, roles, CSRF y auditoría.
 * Nunca toca la base operativa: exige VET_POSTGRES_TEST=true y una URL localhost/vetcqs_test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "VET_POSTGRES_TEST", matches = "true")
class IntegralPostgresTest {
    static final String schema = "vet_test_" + UUID.randomUUID().toString().replace("-", "");
    static final String password = UUID.randomUUID().toString();
    static final ZoneId MX = ZoneId.of("America/Mexico_City");

    static String url() {
        String value = System.getenv("VET_TEST_DB_URL");
        if (value == null || !value.matches("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/vetcqs_test"))
            throw new IllegalStateException("Se requiere una base independiente vetcqs_test en localhost con puerto explícito");
        return value;
    }
    static Connection connection() throws Exception { return DriverManager.getConnection(url(), "vetcqs_test", System.getenv("VET_TEST_DB_PASSWORD")); }

    /** Esquema con el modelo original y datos previos sintéticos; Flyway aplica V2..V6 al arrancar. */
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema); s.execute("SET search_path TO " + schema);
            s.execute(Files.readString(Path.of("../bd/01_modelo_datos.sql")));
            s.execute(Files.readString(Path.of("../bd/03_iteracion_3_4.sql")));
            try (var insert = c.prepareStatement("insert into usuario(nombre,usuario,rol,activo,contrasena_hash) values ('Administrador de prueba','test_admin','ADMINISTRADOR',true,?)")) {
                insert.setString(1, new BCryptPasswordEncoder().encode(password)); insert.executeUpdate();
            }
            // Datos previos a las migraciones (deben conservarse intactos).
            s.execute("insert into duenio(nombre,telefono,correo) values ('Tutora previa','2220000001','previa@ejemplo.test')");
            s.execute("insert into mascota(duenio_id,nombre,especie,raza,sexo,peso_kg) values (1,'Firulais previo','Perro','Mestizo','M',12.5)");
            s.execute("insert into producto(nombre,tipo,precio,stock_actual,stock_minimo,maneja_caducidad) values ('Jarabe caducado','PRODUCTO',90,2,1,true)");
            s.execute("insert into lote(producto_id,numero_lote,caducidad,cantidad) values (1,'VIEJO-1',current_date - 5,2)");
        }
        p.add("spring.datasource.url", () -> url() + "?currentSchema=" + schema);
        p.add("spring.datasource.username", () -> "vetcqs_test");
        p.add("spring.datasource.password", () -> System.getenv("VET_TEST_DB_PASSWORD"));
        p.add("app.bootstrap-admin-password", () -> "");
    }
    @AfterAll static void limpiar() throws Exception {
        if (!schema.matches("vet_test_[a-f0-9]{32}")) throw new IllegalStateException();
        try (var c = connection(); var s = c.createStatement()) { s.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }

    @LocalServerPort int port;
    final ObjectMapper json = new ObjectMapper();

    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    Optional<String> xsrf(HttpClient c) {
        return c.cookieHandler().map(CookieManager.class::cast).flatMap(m -> m.getCookieStore().getCookies().stream()
                .filter(k -> k.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst());
    }
    HttpResponse<byte[]> raw(HttpClient c, String method, String path, Object body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        xsrf(c).ifPresent(t -> b.header("X-XSRF-TOKEN", t));
        var req = method.equals("GET") ? b.GET().build() : b.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return c.send(req, HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode request(HttpClient c, String method, String path, Object body, int expected) throws Exception {
        var res = raw(c, method, path, body);
        String texto = new String(res.body(), StandardCharsets.UTF_8);
        assertEquals(expected, res.statusCode(), method + " " + path + " => " + texto);
        return texto.isBlank() ? json.nullNode() : json.readTree(texto);
    }
    HttpClient login(String user) throws Exception { var c = client(); request(c, "POST", "/api/auth/login", Map.of("usuario", user, "contrasena", password), 200); return c; }
    int count(String sql) throws Exception {
        try (var c = connection(); var s = c.createStatement()) { s.execute("SET search_path TO " + schema); try (var rs = s.executeQuery(sql)) { rs.next(); return rs.getInt(1); } }
    }
    BigDecimal decimal(String sql) throws Exception {
        try (var c = connection(); var s = c.createStatement()) { s.execute("SET search_path TO " + schema); try (var rs = s.executeQuery(sql)) { rs.next(); return rs.getBigDecimal(1); } }
    }
    HttpResponse<String> multipart(HttpClient c, String path, String nombre, byte[] datos) throws Exception {
        String limite = "----prueba" + UUID.randomUUID();
        var cuerpo = new java.io.ByteArrayOutputStream();
        cuerpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"" + nombre + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        cuerpo.write(datos);
        cuerpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "multipart/form-data; boundary=" + limite);
        xsrf(c).ifPresent(t -> b.header("X-XSRF-TOKEN", t));
        return c.send(b.POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void verificacionIntegral() throws Exception {
        // ---------- Migraciones: los datos previos siguen ahí y las columnas nuevas existen.
        assertEquals(1, count("select count(*) from mascota where nombre='Firulais previo' and peso_kg=12.5"));
        assertEquals(1, count("select count(*) from lote where numero_lote='VIEJO-1' and cantidad_recibida=2"));
        assertTrue(count("select count(*) from flyway_schema_history where success") >= 6);
        int mascota = 1, jarabe = 1;

        var admin = login("test_admin");
        for (var e : Map.of("int_vet", "VETERINARIO", "int_caja", "CAJERO").entrySet())
            request(admin, "POST", "/api/empleados", Map.of("nombre", e.getKey(), "usuario", e.getKey(), "contrasena", password, "rol", e.getValue()), 201);
        var vet = login("int_vet"); var caja = login("int_caja");
        var empleados = raw(admin, "GET", "/api/empleados", null);
        String textoEmpleados = new String(empleados.body(), StandardCharsets.UTF_8);
        assertFalse(textoEmpleados.contains("contrasena") || textoEmpleados.contains("$2a$"), "No se exponen contraseñas ni hashes");
        assertEquals("DENY", empleados.headers().firstValue("X-Frame-Options").orElse(""));
        assertTrue(empleados.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'none'"));
        assertEquals("nosniff", empleados.headers().firstValue("X-Content-Type-Options").orElse(""));
        int vetId = request(vet, "GET", "/api/auth/sesion", null, 200).path("id").asInt();

        // ---------- Roles: sin sesión 401, sin permiso 403.
        request(client(), "GET", "/api/agenda/citas?desde=2026-01-01&hasta=2026-01-02", null, 401);
        request(caja, "GET", "/api/agenda/citas?desde=2026-01-01&hasta=2026-01-02", null, 403);
        request(caja, "GET", "/api/farmacia/proveedores", null, 403);
        request(vet, "GET", "/api/reportes/dashboard?desde=2026-01-01&hasta=2026-01-02", null, 403);
        request(vet, "GET", "/api/auditoria?desde=2026-01-01&hasta=2026-01-02", null, 403);
        request(caja, "GET", "/api/clinica/expedientes/" + mascota, null, 403);

        // ---------- Agenda: horario, alta, traslape y reservas simultáneas.
        LocalDate manana = LocalDate.now(MX).plusDays(1);
        request(admin, "POST", "/api/agenda/horarios", Map.of("veterinarioId", vetId, "diaSemana", manana.getDayOfWeek().getValue(), "horaInicio", "09:00", "horaFin", "13:00"), 201);
        int consultaGeneral = 0;
        for (JsonNode t : request(vet, "GET", "/api/agenda/tipos", null, 200)) if (t.path("nombre").asText().equals("Consulta general")) consultaGeneral = t.path("id").asInt();
        assertTrue(consultaGeneral > 0);
        final int tipo = consultaGeneral;
        java.util.function.Function<String, Map<String, Object>> cita = hora -> Map.of("mascotaId", mascota, "veterinarioId", vetId, "tipoCitaId", tipo,
                "inicio", manana + "T" + hora + ":00", "motivo", "Revisión");
        int cita10 = request(vet, "POST", "/api/agenda/citas", cita.apply("10:00"), 201).path("id").asInt();
        request(vet, "POST", "/api/agenda/citas", cita.apply("10:15"), 409);          // se traslapa con 10:00-10:30
        request(vet, "POST", "/api/agenda/citas", cita.apply("13:30"), 409);          // fuera del horario laboral
        ExecutorService hilos = Executors.newFixedThreadPool(6);
        List<Future<Integer>> intentos = new ArrayList<>();
        CountDownLatch salida = new CountDownLatch(1);
        for (int i = 0; i < 6; i++) intentos.add(hilos.submit(() -> { salida.await(); return raw(vet, "POST", "/api/agenda/citas", cita.apply("11:00")).statusCode(); }));
        salida.countDown();
        List<Integer> codigos = new ArrayList<>(); for (var f : intentos) codigos.add(f.get(30, TimeUnit.SECONDS));
        hilos.shutdown();
        assertEquals(1, Collections.frequency(codigos, 201), "Solo una reserva simultánea gana: " + codigos);
        assertEquals(5, Collections.frequency(codigos, 409), "Las demás reciben conflicto: " + codigos);
        assertEquals(1, count("select count(*) from cita where inicio = '" + manana + " 11:00'"));
        var espacios = request(vet, "GET", "/api/agenda/disponibilidad?veterinarioId=" + vetId + "&fecha=" + manana + "&tipoCitaId=" + tipo, null, 200);
        for (JsonNode e : espacios) assertFalse(e.path("inicio").asText().endsWith("T10:00:00") || e.path("inicio").asText().endsWith("T11:00:00"));
        request(vet, "PUT", "/api/agenda/citas/" + cita10 + "/estado", Map.of("estado", "CANCELADA"), 400);   // sin motivo
        request(vet, "PUT", "/api/agenda/citas/" + cita10 + "/estado", Map.of("estado", "CANCELADA", "motivo", "El tutor reprogramará"), 200);
        request(vet, "POST", "/api/agenda/citas", cita.apply("10:00"), 201);           // el espacio cancelado se libera
        assertTrue(request(vet, "GET", "/api/agenda/citas/" + cita10 + "/historial", null, 200).size() >= 2);
        String rango = "/api/agenda/citas?desde=" + manana + "&hasta=" + manana;
        assertEquals(3, request(vet, "GET", rango, null, 200).size());
        assertEquals(1, request(vet, "GET", rango + "&estado=CANCELADA", null, 200).size());
        assertEquals(3, request(vet, "GET", rango + "&veterinarioId=" + vetId + "&q=Firulais", null, 200).size());
        assertEquals(0, request(vet, "GET", rango + "&q=inexistente", null, 200).size());
        assertTrue(request(vet, "GET", "/api/agenda/recordatorios?horas=48", null, 200).size() >= 2);
        // Vincular la cita con la consulta real de la mascota y marcarla atendida.
        request(vet, "POST", "/api/clinica/consultas", Map.of("mascotaId", mascota, "motivo", "Fiebre", "temperaturaC", 60), 400);  // fuera de rango
        int consulta = request(vet, "POST", "/api/clinica/consultas", Map.of("mascotaId", mascota, "motivo", "Revisión", "diagnostico", "Otitis leve",
                "tratamiento", "Gotas", "pesoKg", 12.6, "temperaturaC", 38.6), 201).path("id").asInt();
        int cita11 = request(vet, "GET", rango + "&estado=PENDIENTE", null, 200).get(0).path("id").asInt();
        request(vet, "PUT", "/api/agenda/citas/" + cita11 + "/estado", Map.of("estado", "EN_CURSO"), 200);
        var atendida = request(vet, "PUT", "/api/agenda/citas/" + cita11 + "/atender", Map.of("consultaId", consulta), 200);
        assertEquals("ATENDIDA", atendida.path("estado").asText()); assertEquals(consulta, atendida.path("consultaId").asInt());

        // ---------- Expediente: vacuna, alergia, receta PDF, historial PDF y adjuntos seguros.
        request(vet, "POST", "/api/clinica/expedientes/" + mascota + "/vacunas", Map.of("vacuna", "Rabia", "fechaAplicacion", LocalDate.now(MX).toString(),
                "proximoRefuerzo", LocalDate.now(MX).plusYears(1).toString()), 201);
        request(vet, "POST", "/api/clinica/expedientes/" + mascota + "/antecedentes", Map.of("tipo", "ALERGIA", "descripcion", "Penicilina", "severidad", "GRAVE"), 201);
        var receta = request(vet, "POST", "/api/clinica/expedientes/" + mascota + "/recetas", Map.of("indicaciones", "Reposo",
                "medicamentos", List.of(Map.of("medicamento", "Meloxicam", "dosis", "0.1 mg/kg", "frecuencia", "Cada 24 h", "via", "Oral", "duracion", "5 días"))), 201);
        assertTrue(receta.path("folio").asText().startsWith("R-"));
        var pdfReceta = raw(vet, "GET", "/api/clinica/expedientes/recetas/" + receta.path("id").asInt() + "/pdf", null);
        assertEquals(200, pdfReceta.statusCode()); assertEquals("%PDF", new String(pdfReceta.body(), 0, 4, StandardCharsets.US_ASCII));
        var historial = raw(vet, "GET", "/api/clinica/expedientes/" + mascota + "/pdf", null);
        assertEquals(200, historial.statusCode()); assertEquals("%PDF", new String(historial.body(), 0, 4, StandardCharsets.US_ASCII));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var subido = multipart(vet, "/api/clinica/expedientes/" + mascota + "/documentos", "radiografia.png", png);
        assertEquals(201, subido.statusCode(), subido.body());
        var bajado = raw(vet, "GET", "/api/clinica/expedientes/documentos/" + json.readTree(subido.body()).path("id").asInt(), null);
        assertArrayEquals(png, bajado.body());
        assertEquals("nosniff", bajado.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals(415, multipart(vet, "/api/clinica/expedientes/" + mascota + "/documentos", "factura.pdf", new byte[]{'M', 'Z', 1, 2}).statusCode());
        request(vet, "POST", "/api/clinica/expedientes/" + mascota + "/preventivos", Map.of("tipo", "DESPARASITACION_INTERNA", "producto", "Praziquantel",
                "fechaAplicacion", LocalDate.now(MX).toString(), "proximaAplicacion", LocalDate.now(MX).plusMonths(3).toString()), 201);
        request(vet, "POST", "/api/clinica/expedientes/" + mascota + "/estudios", Map.of("tipo", "Biometría", "resultado", "Valores normales",
                "fecha", LocalDate.now(MX).toString(), "consultaId", consulta), 201);
        request(vet, "POST", "/api/clinica/expedientes/" + mascota + "/estudios", Map.of("tipo", "Biometría", "fecha", LocalDate.now(MX).plusDays(2).toString()), 400);
        assertEquals(1, request(vet, "GET", "/api/clinica/expedientes?q=Firulais", null, 200).size());
        assertEquals(1, request(vet, "GET", "/api/clinica/expedientes?diagnostico=Otitis", null, 200).size());
        var ficha = request(vet, "GET", "/api/clinica/expedientes/" + mascota, null, 200);
        assertEquals(1, ficha.path("preventivos").size()); assertEquals(1, ficha.path("estudios").size());
        assertEquals("Penicilina", ficha.path("alertas").get(0).path("descripcion").asText());
        assertEquals(1, ficha.path("documentos").size());

        // ---------- Farmacia: compra con lotes, FEFO, bloqueo de caducados.
        int proveedor = request(admin, "POST", "/api/farmacia/proveedores", Map.of("nombre", "Distribuidora de prueba", "rfc", "DPR010101AB1"), 201).path("id").asInt();
        int vacuna = request(admin, "POST", "/api/productos", Map.of("nombre", "Vacuna integral", "codigoBarras", "INT001", "tipo", "PRODUCTO", "precio", 100,
                "stockActual", 0, "stockMinimo", 1, "manejaCaducidad", true), 200).path("id").asInt();
        LocalDate hoy = LocalDate.now(MX);
        request(admin, "POST", "/api/farmacia/compras", Map.of("proveedorId", proveedor, "documento", "FAC-1", "productos", List.of(
                Map.of("productoId", vacuna, "numeroLote", "A-PRONTO", "caducidad", hoy.plusDays(10).toString(), "cantidad", 3, "costoUnitario", 40),
                Map.of("productoId", vacuna, "numeroLote", "B-TARDE", "caducidad", hoy.plusDays(100).toString(), "cantidad", 5, "costoUnitario", 45))), 201);
        request(admin, "POST", "/api/farmacia/compras", Map.of("proveedorId", proveedor, "productos", List.of(
                Map.of("productoId", vacuna, "numeroLote", "C-VENCIDO", "caducidad", hoy.minusDays(1).toString(), "cantidad", 1, "costoUnitario", 40))), 400);
        assertEquals(0, new BigDecimal("8").compareTo(decimal("select stock_actual from producto where id=" + vacuna)));
        request(caja, "POST", "/api/turnos", Map.of("fondoInicial", 500), 201);
        request(caja, "POST", "/api/ventas", Map.of("detalles", List.of(Map.of("productoId", jarabe, "cantidad", 1)), "descuento", 0), 409);   // solo existencia caducada
        int venta = request(caja, "POST", "/api/ventas", Map.of("detalles", List.of(Map.of("productoId", vacuna, "cantidad", 4)), "descuento", 0), 201).path("id").asInt();
        request(caja, "POST", "/api/ventas/" + venta + "/cobro", List.of(Map.of("forma", "EFECTIVO", "monto", 400, "recibido", 400)), 200);
        assertEquals(0, BigDecimal.ZERO.compareTo(decimal("select cantidad from lote where numero_lote='A-PRONTO'")), "FEFO agota primero el lote que vence antes");
        assertEquals(0, new BigDecimal("4").compareTo(decimal("select cantidad from lote where numero_lote='B-TARDE'")));
        assertEquals(2, count("select count(*) from movimiento_inventario where venta_id=" + venta + " and lote_id is not null"));
        assertEquals(1, request(admin, "GET", "/api/farmacia/lotes?proveedorId=" + proveedor, null, 200).size(), "Solo B-TARDE conserva existencia");
        assertEquals(2, request(admin, "GET", "/api/farmacia/lotes?productoId=" + vacuna + "&conExistencia=false", null, 200).size());
        assertTrue(request(admin, "GET", "/api/farmacia/caducidades?dias=60", null, 200).size() >= 1);
        int loteB = count("select id from lote where numero_lote='B-TARDE'");
        request(admin, "PUT", "/api/farmacia/lotes/" + loteB + "/ajuste", Map.of("cantidad", -1, "motivo", "Frasco dañado"), 200);
        request(admin, "PUT", "/api/farmacia/lotes/" + loteB + "/ajuste", Map.of("cantidad", -50, "motivo", "No negativos"), 409);
        request(admin, "PUT", "/api/farmacia/lotes/" + loteB + "/ajuste", Map.of("cantidad", 1, "motivo", "Conteo físico"), 200);
        var margen = request(admin, "GET", "/api/farmacia/margenes", null, 200);
        boolean conMargen = false; for (JsonNode m : margen) if (m.path("productoId").asInt() == vacuna) { conMargen = true; assertEquals(0, new BigDecimal("45").compareTo(m.path("costo").decimalValue())); }
        assertTrue(conMargen);
        assertEquals(0, new BigDecimal("4").compareTo(decimal("select stock_actual from producto where id=" + vacuna)));
        // Cobros simultáneos de la última pieza: solo uno se cobra y la existencia nunca queda negativa.
        int ultima = request(admin, "POST", "/api/productos", Map.of("nombre", "Collar única pieza", "codigoBarras", "INT002", "tipo", "PRODUCTO", "precio", 50,
                "stockActual", 1, "stockMinimo", 0), 200).path("id").asInt();
        List<Integer> ventasCompetidas = new ArrayList<>();
        for (int i = 0; i < 4; i++) ventasCompetidas.add(request(caja, "POST", "/api/ventas", Map.of("detalles", List.of(Map.of("productoId", ultima, "cantidad", 1)), "descuento", 0), 201).path("id").asInt());
        ExecutorService cobros = Executors.newFixedThreadPool(4); CountDownLatch disparo = new CountDownLatch(1); List<Future<Integer>> resultados = new ArrayList<>();
        for (int id : ventasCompetidas) resultados.add(cobros.submit(() -> { disparo.await(); return raw(caja, "POST", "/api/ventas/" + id + "/cobro", List.of(Map.of("forma", "TARJETA", "monto", 50))).statusCode(); }));
        disparo.countDown();
        List<Integer> estados = new ArrayList<>(); for (var f : resultados) estados.add(f.get(30, TimeUnit.SECONDS)); cobros.shutdown();
        assertEquals(1, Collections.frequency(estados, 200), "Un solo cobro gana: " + estados);
        assertEquals(0, BigDecimal.ZERO.compareTo(decimal("select stock_actual from producto where id=" + ultima)));
        assertEquals(1, count("select count(*) from venta where estado='PAGADA' and id in (" + ventasCompetidas.toString().replaceAll("[\\[\\]]", "") + ")"));
        assertEquals(1, count("select count(distinct venta_id) from pago where venta_id in (" + ventasCompetidas.toString().replaceAll("[\\[\\]]", "") + ")"), "Los cobros perdedores no dejan pagos");

        // ---------- Devolución: autorización, límite por lo vendido, reingreso a lote y efectivo del turno.
        request(caja, "POST", "/api/ventas/" + venta + "/devoluciones", Map.of("renglones", List.of(Map.of("productoId", vacuna, "cantidad", 1)),
                "motivo", "Cliente devolvió", "formaReembolso", "EFECTIVO", "usuarioAdmin", "test_admin", "claveAdmin", "incorrecta"), 403);
        var dev = request(caja, "POST", "/api/ventas/" + venta + "/devoluciones", Map.of("renglones", List.of(Map.of("productoId", vacuna, "cantidad", 1)),
                "motivo", "Cliente devolvió", "formaReembolso", "EFECTIVO", "usuarioAdmin", "test_admin", "claveAdmin", password), 201);
        assertEquals(0, new BigDecimal("100").compareTo(dev.path("monto").decimalValue()));
        request(caja, "POST", "/api/ventas/" + venta + "/devoluciones", Map.of("renglones", List.of(Map.of("productoId", vacuna, "cantidad", 4)),
                "motivo", "Excede", "formaReembolso", "EFECTIVO", "usuarioAdmin", "test_admin", "claveAdmin", password), 409);
        assertEquals(0, new BigDecimal("5").compareTo(decimal("select stock_actual from producto where id=" + vacuna)));
        assertEquals(0, new BigDecimal("5").compareTo(decimal("select cantidad from lote where numero_lote='B-TARDE'")));
        var corte = request(caja, "POST", "/api/turnos/cierre", Map.of("efectivoContado", 800), 200);
        assertEquals(0, new BigDecimal("800").compareTo(corte.path("efectivoEsperado").decimalValue()), "500 fondo + 400 venta - 100 reembolso");

        // ---------- Indicadores: conciliados con las tablas venta, pago y devolucion.
        var tablero = request(admin, "GET", "/api/reportes/dashboard?desde=" + hoy + "&hasta=" + hoy, null, 200);
        BigDecimal esperado = decimal("select coalesce(sum(total),0) from venta where estado='PAGADA' and fecha >= '" + hoy + "' and fecha < '" + hoy.plusDays(1) + "'")
                .subtract(decimal("select coalesce(sum(monto),0) from devolucion"));
        assertEquals(0, esperado.compareTo(tablero.path("periodo").path("ingresos").decimalValue()));
        assertEquals(0, new BigDecimal("400").compareTo(tablero.path("metodosPago").get(0).path("importe").decimalValue()));
        assertEquals(1, tablero.path("lotesPorCaducar").asInt(), "Solo VIEJO-1 (vencido con existencia); A-PRONTO quedó agotado");
        var pdf = raw(admin, "GET", "/api/reportes/dashboard.pdf?desde=" + hoy + "&hasta=" + hoy, null);
        assertEquals("%PDF", new String(pdf.body(), 0, 4, StandardCharsets.US_ASCII));
        var xlsx = raw(admin, "GET", "/api/reportes/dashboard.xlsx?desde=" + hoy + "&hasta=" + hoy, null);
        assertEquals("PK", new String(xlsx.body(), 0, 2, StandardCharsets.US_ASCII));
        request(admin, "GET", "/api/reportes/dashboard?desde=" + hoy + "&hasta=" + hoy.minusDays(1), null, 400);
        assertEquals("Vacuna integral", tablero.path("masVendidos").get(0).path("producto").asText());
        assertEquals(1, tablero.path("caja").path("turnos").asInt());
        assertEquals(0, BigDecimal.ZERO.compareTo(request(admin, "GET", "/api/reportes/dashboard?desde=" + hoy + "&hasta=" + hoy + "&tipo=SERVICIO", null, 200)
                .path("periodo").path("ingresos").decimalValue()), "Filtro por tipo: no hubo servicios vendidos");
        int cajaId = request(caja, "GET", "/api/auth/sesion", null, 200).path("id").asInt();
        assertEquals(0, esperado.compareTo(request(admin, "GET", "/api/reportes/dashboard?desde=" + hoy + "&hasta=" + hoy + "&usuarioId=" + cajaId, null, 200)
                .path("periodo").path("ingresos").decimalValue()), "Todas las ventas las cobró el cajero");
        var conCitas = request(admin, "GET", "/api/reportes/dashboard?desde=" + hoy + "&hasta=" + manana, null, 200);
        int citasTablero = 0; for (JsonNode c : conCitas.path("citasPorEstado")) citasTablero += c.path("cantidad").asInt();
        assertEquals(count("select count(*) from cita"), citasTablero);
        assertTrue(pdf.headers().firstValue("Content-Disposition").orElse("").contains("REP-"), "El reporte lleva folio");
        // Factura de demostración: leyenda explícita, sin pretender validez fiscal.
        var factura = request(admin, "POST", "/api/facturas/venta/" + venta, Map.of("rfcReceptor", "XAXX010101000", "razonSocial", "Público en general",
                "regimenFiscal", "616", "codigoPostal", "72000", "usoCfdi", "S01", "idempotencia", "int-1"), 201);
        assertTrue(factura.path("mock").asBoolean()); assertTrue(factura.path("aviso").asText().contains("no es un CFDI timbrado por el SAT"));

        // ---------- Auditoría y CSRF.
        var accesos = request(admin, "GET", "/api/auditoria?desde=" + hoy + "&hasta=" + hoy + "&accion=LOGIN_OK", null, 200);
        assertTrue(accesos.size() >= 3);
        assertTrue(count("select count(*) from bitacora where accion='DEVOLUCION' and autorizo_id is not null") >= 1);
        assertEquals(0, count("select count(*) from bitacora where detalle like '%" + password + "%'"), "La bitácora nunca guarda contraseñas");
        var sinToken = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/farmacia/proveedores")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"nombre\":\"Sin token\"}")).build();
        assertEquals(403, admin.send(sinToken, HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(1, count("select count(*) from proveedor"));

        // WebSocket: al cerrar sesión, el servidor cierra la conexión de esa caja.
        var cajaWs = login("int_caja");
        CompletableFuture<Integer> cerrado = new CompletableFuture<>();
        var ws = cajaWs.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + port + "/ws/caja"), new WebSocket.Listener() {
            public void onOpen(WebSocket w) { w.request(1); }
            public CompletionStage<?> onClose(WebSocket w, int codigo, String razon) { cerrado.complete(codigo); return null; }
            public void onError(WebSocket w, Throwable e) { cerrado.complete(-1); }
        }).get(5, TimeUnit.SECONDS);
        request(cajaWs, "POST", "/api/auth/logout", Map.of(), 204);
        assertEquals(1008, cerrado.get(5, TimeUnit.SECONDS), "Cierre por política al terminar la sesión");
        ws.abort();
        // Una sesión por usuario: el segundo inicio de sesión invalida el primero.
        var admin2 = login("test_admin");
        request(admin, "GET", "/api/auth/sesion", null, 401);
        assertEquals("ADMINISTRADOR", request(admin2, "GET", "/api/auth/sesion", null, 200).path("rol").asText());
    }
}
