package br.com.fatec.georural.dto.response.auditoria;

import br.com.fatec.georural.entity.Auditoria;

import java.time.LocalDateTime;

public record AuditoriaResponse(
        Long id,
        Long usuarioId,
        String usuarioNome,
        String acao,
        String entidade,
        Long registroId,
        String dadosAntigos,
        String dadosNovos,
        String ip,
        LocalDateTime dataHora
) {
    public static AuditoriaResponse de(Auditoria a) {
        return new AuditoriaResponse(
                a.getId(),
                a.getUsuario() != null ? a.getUsuario().getId() : null,
                a.getUsuarioNome(),
                a.getOperacao(),
                a.getEntidadeAfetada(),
                a.getRegistroId(),
                a.getDadosAntigos(),
                a.getDadosNovos(),
                a.getIpOrigem(),
                a.getDataHora());
    }
}
