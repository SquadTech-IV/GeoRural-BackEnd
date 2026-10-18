package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ConjuntoDados;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConjuntoDadosRepository
        extends JpaRepository<ConjuntoDados, Long> {

    Optional<ConjuntoDados> findByNome(String nome);

    Optional<ConjuntoDados> findByFonteIdAndTipoConjuntoId(Long fonteId, Long tipoId);

    List<ConjuntoDados> findByFonteIdOrderByNomeAsc(Long fonteId);

    Optional<ConjuntoDados> findByFonteIdAndNomeIgnoreCase(
        Long fonteId,
        String nome
    );

    /** Conjuntos que aceitam entrada por API (API ou AMBOS), de fontes ativas */
    @Query("""
            SELECT c FROM ConjuntoDados c
            JOIN FETCH c.fonte f
            JOIN FETCH c.tipo t
            WHERE c.formaEntrada IN ('API', 'AMBOS')
              AND c.situacao <> 'ARQUIVADO'
              AND f.status = 'S'
            ORDER BY f.nome, c.nome
            """)
    List<ConjuntoDados> buscarConjuntosApi();
}