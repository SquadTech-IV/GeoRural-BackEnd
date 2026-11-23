package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ReservaLegal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservaLegalRepository extends JpaRepository<ReservaLegal, Long> {

    /** Ids (distintos) dos imóveis aos quais as reservas legais deste arquivo bruto se ligaram. */
    @Query("select distinct r.imovel.id from ReservaLegal r where r.arquivoBruto.id = :arquivoBrutoId")
    List<Long> findImovelIdsByArquivoBrutoId(@Param("arquivoBrutoId") Long arquivoBrutoId);
}
