package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.usuario.CriarUsuarioRequest;
import br.com.fatec.georural.dto.request.usuario.EditarUsuarioRequest;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.usuario.DivergenciaUsuarioResponse;
import br.com.fatec.georural.dto.response.usuario.PerfilAcessoResponse;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.service.usuario.UsuarioCadastroService;
import br.com.fatec.georural.service.usuario.UsuarioEdicaoService;
import br.com.fatec.georural.service.usuario.UsuarioStatusService;
import br.com.fatec.georural.service.usuario.UsuarioConsultaService;
import br.com.fatec.georural.service.usuario.UsuarioDivergenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

import org.springframework.data.web.PageableDefault;
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
    private final UsuarioEdicaoService usuarioEdicaoService;
    private final UsuarioStatusService usuarioStatusService;
    private final UsuarioConsultaService usuarioConsultaService;
    private final UsuarioDivergenciaService usuarioDivergenciaService;

    @GetMapping
    @Operation(summary = "Lista os usuários cadastrados com paginação e filtros por busca (nome/e-mail), perfilId e status")
    public PaginaResponse<UsuarioResponse> listarUsuarios(
            @Parameter(description = "Trecho do nome ou e-mail para busca")
            @RequestParam(required = false) String busca,
            @Parameter(description = "Identificador do perfil de acesso (per_id)")
            @RequestParam(required = false) Long perfilId,
            @Parameter(description = "Status do usuário: S (ativo) ou N (inativo)")
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {

        Page<UsuarioResponse> resultado = usuarioConsultaService.listar(busca, perfilId, status, pageable);
        return PaginaResponse.de(resultado);
    }

    @GetMapping("/perfis")
    @Operation(summary = "Lista os perfis de acesso disponíveis para atribuição aos usuários")
    public List<PerfilAcessoResponse> listarPerfisAcesso() {
        return usuarioConsultaService.listarPerfis();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retorna os detalhes de um usuário específico pelo identificador")
    public ResponseEntity<UsuarioResponse> detalharUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioConsultaService.detalhar(id));
    }

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

    @PatchMapping("/{id}")
    @Operation(summary = "Atualiza nome e/ou perfil do usuário (e-mail não editável; troca de perfil encerra sessões)")
    public ResponseEntity<UsuarioResponse> editarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody EditarUsuarioRequest request) {
        UsuarioResponse atualizado = usuarioEdicaoService.editar(id, request);
        return ResponseEntity.ok(atualizado);
    }

    @PostMapping("/{id}/desativar")
    @Operation(summary = "Desativa o usuário no banco e no Keycloak e revoga suas sessões ativas")
    public ResponseEntity<UsuarioResponse> desativarUsuario(@PathVariable Long id) {
        UsuarioResponse atualizado = usuarioStatusService.desativar(id);
        return ResponseEntity.ok(atualizado);
    }

    @PostMapping("/{id}/reativar")
    @Operation(summary = "Reativa um usuário previamente desativado no banco e no Keycloak")
    public ResponseEntity<UsuarioResponse> reativarUsuario(@PathVariable Long id) {
        UsuarioResponse atualizado = usuarioStatusService.reativar(id);
        return ResponseEntity.ok(atualizado);
    }

    @GetMapping("/divergencias")
    @Operation(summary = "Lista divergências de sincronização entre a tabela USUARIO (Oracle) e o realm do Keycloak")
    public List<DivergenciaUsuarioResponse> listarDivergencias() {
        return usuarioDivergenciaService.listarDivergencias();
    }
}