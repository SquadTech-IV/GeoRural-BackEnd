package br.com.fatec.georural.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.Quarentena;

public interface QuarentenaRepository extends JpaRepository<Quarentena, Long> {
}
