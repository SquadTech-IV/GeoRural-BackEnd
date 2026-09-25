package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ArquivoDatalake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ArquivoDatalakeRepository extends JpaRepository<ArquivoDatalake, Long> {


    @Query("""
        SELECT a FROM ArquivoDatalake a
        WHERE (:nome IS NULL OR LOWER(a.nome) LIKE LOWER(CONCAT('%', :nome, '%')))
          AND (:situacao IS NULL OR a.situacao = :situacao)
        ORDER BY a.recebidoEm DESC
        """)
    List<ArquivoDatalake> buscar(@Param("nome") String nome,
                                 @Param("situacao") String situacao);
}