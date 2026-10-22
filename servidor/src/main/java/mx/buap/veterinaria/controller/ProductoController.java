package mx.buap.veterinaria.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mx.buap.veterinaria.model.Producto;
import mx.buap.veterinaria.repository.ProductoRepository;

/** Endpoints de catálogo que usa la caja desde la iteración 1 (RF-01, RF-02). */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoRepository productos;

    public ProductoController(ProductoRepository productos) {
        this.productos = productos;
    }

    /** Lectura por código de barras: el lector escribe el código y la caja llama aquí. */
    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<Producto> porCodigo(@PathVariable String codigo) {
        return productos.findByCodigoBarrasAndActivoTrue(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Búsqueda por nombre (F1 en caja). */
    @GetMapping
    public List<Producto> buscar(@RequestParam(defaultValue = "") String q) {
        return productos.findTop20ByNombreContainingIgnoreCaseAndActivoTrueOrderByNombre(q);
    }
}
