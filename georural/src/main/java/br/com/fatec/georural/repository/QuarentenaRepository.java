package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Quarentena;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuarentenaRepository extends JpaRepository<Quarentena, Long> {
}
