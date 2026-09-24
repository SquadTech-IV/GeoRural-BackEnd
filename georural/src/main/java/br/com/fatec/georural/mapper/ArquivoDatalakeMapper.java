package br.com.fatec.georural.mapper;

import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.entity.ArquivoDatalake;
import org.springframework.stereotype.Component;

@Component
public class ArquivoDatalakeMapper {

    public ArquivoResumoResponse toResumo(ArquivoDatalake a) {
        return new ArquivoResumoResponse(
                a.getId(),
                a.getNome(),
                a.getNomeArquivo(),
                a.getFonte(),
                a.getFormato(),
                a.getQtdArquivos(),
                a.getSituacao(),
                a.getRecebidoEm()
        );
    }
}