package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.cadastro.ConjuntoRequest;
import br.com.fatec.georural.dto.request.cadastro.FonteRequest;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import org.geotools.referencing.CRS;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class CadastroValidacaoService {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private static final Set<String> PERIODICIDADES = Set.of(
        "DIARIA",
        "SEMANAL",
        "MENSAL",
        "TRIMESTRAL",
        "ANUAL",
        "SOB_DEMANDA",
        "UNICA"
    );

    public void validarFonte(FonteRequest request) {
        if (request == null) {
            throw new RequisicaoInvalidaException(
                "Informe os dados da fonte."
            );
        }

        CadastroCampos.obrigatorio(
            request.nome(),
            "nome",
            120
        );

        CadastroCampos.obrigatorio(
            request.sigla(),
            "sigla",
            20
        );

        if ((request.orgao() != null && !request.orgao().isBlank())
            || (request.descricao() != null && !request.descricao().isBlank())) {
            throw new RequisicaoInvalidaException("O cadastro de fontes deste banco utiliza nome, sigla, esfera e tipos fornecidos; órgão e descrição não possuem armazenamento neste esquema.");
        }
        if (request.oferece() != null) {
            for (String tipo : request.oferece()) CadastroCampos.obrigatorio(tipo, "tipo fornecido", 120);
        }

        CadastroCampos.opcional(
            request.esfera(),
            "esfera",
            20
        );
    }

    public void validarConjunto(ConjuntoRequest request) {
        if (request == null) {
            throw new RequisicaoInvalidaException(
                "Informe os dados do conjunto."
            );
        }

        CadastroCampos.validarId(
            request.fonteId(),
            "a fonte do conjunto"
        );

        CadastroCampos.obrigatorio(
            request.nome(),
            "nome do conjunto",
            160
        );

        CadastroCampos.obrigatorio(
            request.tipoCamada(),
            "tipo de camada",
            30
        );

        CadastroCampos.opcional(
            request.descricao(),
            "descrição",
            400
        );

        String esquema = CadastroCampos.obrigatorio(
            request.esquemaTecnico(),
            "esquema técnico",
            20000
        );

        String competencia = CadastroCampos.opcional(
            request.competencia(),
            "competência",
            20
        );

        CadastroCampos.obrigatorio(
            request.cobertura(),
            "cobertura",
            30
        );

        CadastroCampos.opcional(
            request.periodicidade(),
            "periodicidade",
            20
        );

        validarSrs(request.srs());
        validarEsquema(esquema);
        if (competencia != null) validarCompetencia(competencia);
        normalizarPeriodicidade(request.periodicidade());
    }

    public void validarEntradaConjunto(ConjuntoRequest request) {
        boolean upload = !Boolean.FALSE.equals(request.upload());
        boolean api = Boolean.TRUE.equals(request.api());
        if (!upload && !api) throw new RequisicaoInvalidaException("Selecione Upload, API ou ambas as opções.");
        if (!api) return;
        String endpoint = CadastroCampos.obrigatorio(request.endpoint(), "endpoint", 500);
        try {
            java.net.URI url = new java.net.URI(endpoint);
            if (url.getScheme() == null || !(url.getScheme().equalsIgnoreCase("http") || url.getScheme().equalsIgnoreCase("https")) || url.getHost() == null || url.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (Exception erro) {
            throw new RequisicaoInvalidaException("Informe um endpoint HTTP ou HTTPS válido, sem usuário e senha na URL.");
        }
        if (request.formatoApi() == null || !Set.of("GeoJSON", "WFS / GML", "Shapefile (.zip)").contains(request.formatoApi())) {
            throw new RequisicaoInvalidaException("Selecione um formato válido para a API.");
        }
        CadastroCampos.opcional(request.autenticacao(), "autenticação", 4000);
    }

    private void validarSrs(Integer srs) {
        if (srs == null || srs <= 0 || srs > 999999) {
            throw new RequisicaoInvalidaException(
                "Informe um código EPSG entre 1 e 999999."
            );
        }

        try {
            CRS.decode("EPSG:" + srs, true);
        } catch (Exception erro) {
            throw new RequisicaoInvalidaException(
                "O código EPSG informado não foi reconhecido."
            );
        }
    }

    private void validarEsquema(String esquema) {
        try {
            var documento = jsonMapper.readTree(esquema);

            if (
                documento == null ||
                !documento.isObject() ||
                documento.isEmpty()
            ) {
                throw new RequisicaoInvalidaException(
                    "O esquema técnico deve ser um objeto JSON não vazio."
                );
            }

            var campos = documento.get("campos");

            if (campos == null) {
                return;
            }

            if (!campos.isArray() || campos.isEmpty()) {
                throw new RequisicaoInvalidaException(
                    "A propriedade campos deve ser uma lista não vazia."
                );
            }

            Set<String> nomes = new HashSet<>();

            for (var campo : campos) {
                if (!campo.isString() || campo.asString().isBlank()) {
                    throw new RequisicaoInvalidaException(
                        "Cada campo do esquema deve possuir um nome textual."
                    );
                }

                String nome = campo.asString()
                    .trim()
                    .toLowerCase(Locale.ROOT);

                if (!nomes.add(nome)) {
                    throw new RequisicaoInvalidaException(
                        "O esquema técnico possui campos repetidos."
                    );
                }
            }
        } catch (RequisicaoInvalidaException erro) {
            throw erro;
        } catch (Exception erro) {
            throw new RequisicaoInvalidaException(
                "O esquema técnico contém um JSON inválido."
            );
        }
    }

    private void validarCompetencia(String competencia) {
        try {
            if (competencia.matches("\\d{4}")) {
                Year ano = Year.parse(competencia);

                if (ano.getValue() < 1) {
                    throw new IllegalArgumentException();
                }

                return;
            }

            if (competencia.matches("\\d{4}-\\d{2}")) {
                YearMonth mes = YearMonth.parse(competencia);

                if (mes.getYear() < 1) {
                    throw new IllegalArgumentException();
                }

                return;
            }

            if (competencia.matches("\\d{4}-\\d{2}-\\d{2}")) {
                validarData(competencia);
                return;
            }

            if (
                competencia.matches(
                    "\\d{4}-\\d{2}-\\d{2}/\\d{4}-\\d{2}-\\d{2}"
                )
            ) {
                String[] datas = competencia.split("/");

                LocalDate inicio = validarData(datas[0]);
                LocalDate fim = validarData(datas[1]);

                if (fim.isBefore(inicio)) {
                    throw new RequisicaoInvalidaException(
                        "O fim da competência não pode ser anterior ao início."
                    );
                }

                return;
            }
        } catch (RequisicaoInvalidaException erro) {
            throw erro;
        } catch (DateTimeParseException erro) {
            throw new RequisicaoInvalidaException(
                "A competência contém uma data inválida."
            );
        } catch (IllegalArgumentException erro) {
            throw new RequisicaoInvalidaException(
                "A competência deve possuir um ano maior que zero."
            );
        }

        throw new RequisicaoInvalidaException(
            "Informe a competência como AAAA, AAAA-MM, AAAA-MM-DD " +
            "ou AAAA-MM-DD/AAAA-MM-DD."
        );
    }

    private LocalDate validarData(String valor) {
        LocalDate data = LocalDate.parse(valor);

        if (data.getYear() < 1) {
            throw new IllegalArgumentException();
        }

        return data;
    }

    public String normalizarPeriodicidade(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        String periodicidade = Normalizer.normalize(
                valor.trim(),
                Normalizer.Form.NFD
            )
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[\\s-]+", "_");

        if (!PERIODICIDADES.contains(periodicidade)) {
            throw new RequisicaoInvalidaException(
                "Periodicidade inválida. Utilize DIARIA, SEMANAL, " +
                "MENSAL, TRIMESTRAL, ANUAL, SOB_DEMANDA ou UNICA."
            );
        }

        return periodicidade;
    }
}