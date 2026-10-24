<<<<<<< HEAD
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.MovimentacaoZona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentacaoZonaRepository extends JpaRepository<MovimentacaoZona, Long> {

    // historico de zonas de um arquivo bruto (BRUTA -> QUARENTENA -> TRATADA -> PUBLICADA)
    List<MovimentacaoZona> findByArquivoBrutoIdOrderByDataAsc(Long arquivoBrutoId);

    // historico de zonas de TODOS os arquivos de um conjunto de dados
    List<MovimentacaoZona> findByArquivoBruto_Conjunto_IdOrderByDataAsc(Long conjuntoId);

    List<MovimentacaoZona> findByExecucao_Id(Long execucaoId);
}
=======
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.MovimentacaoZona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentacaoZonaRepository extends JpaRepository<MovimentacaoZona, Long> {

    // historico de zonas de um arquivo bruto (BRUTA -> QUARENTENA -> TRATADA -> PUBLICADA)
    List<MovimentacaoZona> findByArquivoBrutoIdOrderByDataAsc(Long arquivoBrutoId);

    // historico de zonas de TODOS os arquivos de um conjunto de dados
    List<MovimentacaoZona> findByArquivoBruto_Conjunto_IdOrderByDataAsc(Long conjuntoId);
}
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
