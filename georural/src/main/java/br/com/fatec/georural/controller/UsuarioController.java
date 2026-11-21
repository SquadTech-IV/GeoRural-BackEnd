package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.usuario.CriarUsuarioRequest;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.service.usuario.UsuarioCadastroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Gestão de usuários", description = "Cadastro, consulta, alteração de perfil/status e convites de acesso (restrito a ADMIN)")
public class UsuarioController {

    private final UsuarioCadastroService usuarioCadastroService;

    @PostMapping
    @Operation(summary = "Cadastra um novo usuário no Keycloak e no banco local e envia e-mail de definição de senha")
    public ResponseEntity<UsuarioResponse> criarUsuario(@Valid @RequestBody CriarUsuarioRequest request) {
        UsuarioResponse criado = usuarioCadastroService.cadastrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PostMapping("/{id}/reenviar-convite")
    @Operation(summary = "Reenvia o e-mail de definição de senha (UPDATE_PASSWORD) para um usuário ativo ainda pendente")
    public ResponseEntity<UsuarioResponse> reenviarConvite(@PathVariable Long id) {
        UsuarioResponse atualizado = usuarioCadastroService.reenviarConvite(id);
        return ResponseEntity.ok(atualizado);
    }
}