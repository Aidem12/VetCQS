package mx.buap.veterinaria.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.buap.veterinaria.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, Integer> {
}
