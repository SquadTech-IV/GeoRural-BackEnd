package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.MovimentacaoZona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentacaoZonaRepository extends JpaRepository<MovimentacaoZona, Long> {

    // historico de zonas de um arquivo bruto (BRUTA -> QUARENTENA -> TRATADA -> PUBLICADA)
    List<MovimentacaoZona> findByArquivoBrutoIdOrderByDataAsc(Long arquivoBrutoId);
}
