package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Embargo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbargoRepository extends JpaRepository<Embargo, Long> {
}
