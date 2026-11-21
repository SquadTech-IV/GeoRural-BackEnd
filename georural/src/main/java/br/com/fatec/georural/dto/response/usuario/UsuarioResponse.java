package br.com.fatec.georural.dto.response.usuario;

import br.com.fatec.georural.entity.Usuario;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        String status,
        Long perfilId,
        String perfilFuncao,
        String perfilNome,
        LocalDateTime ultimoLogin,
        LocalDateTime dataCadastro,
        Boolean convitePendente,
        String avisoEnvioEmail
) {
    public static UsuarioResponse de(Usuario u, Boolean convitePendente, String avisoEnvioEmail) {
        return new UsuarioResponse(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getStatus(),
                u.getPerfil() != null ? u.getPerfil().getId() : null,
                u.getPerfil() != null ? u.getPerfil().getFuncao() : null,
                u.getPerfil() != null ? u.getPerfil().getNome() : null,
                u.getUltimoLogin(),
                u.getDataCadastro(),
                convitePendente,
                avisoEnvioEmail
        );
    }

    public static UsuarioResponse de(Usuario u) {
        return de(u, null, null);
    }
}