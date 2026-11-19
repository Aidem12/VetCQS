package mx.buap.veterinaria;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import mx.buap.veterinaria.comun.*;
import mx.buap.veterinaria.expediente.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

/** Validaciones del expediente: fechas, anulación sin borrado, recetas y adjuntos seguros. */
class ExpedienteServiceTest {
    final MascotaRepository mascotas = mock(MascotaRepository.class);
    final VacunaRepository vacunas = mock(VacunaRepository.class);
    final RecetaRepository recetas = mock(RecetaRepository.class);
    final DocumentoClinicoRepository documentos = mock(DocumentoClinicoRepository.class);
    final Reloj reloj = new Reloj();
    final ExpedienteService servicio = new ExpedienteService(mascotas, mock(ConsultaRepository.class),
            mock(ProductoRepository.class), vacunas, mock(TratamientoPreventivoRepository.class),
            mock(AntecedenteClinicoRepository.class), mock(NotaClinicaRepository.class), recetas,
            mock(EstudioRepository.class), documentos, mock(AuditoriaService.class), mock(EntityManager.class), reloj);
    static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    Usuario vet;

    @BeforeEach void preparar() {
        reloj.fijar(Clock.fixed(HOY.atTime(12, 0).atZone(Reloj.ZONA).toInstant(), Reloj.ZONA));
        vet = mock(Usuario.class); when(vet.getId()).thenReturn(7);
        Mascota max = mock(Mascota.class); when(max.getId()).thenReturn(3);
        when(mascotas.findById(3)).thenReturn(Optional.of(max));
        when(vacunas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(recetas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentos.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test void vacunaValidaFechas() {
        assertThrows(ResponseStatusException.class, () -> servicio.registrarVacuna(3,
                new ExpedienteService.VacunaDatos("Rabia", null, "1a", HOY.plusDays(1), null, null), vet));
        assertThrows(ResponseStatusException.class, () -> servicio.registrarVacuna(3,
                new ExpedienteService.VacunaDatos("Rabia", null, "1a", HOY, HOY.minusDays(3), null), vet));
        Vacuna v = servicio.registrarVacuna(3, new ExpedienteService.VacunaDatos(" Rabia ", "L9", "1a", HOY, HOY.plusYears(1), null), vet);
        assertEquals("Rabia", v.getVacuna());
        assertSame(vet, v.getVeterinario());
    }

    @Test void anularExigeMotivoYNoBorra() {
        Vacuna v = new Vacuna();
        when(vacunas.findById(4)).thenReturn(Optional.of(v));
        assertThrows(ResponseStatusException.class, () -> servicio.anular("vacunas", 4, " ", vet));
        servicio.anular("vacunas", 4, "Capturada en la mascota equivocada", vet);
        assertTrue(v.isAnulada());
        assertThrows(ResponseStatusException.class, () -> servicio.anular("vacunas", 4, "otra vez", vet));
        verify(vacunas, never()).delete(any());
        verify(vacunas, never()).deleteById(any());
    }

    @Test void recetaRequiereMedicamentosCompletosYAsignaFolio() {
        assertThrows(ResponseStatusException.class, () -> servicio.crearReceta(3, null, null, List.of(), vet));
        assertThrows(ResponseStatusException.class, () -> servicio.crearReceta(3, null, null, List.of(
                new ExpedienteService.RecetaRenglon(null, "Amoxicilina", "250 mg", "", "Oral", "7 días", null)), vet));
        when(recetas.siguienteFolio()).thenReturn(42L);
        Receta r = servicio.crearReceta(3, null, "Reposo", List.of(
                new ExpedienteService.RecetaRenglon(null, "Amoxicilina", "250 mg", "Cada 12 h", "Oral", "7 días", "Con alimento")), vet);
        assertEquals("R-000042", r.getFolio());
        assertEquals(1, r.getDetalles().size());
    }

    @Test void adjuntosSoloPdfPngJpegDeHasta5Mb() {
        byte[] pdf = "%PDF-1.7 contenido".getBytes();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
        byte[] exe = {'M', 'Z', 0, 0, 1, 2};
        DocumentoClinico d = servicio.adjuntar(3, null, "../../resultado:biometria.pdf", pdf, vet);
        assertEquals("application/pdf", d.getTipoMime());
        assertFalse(d.getNombreOriginal().contains("/"));
        assertEquals(64, d.getSha256().length());
        assertEquals("image/png", servicio.adjuntar(3, null, "rx.png", png, vet).getTipoMime());
        assertThrows(ResponseStatusException.class, () -> servicio.adjuntar(3, null, "virus.pdf", exe, vet));
        assertThrows(ResponseStatusException.class, () -> servicio.adjuntar(3, null, "vacio.pdf", new byte[0], vet));
        byte[] grande = new byte[5 * 1024 * 1024 + 1]; grande[0] = '%'; grande[1] = 'P'; grande[2] = 'D'; grande[3] = 'F'; grande[4] = '-';
        assertThrows(ResponseStatusException.class, () -> servicio.adjuntar(3, null, "grande.pdf", grande, vet));
    }
}
