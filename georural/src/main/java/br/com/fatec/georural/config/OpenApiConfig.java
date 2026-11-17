package br.com.fatec.georural.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Documentacao OpenAPI da API de consulta (US#17.07).
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_TOKEN = "bearerAuth";
    private static final String ESQUEMA_ERRO = "ErroResponse";

    private static final String DESCRICAO = """
            API de consulta somente leitura do GeoRural DataHub, para integrar os
            resultados do processamento a outros sistemas sem passar pelo portal.

            ## Convencoes

            - **Prefixo**: todas as rotas de consulta ficam em `/api/v1`. As rotas de
              operacao do portal continuam em `/api/...` e nao fazem parte deste contrato.
            - **Paginacao**: `pagina` comeca em 0 e `tamanho` define quantos itens vem.
              A resposta e sempre o envelope com `itens`, `pagina`, `tamanho`,
              `totalItens`, `totalPaginas`, `primeira` e `ultima`. O teto de `tamanho`
              varia com o peso da rota: 200 no geral, 500 nos registros tratados e nos
              resultados de indicador, e 50 quando a geometria e pedida.
            - **Ordenacao**: `ordenar` aceita apenas os valores listados em cada rota.
              Toda ordenacao tem o identificador como critario de desempate, para que a
              paginacao nao repita nem pule linha.
            - **Datas**: ISO-8601. Parametros de data usam `AAAA-MM-DD` e o dia final
              entra por inteiro. Competencia usa `AAAA-MM`.
            - **Filtros de texto**: `q` faz busca parcial sem diferenciar maiuscula de
              minuscula. Os demais filtros sao exatos.
            - **Versoes**: sem filtro de versao ou de vigencia, as consultas devolvem os
              registros de todas as versoes. Cada linha traz a versao a que pertence.
            - **Erros**: sempre no mesmo formato, com `timestamp`, `status`, `erro`,
              `mensagem` e `caminho`.

            ## Autenticacao

            Todas as rotas de `/api` exigem um token JWT emitido pelo Keycloak, enviado
            no cabecalho `Authorization: Bearer <token>`. A documentacao
            (`/swagger-ui.html` e `/v3/api-docs`) fica aberta. Use o botao **Authorize**
            para colar o token e testar as rotas por aqui.
            """;

    private final String titulo;
    private final String versao;
    private final List<String> servidores;
    private final String issuer;

    public OpenApiConfig(
            @Value("${georural.api.titulo:GeoRural DataHub - API de consulta}")
            String titulo,
            @Value("${georural.api.versao:v1}") String versao,
            @Value("${georural.api.servidores:http://localhost:8080}")
            List<String> servidores,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
            String issuer) {
        this.titulo = titulo;
        this.versao = versao;
        this.servidores = servidores;
        this.issuer = issuer;
    }

    @Bean
    public OpenAPI openApi() {
        List<Server> lista = new ArrayList<>();

        for (String url : servidores) {
            lista.add(new Server().url(url.trim()));
        }

        return new OpenAPI()
                .info(new Info()
                        .title(titulo)
                        .version(versao)
                        .description(DESCRICAO)
                        .contact(new Contact()
                                .name("SquadTech IV - GeoRural DataHub")))
                .servers(lista)
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_TOKEN))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_TOKEN, esquemaToken())
                        .addSchemas(ESQUEMA_ERRO, esquemaErro()))
                .tags(tags());
    }

    // Acrescenta as respostas de erro padrao em toda operacao que nao as declarou.
    @Bean
    public OpenApiCustomizer respostasDeErro() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((caminho, item) ->
                    item.readOperations().forEach(this::completarRespostas));
        };
    }

    private void completarRespostas(Operation operacao) {
        if (operacao.getResponses() == null) {
            return;
        }

        Map<String, String> padrao = Map.of(
                "400", "Parametro invalido na requisicao",
                "401", "Token ausente, expirado ou invalido",
                "404", "Recurso nao encontrado",
                "500", "Erro interno no servidor"
        );

        padrao.forEach((codigo, descricao) -> {
            if (operacao.getResponses().get(codigo) == null) {
                operacao.getResponses().addApiResponse(codigo, respostaErro(descricao));
            }
        });
    }

    private ApiResponse respostaErro(String descricao) {
        return new ApiResponse()
                .description(descricao)
                .content(new Content().addMediaType(
                        "application/json",
                        new MediaType().schema(
                                new Schema<>().$ref(
                                        "#/components/schemas/" + ESQUEMA_ERRO))));
    }

    private SecurityScheme esquemaToken() {
        String onde = issuer == null || issuer.isBlank()
                ? "Keycloak"
                : issuer;

        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Token JWT emitido por " + onde
                        + ". Envie em Authorization: Bearer <token>.");
    }

    // Formato unico de erro, igual ao ErroResponse devolvido pelo GlobalExceptionHandler.
    private Schema<?> esquemaErro() {
        return new ObjectSchema()
                .description("Formato unico de erro da API")
                .addProperty("timestamp", new StringSchema()
                        .format("date-time")
                        .description("Quando o erro aconteceu"))
                .addProperty("status", new IntegerSchema()
                        .description("Codigo HTTP"))
                .addProperty("erro", new StringSchema()
                        .description("Nome do status HTTP"))
                .addProperty("mensagem", new StringSchema()
                        .description("O que deu errado, em texto"))
                .addProperty("caminho", new StringSchema()
                        .description("Rota chamada"));
    }

    private List<Tag> tags() {
        return List.of(
                new Tag().name("Catalogo de dados")
                        .description("Conjuntos de dados, filtros e detalhe"),
                new Tag().name("Indicadores e metricas")
                        .description("Indicadores, resultados e metricas agregadas"),
                new Tag().name("Relatorios de qualidade")
                        .description("Relatorios, resumo e regras de qualidade"),
                new Tag().name("Qualidade e quarentena")
                        .description("Execucoes de validacao e registros em quarentena"),
                new Tag().name("Linhagem dos dados")
                        .description("Do resultado ate a origem, e o caminho de volta"),
                new Tag().name("Execucoes das pipelines")
                        .description("Historico por etapa, por rodada e situacao atual")
        );
    }
}
