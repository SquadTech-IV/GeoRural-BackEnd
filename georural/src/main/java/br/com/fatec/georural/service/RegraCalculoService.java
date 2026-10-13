package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.regra.CriarVersaoRegraRequest;
import br.com.fatec.georural.dto.request.regra.ParametroInput;
import br.com.fatec.georural.dto.response.regra.RegraCalculoResponse;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ParametroRegraCalculo;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ParametroRegraCalculoRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Service
public class RegraCalculoService {

    private final RegraCalculoRepository regraCalculoRepository;
    private final ParametroRegraCalculoRepository parametroRegraCalculoRepository;
    private final IndicadorService indicadorService;
    private final ParametroRegraValidador parametroRegraValidador;

    public RegraCalculoService(RegraCalculoRepository regraCalculoRepository,
                               ParametroRegraCalculoRepository parametroRegraCalculoRepository,
                               IndicadorService indicadorService,
                               ParametroRegraValidador parametroRegraValidador) {
        this.regraCalculoRepository = regraCalculoRepository;
        this.parametroRegraCalculoRepository = parametroRegraCalculoRepository;
        this.indicadorService = indicadorService;
        this.parametroRegraValidador = parametroRegraValidador;
    }

    @Transactional
    public RegraCalculoResponse criarNovaVersao(String sigla, CriarVersaoRegraRequest request) {

        if (isBlank(request.motivo())) {
            throw new RequisicaoInvalidaException("O motivo e obrigatorio para criar uma nova versao.");
        }
        if (isBlank(request.autor())) {
            throw new RequisicaoInvalidaException("O autor e obrigatorio para criar uma nova versao.");
        }


        parametroRegraValidador.validar(request.parametros());

        Indicador indicador = indicadorService.buscarEntidade(sigla); // 404 se a sigla nao existir

        RegraCalculo atual = regraCalculoRepository
                .findByIndicadorAndVigente(indicador, "S")
                .orElse(null);

        if (atual != null && semAlteracao(request, atual)) {
            throw new RequisicaoInvalidaException(
                    "Nenhuma alteracao em relacao a versao vigente; a nova versao so e criada se algum valor mudar.");
        }

        if (atual != null) {
            atual.setVigente("N");
            regraCalculoRepository.saveAndFlush(atual);
        }

        long vigentes = regraCalculoRepository.countByIndicadorAndVigente(indicador, "S");
        if (vigentes > 0) {
            throw new EstadoInconsistenteException(
                    "Ja existe regra vigente para o indicador " + sigla + "; estado inconsistente.");
        }

        long qtdVersoes = regraCalculoRepository.countByIndicador(indicador);
        String novoNumero = "v" + (qtdVersoes + 1);

        RegraCalculo nova = new RegraCalculo();
        nova.setIndicador(indicador);
        nova.setVersao(novoNumero);
        nova.setFormula(resolverFormula(request, atual));
        nova.setDescricao(request.descricao() != null ? request.descricao()
                : (atual != null ? atual.getDescricao() : null));
        nova.setAutor(request.autor().trim());
        nova.setMotivo(request.motivo().trim());
        nova.setVigente("S");
        nova.setDataCriacao(LocalDateTime.now());
        RegraCalculo salva = regraCalculoRepository.save(nova);

        List<ParametroRegraCalculo> parametros = new ArrayList<>();
        if (request.parametros() != null) {
            for (ParametroInput entrada : request.parametros()) {
                ParametroRegraCalculo parametro = new ParametroRegraCalculo();
                parametro.setRegraCalculo(salva);
                parametro.setNome(entrada.nome().trim());
                parametro.setTipo(entrada.tipo().trim().toUpperCase());
                parametro.setUnidade(entrada.unidade());
                parametro.setValor(entrada.valor().trim());
                parametros.add(parametro);
            }
            parametroRegraCalculoRepository.saveAll(parametros);
        }

        return RegraCalculoResponse.de(salva, parametros);
    }

    private boolean semAlteracao(CriarVersaoRegraRequest request, RegraCalculo atual) {
        String novaFormula = resolverFormula(request, atual);
        String novaDescricao = request.descricao() != null ? request.descricao() : atual.getDescricao();
        if (!Objects.equals(novaFormula, atual.getFormula())) {
            return false;
        }
        if (!Objects.equals(novaDescricao, atual.getDescricao())) {
            return false;
        }
        Map<String, String> novos = mapaDosInputs(request.parametros());
        Map<String, String> atuais = mapaDasEntidades(
                parametroRegraCalculoRepository.findByRegraCalculo(atual));
        return novos.equals(atuais);
    }

    private Map<String, String> mapaDosInputs(List<ParametroInput> inputs) {
        Map<String, String> mapa = new HashMap<>();
        if (inputs != null) {
            for (ParametroInput p : inputs) {
                mapa.put(p.nome().trim(), p.valor().trim());
            }
        }
        return mapa;
    }

    private Map<String, String> mapaDasEntidades(List<ParametroRegraCalculo> params) {
        Map<String, String> mapa = new HashMap<>();
        for (ParametroRegraCalculo p : params) {
            mapa.put(p.getNome(), p.getValor());
        }
        return mapa;
    }

    private String resolverFormula(CriarVersaoRegraRequest request, RegraCalculo atual) {
        if (request.formula() != null) {
            if (request.formula().isBlank()) {
                throw new RequisicaoInvalidaException("A formula nao pode ser vazia.");
            }
            return request.formula().trim();
        }
        if (atual != null) {
            return atual.getFormula();
        }
        throw new RequisicaoInvalidaException(
                "A formula e obrigatoria quando o indicador ainda nao tem regra vigente.");
    }

    private boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}