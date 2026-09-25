package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Municipio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipioRepository extends JpaRepository<Municipio, Long> {
}
