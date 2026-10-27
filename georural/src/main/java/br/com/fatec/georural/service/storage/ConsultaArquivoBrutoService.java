package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.dto.response.ArquivoBrutoResumoResponse;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Consulta somente leitura dos arquivos registrados na zona bruta (tabela ARQUIVO_BRUTO).
 *
 * <p>A listagem vem do banco, e não do bucket: o registro tem responsável, data e situação, e é paginável.
 * Inclui todos os registros, de qualquer situação e via de entrada, inclusive os do fluxo de embargos.
 */
@Service
@RequiredArgsConstructor
public class ConsultaArquivoBrutoService {

    public static final int TAMANHO_MAXIMO = 100;

    private static final Set<String> SITUACOES_VALIDAS = Set.of(
            "RECEBENDO", "RECEBIDO", "VALIDANDO", "APROVADO", "REJEITADO", "PROCESSADO", "FALHA");

    private final ArquivoBrutoRepository repositorio;

    /**
     * @param conjuntoId filtro opcional por conjunto
     * @param status     filtro opcional por situação (sem diferenciar maiúsculas)
     * @param pagina     número da página, a partir de 0
     * @param tamanho    itens por página, de 1 a {@value #TAMANHO_MAXIMO}
     * @throws RequisicaoInvalidaException se a página, o tamanho ou a situação forem inválidos
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ArquivoBrutoResumoResponse> listar(Long conjuntoId, String status, int pagina, int tamanho) {
        if (pagina < 0) {
            throw new RequisicaoInvalidaException("A página deve ser maior ou igual a zero.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new RequisicaoInvalidaException("O tamanho da página deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        String situacao = normalizarSituacao(status);

        // Mais recentes primeiro; o id desempata para a ordem ser estável entre páginas.
        Pageable paginacao = PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Direction.DESC, "dataUpload").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<ArquivoBruto> resultado;
        if (conjuntoId != null && situacao != null) {
            resultado = repositorio.findByConjuntoIdAndStatus(conjuntoId, situacao, paginacao);
        } else if (conjuntoId != null) {
            resultado = repositorio.findByConjuntoId(conjuntoId, paginacao);
        } else if (situacao != null) {
            resultado = repositorio.findByStatus(situacao, paginacao);
        } else {
            resultado = repositorio.findAllBy(paginacao);
        }
        return PaginaResponse.de(resultado.map(ArquivoBrutoResumoResponse::de));
    }

    private static String normalizarSituacao(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String situacao = status.trim().toUpperCase(Locale.ROOT);
        if (!SITUACOES_VALIDAS.contains(situacao)) {
            throw new RequisicaoInvalidaException(
                    "Situação inválida: " + status + ". Valores aceitos: " + new TreeSet<>(SITUACOES_VALIDAS) + ".");
        }
        return situacao;
    }
}