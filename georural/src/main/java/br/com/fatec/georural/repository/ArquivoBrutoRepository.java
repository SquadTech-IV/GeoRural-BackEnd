package br.com.fatec.georural.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fatec.georural.entity.ArquivoBruto;

public interface ArquivoBrutoRepository extends JpaRepository<ArquivoBruto, Long> {
    @Query("select a from ArquivoBruto a where a.conjunto.id = :conId and a.hash = :hash "
     + "and (a.status is null or a.status <> 'FALHA')")
    Optional<ArquivoBruto> findArquivoBruto(@Param("conId") Long conId, @Param("hash") String hash);
}