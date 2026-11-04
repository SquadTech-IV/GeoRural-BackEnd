package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.perfil.PerfilCamadaResponse;
import br.com.fatec.georural.dto.response.perfil.PerfilCamadaResponse.CampoResponse;
import br.com.fatec.georural.service.pipeline.perfil.PerfilCamada;
import br.com.fatec.georural.service.pipeline.perfil.RegistroPerfis;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/perfis")
@RequiredArgsConstructor
public class PerfilCamadaController {

    private final RegistroPerfis registro;

    // perfis disponiveis
    @GetMapping
    public List<PerfilCamadaResponse> listar() {
        return registro.tiposComPerfil().stream()
                .map(registro::para)
                .map(this::paraResposta)
                .toList();
    }

    // perfil de um tipo de conjunto
    @GetMapping("/{tipoConjunto}")
    public PerfilCamadaResponse porTipo(@PathVariable String tipoConjunto) {
        return paraResposta(registro.para(tipoConjunto));
    }

    private PerfilCamadaResponse paraResposta(PerfilCamada perfil) {
        return new PerfilCamadaResponse(
                perfil.tipoConjunto(),
                perfil.rotulo(),
                perfil.geometriaEsperada().descricao(),
                perfil.exigeGeometria(),
                perfil.exigeDentroDoParana(),
                perfil.campoIdentificador(),
                perfil.campos().stream()
                        .map(c -> new CampoResponse(c.nome(), c.tipo().descricao(),
                                c.obrigatorio(), c.nomesAceitos()))
                        .toList());
    }
}
