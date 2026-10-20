package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Validacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidacaoRepository extends JpaRepository<Validacao, Long> {

    /** Todas as validacoes vinculadas a uma execucao do pipeline. */
    List<Validacao> findByExecucao_Id(Long execucaoId);
}