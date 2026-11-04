package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ArquivoBruto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArquivoBrutoRepository extends JpaRepository<ArquivoBruto, Long> {


    @Query("""
           SELECT a FROM ArquivoBruto a
            WHERE a.conjunto.id = :conjuntoId
              AND a.competencia = :competencia
              AND a.hash = :hash
              AND (a.status IS NULL OR a.status <> 'FALHA')
           """)
    Optional<ArquivoBruto> buscarPorIdentidade(@Param("conjuntoId") Long conjuntoId,
                                               @Param("competencia") String competencia,
                                               @Param("hash") String hash);


    @Query("""
           SELECT a FROM ArquivoBruto a
            WHERE a.conjunto.id = :conjuntoId
              AND a.hash = :hash
              AND (a.status IS NULL OR a.status <> 'FALHA')
            ORDER BY a.dataUpload DESC
           """)
    List<ArquivoBruto> buscarMesmoConteudoNoConjunto(@Param("conjuntoId") Long conjuntoId,
                                                     @Param("hash") String hash);

    @Query("""
           SELECT a FROM ArquivoBruto a
            WHERE a.conjunto.id = :conId AND a.hash = :hash
              AND (a.status IS NULL OR a.status <> 'FALHA')
           """)
    Optional<ArquivoBruto> findArquivoBruto(@Param("conId") Long conId, @Param("hash") String hash);

    @Query("SELECT a FROM ArquivoBruto a WHERE a.hash = :hash ORDER BY a.dataUpload DESC")
    List<ArquivoBruto> buscarTodosPorHash(@Param("hash") String hash);

    @Deprecated
    default Optional<ArquivoBruto> findByHash(String hash) {
        List<ArquivoBruto> achados = buscarTodosPorHash(hash);
        return achados.isEmpty() ? Optional.empty() : Optional.of(achados.get(0));
    }

    /** Membros de um arquivo container */
    List<ArquivoBruto> findByPai_IdOrderByCaminhoInternoAsc(Long paiId);

    /** Apenas os arquivos de primeiro nivel de um conjunto, os que o usuario enviou. */
    List<ArquivoBruto> findByConjunto_IdAndPaiIsNullOrderByDataUploadDesc(Long conjuntoId);

    boolean existsByPai_Id(Long paiId);

    boolean existsByConjunto_IdAndNome(Long conjuntoId, String nome);

    List<ArquivoBruto> findByConjunto_IdOrderByDataUploadDesc(Long conjuntoId, Pageable pageable);

    List<ArquivoBruto> findByConjunto_IdOrderByDataUploadAsc(Long conjuntoId);
}
