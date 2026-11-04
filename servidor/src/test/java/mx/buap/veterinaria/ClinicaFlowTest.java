package mx.buap.veterinaria;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import mx.buap.veterinaria.config.SecurityConfig;
import mx.buap.veterinaria.controller.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

@WebMvcTest({ClinicaController.class, ProductoController.class, AuthController.class})
@Import(SecurityConfig.class)
class ClinicaFlowTest {
    @Autowired MockMvc mvc;
    @MockitoBean DuenoRepository duenos;
    @MockitoBean MascotaRepository mascotas;
    @MockitoBean ConsultaRepository consultas;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean ProductoRepository productos;

    private MockHttpSession session(String role) {
        MockHttpSession s = new MockHttpSession(); s.setAttribute("usuarioId",42); s.setAttribute("rol",role); return s;
    }

    @Test void registraDuenoYVinculaMascota() throws Exception {
        when(duenos.save(any())).thenAnswer(i -> i.getArgument(0));
        mvc.perform(post("/api/clinica/duenos").session(session("VETERINARIO")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Ana\",\"telefono\":\"2221234567\",\"correo\":\"ana@example.com\",\"direccion\":\"Centro\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("Ana")).andExpect(jsonPath("$.direccion").value("Centro"));
        Dueno owner = new Dueno(); owner.setNombre("Ana"); when(duenos.findById(7)).thenReturn(Optional.of(owner));
        when(mascotas.save(any())).thenAnswer(i -> { Mascota m=i.getArgument(0); assertSame(owner,m.getDueno()); return m; });
        for (String especie : List.of("Perro","Gato","Pez","Pájaro")) {
            mvc.perform(post("/api/clinica/duenos/7/mascotas").session(session("VETERINARIO")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"Paciente\",\"especie\":\""+especie+"\",\"sexo\":\"H\",\"pesoKg\":2.5}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.especie").value(especie));
        }
    }

    @Test void consultaUsaVeterinarioAutenticadoYSeLeeDesdeSuPaciente() throws Exception {
        Dueno owner=new Dueno(); Mascota patient=new Mascota(); patient.setDueno(owner);
        Usuario vet=new Usuario(); vet.setRol(Usuario.Rol.VETERINARIO);
        when(mascotas.findById(9)).thenReturn(Optional.of(patient)); when(usuarios.findById(42)).thenReturn(Optional.of(vet));
        when(consultas.save(any())).thenAnswer(i -> {
            Consulta c=i.getArgument(0); assertSame(patient,c.getMascota()); assertSame(owner,c.getMascota().getDueno()); assertSame(vet,c.getVeterinario());
            when(consultas.findByMascotaIdOrderByFechaDesc(9)).thenReturn(List.of(c)); return c;
        });
        mvc.perform(post("/api/clinica/consultas").session(session("VETERINARIO")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mascotaId\":9,\"veterinarioId\":999,\"motivo\":\"Revisión\",\"diagnostico\":\"Paciente estable\",\"tratamiento\":\"Control en 7 días\",\"pesoKg\":2.5,\"temperaturaC\":38.2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.diagnostico").value("Paciente estable"));
        verify(usuarios,never()).findById(999);
        mvc.perform(get("/api/clinica/mascotas/9/consultas").session(session("VETERINARIO")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].tratamiento").value("Control en 7 días"));
        mvc.perform(get("/api/clinica/mascotas/10/consultas").session(session("VETERINARIO")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void veterinarioPuedeLeerInventarioPeroNoModificarlo() throws Exception {
        Producto p=new Producto(); p.setNombre("Vacuna"); p.setStockActual(new java.math.BigDecimal("8"));
        when(productos.findTop20ByNombreContainingIgnoreCaseAndActivoTrueOrderByNombre("Vacuna")).thenReturn(List.of(p));
        mvc.perform(get("/api/productos?q=Vacuna").session(session("VETERINARIO"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].stockActual").value(8));
        mvc.perform(post("/api/productos").session(session("VETERINARIO")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clinica/duenos").session(session("CAJERO"))).andExpect(status().isForbidden());
    }

    @Test void cerrarSesionInvalidaSesionDeCadaRol() throws Exception {
        for (String role:List.of("ADMINISTRADOR","CAJERO","VETERINARIO")) {
            MockHttpSession s=session(role);
            mvc.perform(post("/api/auth/logout").session(s)).andExpect(status().isNoContent()); assertTrue(s.isInvalid());
        }
        mvc.perform(get("/api/productos")).andExpect(status().isForbidden());
    }

    @Test void rechazaConsultaSinMotivoYMascotaSinDuenoExistente() throws Exception {
        mvc.perform(post("/api/clinica/consultas").session(session("VETERINARIO")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mascotaId\":9,\"motivo\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/clinica/duenos/404/mascotas").session(session("VETERINARIO")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Luna\",\"especie\":\"Gato\"}")).andExpect(status().isNotFound());
    }
}
