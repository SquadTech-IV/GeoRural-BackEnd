package br.com.fatec.georural.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.Versao;

public interface VersaoRepository extends JpaRepository<Versao, Long> {

    Optional<Versao> findTopByConjuntoOrderByNumeroDesc(ConjuntoDados conjunto);

    // versoes de um conjunto em uma dada situacao
    List<Versao> findByConjunto_IdAndSituacao(Long conjuntoId, String situacao);

    // versao com o mesmo conteudo; e o que evita criar versao sem necessidade
    Optional<Versao> findFirstByConjunto_IdAndHashOrderByNumeroDesc(Long conjuntoId, String hash);

    // maior numero de versao do conjunto
    @Query("SELECT MAX(v.numero) FROM Versao v WHERE v.conjunto.id = :conjuntoId")
    Long maiorNumero(@Param("conjuntoId") Long conjuntoId);

    // versoes publicadas geradas a partir de um arquivo bruto, mais recente primeiro
    @Query("""
           SELECT v FROM Versao v
           WHERE v.execucao.arquivoBruto.id = :arquivoId
             AND v.dataPublicacao IS NOT NULL
           ORDER BY v.dataPublicacao DESC
           """)
    List<Versao> findPublicadasPorArquivo(@Param("arquivoId") Long arquivoId);

    // fila de versoes com conjunto/usuario/execucao carregados
    @Query("""
           SELECT DISTINCT v FROM Versao v
           JOIN FETCH v.conjunto
           LEFT JOIN FETCH v.usuario
           LEFT JOIN FETCH v.execucao
           ORDER BY v.dataCriacao DESC
           """)
    List<Versao> listarResumo();

    // filtro por situacao
    @Query("""
           SELECT DISTINCT v FROM Versao v
           JOIN FETCH v.conjunto
           LEFT JOIN FETCH v.usuario
           LEFT JOIN FETCH v.execucao
           WHERE v.situacao = :situacao
           ORDER BY v.dataCriacao DESC
           """)
    List<Versao> listarResumoPorSituacao(@Param("situacao") String situacao);
}
