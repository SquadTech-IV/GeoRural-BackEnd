package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.mapper.ArquivoDatalakeMapper;
import br.com.fatec.georural.repository.ArquivoDatalakeRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ArquivoDatalakeService {

    private final ArquivoDatalakeRepository repository;
    private final ArquivoDatalakeMapper mapper;

    public ArquivoDatalakeService(ArquivoDatalakeRepository repository,
                                  ArquivoDatalakeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<ArquivoResumoResponse> listar(String nome, String situacao) {
        String filtroNome = (nome != null && !nome.isBlank()) ? nome.trim() : null;
        String filtroSituacao = (situacao != null && !situacao.isBlank())
                ? situacao.trim().toUpperCase() : null;

        return repository.buscar(filtroNome, filtroSituacao)
                .stream()
                .map(mapper::toResumo)
                .toList();
    }
}