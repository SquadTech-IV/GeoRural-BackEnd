package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.TipoConjunto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.dto.request.cadastro.ConjuntoRequest;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.TipoConjuntoRepository;
import org.springframework.stereotype.Service;

@Service
public class CatalogoConjuntoService {
    private final TipoConjuntoRepository repository;

    public CatalogoConjuntoService(TipoConjuntoRepository repository) {
        this.repository = repository;
    }

    public TipoConjunto porNome(String nome) {
        String valor = CadastroCampos.obrigatorio(nome, "tipo fornecido", 120);
        return repository.findByNomeIgnoreCase(valor).orElseThrow(() ->
            new RequisicaoInvalidaException("Tipo de conjunto não encontrado no catálogo: " + valor));
    }

    public TipoConjunto resolver(ConjuntoRequest request, ConjuntoDados atual) {
        if (request == null) throw new RequisicaoInvalidaException("Informe os dados do conjunto.");
        String codigo = CadastroCampos.opcional(request.tipoCamada(), "tipo de camada", 30);
        TipoConjunto tipo;
        if (codigo != null) {
            if ("VEGETACAO_NATIVA".equalsIgnoreCase(codigo)) codigo = "VEGETACAO";
            String valor = codigo;
            tipo = repository.findByCodigoIgnoreCase(valor).orElseThrow(() ->
                new RequisicaoInvalidaException("Tipo de camada não encontrado no catálogo: " + valor));
        } else if (atual.getId() != null) {
            tipo = atual.getTipoConjunto();
        } else {
            tipo = porNome(request.nome());
        }
        if (tipo == null) throw new RequisicaoInvalidaException("O conjunto precisa de um tipo válido.");
        if (request.nome() != null) {
            var tipoPeloNome = repository.findByNomeIgnoreCase(request.nome().trim());
            if (tipoPeloNome.isPresent() && !java.util.Objects.equals(tipoPeloNome.get().getId(), tipo.getId())) {
                throw new RequisicaoInvalidaException("O nome selecionado corresponde a outro tipo do catálogo.");
            }
        }
        return tipo;
    }
}
