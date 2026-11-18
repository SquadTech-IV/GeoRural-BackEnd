package br.com.fatec.georural.security;

public record UsuarioAutenticado(
        Long usuId,
        String keycloakId,
        String status,
        String perFuncao
) {
    public boolean isAdminOuGestor() {
        return "ADMIN".equalsIgnoreCase(perFuncao) || "GESTOR".equalsIgnoreCase(perFuncao);
    }

    public boolean isOperador() {
        return "OPERADOR".equalsIgnoreCase(perFuncao);
    }
}