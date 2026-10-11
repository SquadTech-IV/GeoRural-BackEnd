package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.indicador.IndicadorRequest;
import br.com.fatec.georural.dto.response.indicador.IndicadorResponse;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.IndicadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IndicadorService {

    private final IndicadorRepository indicadorRepository;

    public IndicadorService(IndicadorRepository indicadorRepository) {
        this.indicadorRepository = indicadorRepository;
    }

    @Transactional(readOnly = true)
    public List<IndicadorResponse> listar() {
        return indicadorRepository.findAll().stream()
                .map(IndicadorResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public IndicadorResponse buscarPorSigla(String sigla) {
        return IndicadorResponse.de(buscarEntidade(sigla));
    }

    @Transactional
    public IndicadorResponse cadastrar(IndicadorRequest request) {
        String sigla = normalizarSigla(request.sigla());

        indicadorRepository.findBySigla(sigla).ifPresent(existente -> {
            throw new ConflitoException("Ja existe um indicador com a sigla " + sigla + ".");
        });

        Indicador indicador = new Indicador();
        indicador.setSigla(sigla);
        indicador.setNome(request.nome());
        indicador.setDescricao(request.descricao());
        indicador.setUnidade(request.unidade());

        return IndicadorResponse.de(indicadorRepository.save(indicador));
    }

    public Indicador buscarEntidade(String sigla) {
        return indicadorRepository.findBySigla(normalizarSigla(sigla))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Indicador nao encontrado para a sigla " + sigla + "."));
    }

    private String normalizarSigla(String sigla) {
        return sigla == null ? null : sigla.trim().toUpperCase();
    }
}