package br.com.fatec.georural.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.Municipio;

public interface MunicipioRepository extends JpaRepository<Municipio, Long> {
    Optional<Municipio> findByNome(String nome);
}