package br.com.fatec.georural.dto.response.catalogo;

import java.time.LocalDateTime;
import java.util.List;

// Um conjunto de dados na listagem do catalogo.
public record CatalogoItemResponse(
        Long conjuntoId,
        String nome,
        String descricao,
        FonteResumo fonte,
        TipoResumo tipo,
        Integer srs,
        String cobertura,
        String periodicidade,
        String situacao,
        String formaEntrada,
        boolean ativo,
        VersaoResumo versaoVigente,
        AcervoResumo acervo,
        List<IndicadorResumo> indicadores,
        String indicadoresTexto,
        LocalDateTime cadastradoEm
) {}
