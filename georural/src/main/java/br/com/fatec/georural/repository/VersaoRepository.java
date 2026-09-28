package br.com.fatec.georural.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.Versao;

public interface VersaoRepository extends JpaRepository<Versao, Long> {
    Optional<Versao> findTopByConjuntoOrderByNumeroDesc(ConjuntoDados conjunto);

    @Query(value = """
        SELECT v.ver_id
        FROM VERSAO v
        JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id
        WHERE c.con_tipo_camada = 'EMBARGO'
        ORDER BY v.ver_data_criacao DESC
        FETCH FIRST 1 ROWS ONLY
        """, nativeQuery = true)
    Optional<Long> encontrarVersaoEmbargoMaisRecente();
}