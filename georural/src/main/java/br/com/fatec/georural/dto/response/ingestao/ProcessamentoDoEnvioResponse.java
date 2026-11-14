package br.com.fatec.georural.dto.response.ingestao;

import java.util.List;

// o que o pipeline fez com os arquivos aceitos no envio
public record ProcessamentoDoEnvioResponse(
        int disparados,
        int concluidos,
        int falhados,
        String mensagem,
        List<Item> itens
) {

    // uma rodada, por arquivo. As etapas ficam na tela de operacao, pelo dagRunId
    public record Item(
            Long arquivoBrutoId,
            String nome,
            String dagRunId,
            String situacao,
            Long versaoGerada,
            String mensagem
    ) {
    }

    // nenhum arquivo aceito: nada para rodar
    public static ProcessamentoDoEnvioResponse nadaParaRodar() {
        return new ProcessamentoDoEnvioResponse(0, 0, 0,
                "Nenhum arquivo aceito no envio: nada foi enviado ao pipeline.", List.of());
    }
}
