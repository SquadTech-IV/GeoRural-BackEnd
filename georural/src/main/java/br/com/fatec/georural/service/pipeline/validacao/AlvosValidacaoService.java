package br.com.fatec.georural.service.pipeline.validacao;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// descobre o que validar e mantem o status do arquivo
@Service
@RequiredArgsConstructor
public class AlvosValidacaoService {

    private final ArquivoBrutoRepository arquivoRepository;

    @Transactional(readOnly = true)
    public String tipoConjunto(Long arquivoId) {
        ConjuntoDados conjunto = buscar(arquivoId).getConjunto();
        return conjunto != null && conjunto.getTipoConjunto() != null
                ? conjunto.getTipoConjunto().getCodigo() : null;
    }

    @Transactional(readOnly = true)
    public Long conjuntoId(Long arquivoId) {
        ConjuntoDados conjunto = buscar(arquivoId).getConjunto();
        return conjunto != null ? conjunto.getId() : null;
    }

    // membros de dentro do container; se nao houver, o proprio arquivo
    @Transactional(readOnly = true)
    public List<AlvoValidacao> alvos(Long arquivoId, List<Long> idsEscolhidos) {
        ArquivoBruto arquivo = buscar(arquivoId);
        List<ArquivoBruto> membros = arquivoRepository.findByPai_IdOrderByCaminhoInternoAsc(arquivoId);

        List<ArquivoBruto> candidatos = membros.isEmpty() ? List.of(arquivo) : membros;
        if (idsEscolhidos != null && !idsEscolhidos.isEmpty()) {
            candidatos = candidatos.stream()
                    .filter(c -> idsEscolhidos.contains(c.getId()))
                    .toList();
        }
        return candidatos.stream().map(this::paraAlvo).toList();
    }

    @Transactional
    public void marcarStatus(Long arquivoId, String status) {
        ArquivoBruto arquivo = buscar(arquivoId);
        arquivo.setStatus(status);
        arquivoRepository.save(arquivo);
    }

    // o SRC usado e o do arquivo quando ele informou; senao o do cadastro
    private AlvoValidacao paraAlvo(ArquivoBruto arquivo) {
        Integer srsDoCadastro = arquivo.getConjunto() != null ? arquivo.getConjunto().getSrs() : null;
        boolean lidoDoArquivo = arquivo.getSrsDeclarado() != null;
        Integer srs = arquivo.getSrsUtilizado() != null ? arquivo.getSrsUtilizado()
                : arquivo.getSrsDeclarado() != null ? arquivo.getSrsDeclarado()
                : srsDoCadastro != null ? srsDoCadastro : ReprojetorFeicao.SRID_DESTINO;

        return new AlvoValidacao(
                arquivo.getId(), arquivo.getNome(), arquivo.getCaminhoInterno(),
                arquivo.getPathArmazenamento(), formatoDe(arquivo), srs, lidoDoArquivo);
    }

    private FormatoArquivo formatoDe(ArquivoBruto arquivo) {
        for (String candidato : new String[] {arquivo.getFormatoDetectado(), arquivo.getFormato()}) {
            if (candidato != null && !candidato.isBlank()) {
                try {
                    return FormatoArquivo.valueOf(candidato.trim().toUpperCase());
                } catch (IllegalArgumentException ignorado) {
                    // tenta o proximo
                }
            }
        }
        return FormatoArquivo.DESCONHECIDO;
    }

    private ArquivoBruto buscar(Long arquivoId) {
        return arquivoRepository.findById(arquivoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Arquivo bruto nao encontrado: " + arquivoId));
    }
}
