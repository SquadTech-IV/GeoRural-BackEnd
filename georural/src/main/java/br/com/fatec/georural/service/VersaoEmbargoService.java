package br.com.fatec.georural.service;

import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.repository.EmbargoRepository;
import lombok.RequiredArgsConstructor;
import br.com.fatec.georural.entity.Embargo;

import org.springframework.stereotype.Service;

@Service 
@RequiredArgsConstructor
public class VersaoEmbargoService {

    private final EmbargoRepository embargoRepository;

    // Ponto único da regra. Hoje: versão de embargo criada mais recentemente.
    // Para migrar para a vigente, altere apenas este método.
    public Long resolverId() {
        Embargo e = embargoRepository.findFirstByOrderByVersaoDesc()
            .orElseThrow(() -> new EstadoInconsistenteException("Nenhuma versão de embargo cadastrada"));
        return e.getVersao().getNumero();
    }
}
