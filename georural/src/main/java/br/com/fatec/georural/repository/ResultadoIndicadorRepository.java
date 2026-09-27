package br.com.fatec.georural.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.ResultadoIndicador;

public interface ResultadoIndicadorRepository extends JpaRepository<ResultadoIndicador, Long> {
    Optional<ResultadoIndicador> findFirstByImovel_IdAndIndicador_SiglaOrderByDataCalculoDesc(
            Long imovelId, String sigla);
}
