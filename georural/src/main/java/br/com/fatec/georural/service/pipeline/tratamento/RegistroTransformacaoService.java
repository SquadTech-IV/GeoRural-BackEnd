package br.com.fatec.georural.service.pipeline.tratamento;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Transformacao;
import br.com.fatec.georural.repository.TransformacaoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// grava o que cada passo fez; e disso que a tela de linhagem se alimenta
@Service
@RequiredArgsConstructor
public class RegistroTransformacaoService {

    @PersistenceContext
    private EntityManager entityManager;

    private final TransformacaoRepository transformacaoRepository;

    public Trilha abrir() {
        return new Trilha();
    }

    @Transactional
    public int gravar(Long execucaoId, List<Passo> passos) {
        if (passos.isEmpty()) {
            return 0;
        }
        ExecucaoPipeline execucao = entityManager.getReference(ExecucaoPipeline.class, execucaoId);
        LocalDateTime agora = LocalDateTime.now();
        int ordem = 1;
        for (Passo passo : passos) {
            Transformacao linha = new Transformacao();
            linha.setExecucao(execucao);
            linha.setOrdem(ordem++);
            linha.setTipo(passo.tipo());
            linha.setDescricao(cortar(passo.descricao(), 400));
            linha.setEntrada(cortar(passo.entrada(), 200));
            linha.setSaida(cortar(passo.saida(), 200));
            linha.setData(agora);
            transformacaoRepository.save(linha);
        }
        return passos.size();
    }

    private String cortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max - 3) + "...";
    }

    // acumula os passos durante a etapa
    public static class Trilha {

        private final List<Passo> passos = new ArrayList<>();

        public Trilha passo(String tipo, String descricao, String entrada, String saida) {
            passos.add(new Passo(tipo, descricao, entrada, saida));
            return this;
        }

        public List<Passo> passos() {
            return List.copyOf(passos);
        }
    }

    public record Passo(String tipo, String descricao, String entrada, String saida) {
    }
}
