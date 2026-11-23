package br.com.fatec.georural.dto.response.usuario;

import br.com.fatec.georural.entity.Perfil;

public record PerfilAcessoResponse(
        Long id,
        String funcao,
        String nome
) {
    public static PerfilAcessoResponse de(Perfil perfil) {
        return new PerfilAcessoResponse(
                perfil.getId(),
                perfil.getFuncao(),
                perfil.getNome()
        );
    }
}