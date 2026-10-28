package mx.buap.veterinaria.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import mx.buap.veterinaria.model.Producto;
import mx.buap.veterinaria.dto.ProductoRequest;
import mx.buap.veterinaria.repository.ProductoRepository;

/** Endpoints de catálogo que usa la caja desde la iteración 1 (RF-01, RF-02). */
@RestController
@RequestMapping("/api/productos")
@Validated
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

    @PostMapping
    public Producto crear(@Valid @RequestBody ProductoRequest request) {
        Producto producto = new Producto();
        copiar(request, producto);
        return productos.save(producto);
    }

    @PutMapping("/{id}")
    public Producto actualizar(@PathVariable Integer id, @Valid @RequestBody ProductoRequest request) {
        Producto producto = productos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        copiar(request, producto);
        return productos.save(producto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Integer id) {
        Producto producto = productos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        producto.setActivo(false);
        productos.save(producto);
    }

    private static void copiar(ProductoRequest request, Producto producto) {
        producto.setCodigoBarras(request.codigoBarras());
        producto.setNombre(request.nombre());
        producto.setTipo(request.tipo());
        producto.setPrecio(request.precio());
        producto.setStockActual(request.stockActual());
        producto.setStockMinimo(request.stockMinimo());
        producto.setManejaCaducidad(request.manejaCaducidad());
    }
}
