package br.com.fatec.georural.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// Envelope de paginacao das APIs de consulta (/api/v1).
public record PaginaResponse<T>(
        List<T> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas,
        boolean primeira,
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
