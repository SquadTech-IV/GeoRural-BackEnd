package br.com.fatec.georural.mapper;

import br.com.fatec.georural.dto.response.ArquivoDetalheResponse;
import br.com.fatec.georural.dto.response.ArquivoItemResponse;
import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.entity.ArquivoDatalake;
import br.com.fatec.georural.entity.ArquivoDatalakeItem;
import org.springframework.stereotype.Component;

import java.util.List;

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

    public ArquivoItemResponse toItem(ArquivoDatalakeItem i) {
        return new ArquivoItemResponse(
                i.getId(),
                i.getNome(),
                i.getFormato(),
                i.getTamanhoBytes(),
                i.getDescricao()
        );
    }

    public ArquivoDetalheResponse toDetalhe(ArquivoDatalake a, List<ArquivoDatalakeItem> itens) {
        List<ArquivoItemResponse> arquivos = itens.stream()
                .map(this::toItem)
                .toList();
        return new ArquivoDetalheResponse(
                a.getId(),
                a.getNome(),
                a.getNomeArquivo(),
                a.getFonte(),
                a.getFormato(),
                a.getSrid(),
                a.getTamanhoBytes(),
                a.getQtdArquivos(),
                a.getCaminho(),
                a.getSituacao(),
                a.getDescricao(),
                a.getRecebidoEm(),
                arquivos
        );
    }
}