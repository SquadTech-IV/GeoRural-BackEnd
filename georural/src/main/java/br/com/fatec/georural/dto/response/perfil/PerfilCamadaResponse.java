package br.com.fatec.georural.dto.response.perfil;

import java.util.List;

// o que uma camada espera encontrar no arquivo
public record PerfilCamadaResponse(
        String tipoConjunto,
        String rotulo,
        String geometriaEsperada,
        boolean exigeGeometria,
        boolean exigeDentroDoParana,
        String campoIdentificador,
        List<CampoResponse> campos) {

    public record CampoResponse(
            String nome,
            String tipo,
            boolean obrigatorio,
            List<String> nomesAceitos) {
    }
}
