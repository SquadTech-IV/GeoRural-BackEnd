package br.com.fatec.georural.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.data.domain.Page;

import java.util.List;

// Envelope de paginacao das APIs de consulta (/api/v1).
@Schema(description = "Envelope de paginacao usado por todas as consultas de /api/v1")
public record PaginaResponse<T>(

        @Schema(description = "Os itens da pagina")
        List<T> itens,

        @Schema(description = "Numero da pagina, comecando em 0", example = "0")
        int pagina,

        @Schema(description = "Quantos itens a pagina pediu", example = "20")
        int tamanho,

        @Schema(description = "Total de itens que o filtro encontrou", example = "137")
        long totalItens,

        @Schema(description = "Total de paginas para esse tamanho", example = "7")
        int totalPaginas,

        @Schema(description = "Se esta e a primeira pagina")
        boolean primeira,

        @Schema(description = "Se esta e a ultima pagina")
        boolean ultima
) {

    public static <T> PaginaResponse<T> de(
            List<T> itens,
            int pagina,
            int tamanho,
            long totalItens) {
        int totalPaginas = tamanho <= 0
                ? 0
                : (int) ((totalItens + tamanho - 1) / tamanho);

        return new PaginaResponse<>(
                itens,
                pagina,
                tamanho,
                totalItens,
                totalPaginas,
                pagina == 0,
                totalPaginas == 0 || pagina >= totalPaginas - 1
        );
    }

    // Converte uma Page do Spring Data para o mesmo envelope.
    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
