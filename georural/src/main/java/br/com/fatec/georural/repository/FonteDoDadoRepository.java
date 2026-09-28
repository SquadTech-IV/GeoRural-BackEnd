package br.com.fatec.georural.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.FonteDoDado;

public interface FonteDoDadoRepository extends JpaRepository<FonteDoDado, Long> {
    Optional<FonteDoDado> findByNome(String nome);
}