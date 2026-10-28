package mx.buap.veterinaria.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.buap.veterinaria.model.Pago;

public interface PagoRepository extends JpaRepository<Pago, Integer> {
}
