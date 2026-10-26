package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ArquivoBruto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;

public interface ArquivoBrutoRepository extends JpaRepository<ArquivoBruto, Long> {
    @Query("select a from ArquivoBruto a where a.conjunto.id = :conId and a.hash = :hash "
     + "and (a.status is null or a.status <> 'FALHA')")

    Optional<ArquivoBruto> findArquivoBruto(@Param("conId") Long conId, @Param("hash") String hash);

    Optional<ArquivoBruto> findByHash(String hash);

    /** ja existe um arquivo com esse nome nesse conjunto? */
    boolean existsByConjunto_IdAndNome(Long conjuntoId, String nome);

    /** ingestoes recentes de um conjunto  */
    List<ArquivoBruto> findByConjunto_IdOrderByDataUploadDesc(Long conjuntoId, Pageable pageable);

    List<ArquivoBruto> findByConjunto_IdOrderByDataUploadAsc(Long conjuntoId);
}