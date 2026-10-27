package br.com.fatec.georural.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Página de resultados de uma listagem.
 */
@Schema(description = "Página de resultados")
public record PaginaResponse<T>(
        @Schema(description = "Itens da página") List<T> itens,
        @Schema(description = "Número da página, começando em 0") int pagina,
        @Schema(description = "Tamanho solicitado da página") int tamanho,
        @Schema(description = "Total de itens que atendem aos filtros") long totalItens,
        @Schema(description = "Total de páginas") int totalPaginas
) {

    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}