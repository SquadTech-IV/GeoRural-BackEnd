package br.com.fatec.georural.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.ImovelRural;

public interface ImovelRuralRepository extends JpaRepository<ImovelRural, Long> {
    Optional<ImovelRural> findByCodigoCar(String codigoCar);
}
