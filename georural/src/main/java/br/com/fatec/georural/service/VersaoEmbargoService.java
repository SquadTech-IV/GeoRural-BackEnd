package br.com.fatec.georural.service;

import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.repository.VersaoRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service 
@RequiredArgsConstructor
public class VersaoEmbargoService {

    private final VersaoRepository versaoRepository;

    // Ponto único da regra. Hoje: versão de embargo criada mais recentemente.
    // Para migrar para a vigente, altere apenas este método.
    public Long resolverId() {
        return versaoRepository.encontrarVersaoEmbargoMaisRecente()
            .orElseThrow(() -> new EstadoInconsistenteException("Nenhuma versão de embargo cadastrada"));
    }
}
