package br.com.fatec.georural.service.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CargaLimiteTerritorialRunner implements ApplicationRunner {

    private final CargaLimiteTerritorialService servico;

    @Override
    public void run(ApplicationArguments args) {
        try {
            servico.carregarSeNecessario();
        } catch (RuntimeException e) {
            log.warn("Limite do Parana nao pode ser derivado agora: {}", e.getMessage());
        }
    }
}
