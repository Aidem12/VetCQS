package mx.buap.veterinaria;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.agenda.*;
import mx.buap.veterinaria.comun.Reloj;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

/** Reglas de la agenda: horario, ausencias, traslapes, fechas y estados. */
class AgendaServiceTest {
    final CitaRepository citas = mock(CitaRepository.class);
    final CitaHistorialRepository historial = mock(CitaHistorialRepository.class);
    final TipoCitaRepository tipos = mock(TipoCitaRepository.class);
    final HorarioVeterinarioRepository horarios = mock(HorarioVeterinarioRepository.class);
    final AusenciaVeterinarioRepository ausencias = mock(AusenciaVeterinarioRepository.class);
    final MascotaRepository mascotas = mock(MascotaRepository.class);
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final ConsultaRepository consultas = mock(ConsultaRepository.class);
    final Reloj reloj = new Reloj();
    final AgendaService agenda = new AgendaService(citas, historial, tipos, horarios, ausencias, mascotas, usuarios, consultas, reloj);

    // Lunes 2026-11-23 a las 08:00 en la Ciudad de México.
    static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    Usuario vet;
    Mascota max;
    TipoCita consulta30;

    @BeforeEach void preparar() {
        reloj.fijar(Clock.fixed(LUNES.atTime(8, 0).atZone(Reloj.ZONA).toInstant(), Reloj.ZONA));
        vet = usuario(7, Usuario.Rol.VETERINARIO);
        max = mock(Mascota.class); when(max.getId()).thenReturn(3);
        when(mascotas.findById(3)).thenReturn(Optional.of(max));
        consulta30 = new TipoCita(); consulta30.setNombre("Consulta"); consulta30.setDuracionMin(30);
        when(tipos.findById(1)).thenReturn(Optional.of(consulta30));
        HorarioVeterinario manana = horario(9, 14), tarde = horario(16, 19);
        when(horarios.findByVeterinarioIdAndDiaSemanaOrderByHoraInicio(7, (short) 1)).thenReturn(List.of(manana, tarde));
        when(citas.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    Usuario usuario(int id, Usuario.Rol rol) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(id); when(u.getRol()).thenReturn(rol); when(u.isActivo()).thenReturn(true);
        when(u.getNombre()).thenReturn("Usuario " + id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return u;
    }
    HorarioVeterinario horario(int desde, int hasta) {
        HorarioVeterinario h = new HorarioVeterinario();
        h.setDiaSemana((short) 1); h.setHoraInicio(LocalTime.of(desde, 0)); h.setHoraFin(LocalTime.of(hasta, 0));
        return h;
    }
    AgendaService.NuevaCita nueva(LocalDateTime inicio) {
        return new AgendaService.NuevaCita(3, 7, 1, inicio, "Revisión", null);
    }

    @Test void creaDentroDelHorarioYCalculaElFin() {
        Cita c = agenda.crear(nueva(LUNES.atTime(10, 0)), vet);
        assertEquals(LUNES.atTime(10, 30), c.getFin());
        assertEquals(Cita.Estado.PENDIENTE, c.getEstado());
        verify(historial).save(any(CitaHistorial.class));
    }

    @Test void rechazaFueraDeHorarioYEnElDescanso() {
        assertThrows(ResponseStatusException.class, () -> agenda.crear(nueva(LUNES.atTime(13, 45)), vet));
        assertThrows(ResponseStatusException.class, () -> agenda.crear(nueva(LUNES.atTime(14, 30)), vet));
        assertThrows(ResponseStatusException.class, () -> agenda.crear(nueva(LUNES.plusDays(1).atTime(10, 0)), vet));
    }

    @Test void rechazaPasadoAusenciaYTraslape() {
        assertThrows(ResponseStatusException.class, () -> agenda.crear(nueva(LUNES.atTime(7, 30)), vet));
        when(ausencias.traslapadas(eq(7), any(), any())).thenReturn(List.of(new AusenciaVeterinario()));
        assertThrows(ResponseStatusException.class, () -> agenda.crear(nueva(LUNES.atTime(11, 0)), vet));
        when(ausencias.traslapadas(eq(7), any(), any())).thenReturn(List.of());
        when(citas.traslapes(eq(7), any(), any(), any())).thenReturn(1L);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> agenda.crear(nueva(LUNES.atTime(11, 0)), vet));
        assertTrue(e.getReason().contains("ocupado"));
        verify(citas, never()).saveAndFlush(any());
    }

    @Test void soloAceptaVeterinariosActivos() {
        usuario(9, Usuario.Rol.CAJERO);
        assertThrows(ResponseStatusException.class,
                () -> agenda.crear(new AgendaService.NuevaCita(3, 9, 1, LUNES.atTime(10, 0), null, null), vet));
    }

    @Test void respetaLasTransicionesDeEstado() {
        Cita c = agenda.crear(nueva(LUNES.atTime(10, 0)), vet);
        when(citas.findByIdForUpdate(5)).thenReturn(Optional.of(c));
        assertThrows(ResponseStatusException.class, () -> agenda.cambiarEstado(5, Cita.Estado.CANCELADA, " ", vet));
        assertThrows(ResponseStatusException.class, () -> agenda.cambiarEstado(5, Cita.Estado.ATENDIDA, null, vet));
        agenda.cambiarEstado(5, Cita.Estado.CONFIRMADA, null, vet);
        agenda.cambiarEstado(5, Cita.Estado.CANCELADA, "El dueño no puede asistir", vet);
        assertEquals("El dueño no puede asistir", c.getMotivoCancelacion());
        assertThrows(ResponseStatusException.class, () -> agenda.cambiarEstado(5, Cita.Estado.CONFIRMADA, null, vet));
        assertThrows(ResponseStatusException.class, () -> agenda.reprogramar(5, LUNES.atTime(17, 0), null, vet));
    }

    @Test void reprogramaYVuelveAPendiente() {
        Cita c = agenda.crear(nueva(LUNES.atTime(10, 0)), vet);
        c.setEstado(Cita.Estado.CONFIRMADA);
        when(citas.findByIdForUpdate(5)).thenReturn(Optional.of(c));
        agenda.reprogramar(5, LUNES.atTime(17, 0), null, vet);
        assertEquals(LUNES.atTime(17, 0), c.getInicio());
        assertEquals(Cita.Estado.PENDIENTE, c.getEstado());
    }

    @Test void atenderExigeConsultaDeLaMismaMascota() {
        Cita c = agenda.crear(nueva(LUNES.atTime(10, 0)), vet);
        when(citas.findByIdForUpdate(5)).thenReturn(Optional.of(c));
        Mascota otra = mock(Mascota.class); when(otra.getId()).thenReturn(99);
        Consulta ajena = mock(Consulta.class); when(ajena.getMascota()).thenReturn(otra);
        when(consultas.findById(50)).thenReturn(Optional.of(ajena));
        assertThrows(ResponseStatusException.class, () -> agenda.atender(5, 50, vet));
        Consulta propia = mock(Consulta.class); when(propia.getMascota()).thenReturn(max);
        when(consultas.findById(51)).thenReturn(Optional.of(propia));
        agenda.atender(5, 51, vet);
        assertEquals(Cita.Estado.ATENDIDA, c.getEstado());
        assertSame(propia, c.getConsulta());
    }

    @Test void disponibilidadOmiteOcupadosYDescanso() {
        Cita ocupada = new Cita(); ocupada.setInicio(LUNES.atTime(9, 0)); ocupada.setFin(LUNES.atTime(9, 30));
        when(citas.ocupadas(eq(7), any(), any())).thenReturn(List.of(ocupada));
        List<AgendaService.Espacio> libres = agenda.disponibilidad(7, LUNES, 1);
        assertFalse(libres.stream().anyMatch(e -> e.inicio().equals(LUNES.atTime(9, 0))));
        assertTrue(libres.stream().anyMatch(e -> e.inicio().equals(LUNES.atTime(9, 30))));
        assertFalse(libres.stream().anyMatch(e -> e.inicio().toLocalTime().equals(LocalTime.of(14, 0))));
        assertTrue(libres.stream().allMatch(e -> !e.fin().toLocalTime().isAfter(LocalTime.of(19, 0))));
    }

    @Test void limitaElRangoDeBusqueda() {
        assertThrows(ResponseStatusException.class, () -> agenda.buscar(LUNES, LUNES.minusDays(1), null, null, null, null, ""));
        assertThrows(ResponseStatusException.class, () -> agenda.buscar(LUNES, LUNES.plusDays(90), null, null, null, null, ""));
    }
}
