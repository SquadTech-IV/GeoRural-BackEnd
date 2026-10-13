package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ParametroRegraCalculo;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.ParametroRegraCalculoRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ParametrosRegraService {

    private final IndicadorRepository indicadorRepository;
    private final RegraCalculoRepository regraCalculoRepository;
    private final ParametroRegraCalculoRepository parametroRegraCalculoRepository;

    @Transactional(readOnly = true)
    public ParametrosVigentes daRegraVigente(String sigla) {
        Indicador indicador = indicadorRepository.findBySigla(sigla)
                .orElseThrow(() -> new EstadoInconsistenteException("Indicador nao cadastrado: " + sigla));

        RegraCalculo vigente = regraCalculoRepository.findByIndicadorAndVigente(indicador, "S")
                .orElseThrow(() -> new EstadoInconsistenteException("Nenhuma regra vigente para " + sigla));

        Map<String, String> valores = new HashMap<>();
        for (ParametroRegraCalculo p : parametroRegraCalculoRepository.findByRegraCalculo(vigente)) {
            valores.put(p.getNome(), p.getValor());
        }
        return new ParametrosVigentes(sigla, vigente.getVersao(), valores);
    }
}