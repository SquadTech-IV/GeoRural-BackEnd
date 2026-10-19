package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Transformacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransformacaoRepository extends JpaRepository<Transformacao, Long> {
    List<Transformacao> findByExecucao_IdOrderByOrdemAsc(Long execucaoId);
}
