package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.auditoria.AuditoriaResponse;
import br.com.fatec.georural.service.auditoria.AuditoriaConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
@Tag(name = "Auditoria", description = "Histórico de ações do sistema (US#20) - somente perfil AUDITOR")
public class AuditoriaController {

    private final AuditoriaConsultaService service;

    @GetMapping
    @Operation(summary = "Consulta paginada do histórico de ações, mais recentes primeiro")
    public PaginaResponse<AuditoriaResponse> listar(
            @Parameter(description = "Identificador do usuário responsável")
            @RequestParam(required = false) Long usuarioId,
            @Parameter(description = "Ação: UPLOAD, DOWNLOAD, EXECUCAO, API_ACCESS, EXPORT, INSERT, UPDATE...")
            @RequestParam(required = false) String acao,
            @Parameter(description = "Data inicial (aaaa-mm-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Data final, inclusive (aaaa-mm-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @PageableDefault(size = 20, sort = "dataHora", direction = Sort.Direction.DESC) Pageable pageable) {
        return PaginaResponse.de(service.consultar(usuarioId, acao, de, ate, pageable));
    }
}
