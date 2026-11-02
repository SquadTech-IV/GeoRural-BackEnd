package br.com.fatec.georural.service.pipeline.etapa;

import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.EtapaPipeline;
import br.com.fatec.georural.service.pipeline.ResultadoEtapa;
import br.com.fatec.georural.service.pipeline.leitor.ContextoLeitura;
import br.com.fatec.georural.service.pipeline.leitor.LeitorFactory;
import br.com.fatec.georural.service.pipeline.perfil.PerfilCamada;
import br.com.fatec.georural.service.pipeline.perfil.RegistroPerfis;
import br.com.fatec.georural.service.pipeline.validacao.AlvoValidacao;
import br.com.fatec.georural.service.pipeline.validacao.AlvosValidacaoService;
import br.com.fatec.georural.service.pipeline.validacao.CargaStagingService;
import br.com.fatec.georural.service.pipeline.validacao.FeicaoPadronizada;
import br.com.fatec.georural.service.pipeline.validacao.PadronizadorFeicao;
import br.com.fatec.georural.service.pipeline.validacao.ProblemaFeicao;
import br.com.fatec.georural.service.pipeline.validacao.RegistroQuarentenaService;
import br.com.fatec.georural.service.pipeline.validacao.RegistroQuarentenaService.RegistroPendente;
import br.com.fatec.georural.service.pipeline.validacao.RegrasQualidade;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// etapa 2 - VALIDACAO: le, padroniza pelo perfil e carrega a area de pouso
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidacaoEtapa implements EtapaPipeline {

    private static final String ETAPA = "VALIDACAO";

    private final AlvosValidacaoService alvosService;
    private final RegistroPerfis registroPerfis;
    private final LeitorFactory leitorFactory;
    private final PadronizadorFeicao padronizador;
    private final CargaStagingService cargaStaging;
    private final RegistroQuarentenaService quarentena;
    private final ArmazenamentoZonaPort armazenamento;

    @Override
    public Etapa etapa() {
        return Etapa.VALIDACAO;
    }

    // sem transacao unica: cada lote de 500 fecha a sua
    @Override
    public ResultadoEtapa executar(ContextoExecucao ctx) {
        Long execucaoId = exigir(ctx.getExecucaoIdAtual(), "execucaoIdAtual");
        Long arquivoId = exigir(ctx.getArquivoBrutoId(), "arquivoBrutoId");

        String tipoConjunto = alvosService.tipoConjunto(arquivoId);
        Optional<PerfilCamada> perfilDoConjunto = registroPerfis.buscar(tipoConjunto);

        List<AlvoValidacao> alvos = alvosService.alvos(arquivoId, idsEscolhidos(ctx));
        if (alvos.isEmpty()) {
            return ResultadoEtapa.semFeicoes("Nenhuma camada selecionada para validar.");
        }

        cargaStaging.limpar(execucaoId);
        ctx.comParametro("camadas", String.valueOf(alvos.size()));

        Contagem geral = new Contagem();
        List<String> resumos = new ArrayList<>();
        Set<String> perfisUsados = new LinkedHashSet<>();

        for (AlvoValidacao alvo : alvos) {
            // o nome da camada decide o perfil
            Optional<PerfilCamada> perfil = registroPerfis.porNomeDeCamada(alvo.nome())
                    .or(() -> perfilDoConjunto);

            if (perfil.isEmpty()) {
                geral.somar(semPerfil(alvo, tipoConjunto, execucaoId));
                resumos.add(alvo.rotulo() + ": sem perfil de camada");
                continue;
            }

            perfisUsados.add(perfil.get().tipoConjunto());
            Contagem daCamada = validarCamada(alvo, perfil.get(), execucaoId);
            geral.somar(daCamada);
            resumos.add(alvo.rotulo() + " (" + perfil.get().tipoConjunto() + "): "
                    + daCamada.aprovados + " aprovada(s), " + daCamada.rejeitados + " rejeitada(s)");
        }

        ctx.comParametro("perfis", String.join(",", perfisUsados));

        return ResultadoEtapa.de(geral.total, geral.rejeitados, String.join("; ", resumos));
    }

    // rejeita a camada que nenhum perfil atende
    private Contagem semPerfil(AlvoValidacao alvo, String tipoConjunto, Long execucaoId) {
        Long validacaoId = quarentena.abrirValidacao(alvo.arquivoId(), execucaoId);
        alvosService.marcarStatus(alvo.arquivoId(), "VALIDANDO");

        quarentena.registrarLote(validacaoId, ETAPA, List.of(new RegistroPendente(
                alvo.rotulo(), null, null,
                ProblemaFeicao.erro(RegrasQualidade.FORA_DO_DOMINIO, "camada",
                        alvo.nome(), String.join(", ", registroPerfis.camadasReconhecidas()),
                        "Nenhum perfil de camada atende '" + alvo.nome() + "', e o tipo do "
                                + "conjunto (" + tipoConjunto + ") tambem nao tem perfil. A camada "
                                + "nao foi validada para nao ser lida com os campos de outra."))));

        Contagem contagem = new Contagem();
        contagem.total = 1;
        contagem.rejeitados = 1;
        encerrarCamada(alvo, validacaoId, contagem);
        return contagem;
    }

    // valida uma camada e devolve as contagens dela
    private Contagem validarCamada(AlvoValidacao alvo, PerfilCamada perfil, Long execucaoId) {
        Long validacaoId = quarentena.abrirValidacao(alvo.arquivoId(), execucaoId);
        alvosService.marcarStatus(alvo.arquivoId(), "VALIDANDO");
        Contagem contagem = new Contagem();

        if (!leitorFactory.temLeitor(alvo.formato())) {
            quarentena.registrarLote(validacaoId, ETAPA, List.of(new RegistroPendente(
                    alvo.rotulo(), null, null,
                    ProblemaFeicao.erro(RegrasQualidade.FORMATO_NAO_SUPORTADO, "formato",
                            alvo.formato().rotulo(), ".shp, .gpkg, .geojson, .json, .csv",
                            "A camada '" + alvo.rotulo() + "' esta em formato que o pipeline "
                                    + "ainda nao le, por isso nao foi validada."))));
            // a camada conta como uma unidade recebida e rejeitada
            contagem.total = 1;
            contagem.rejeitados = 1;
            encerrarCamada(alvo, validacaoId, contagem);
            return contagem;
        }

        Set<String> identificadoresVistos = new HashSet<>();
        // registra aviso de estrutura uma vez por camada, nao por feicao
        Set<String> avisosJaRegistrados = new HashSet<>();
        List<FeicaoPadronizada> loteStaging = new ArrayList<>();
        List<RegistroPendente> loteQuarentena = new ArrayList<>();

        ContextoLeitura leitura = new ContextoLeitura(
                alvo.formato(), alvo.nome(), alvo.caminhoInterno(),
                () -> armazenamento.ler(Zona.BRUTA, alvo.pathArmazenamento()),
                alvo.srsUtilizado());

        try {
            if (!alvo.srsLidoDoArquivo()) {
                loteQuarentena.add(new RegistroPendente(alvo.rotulo(), null, null,
                        ProblemaFeicao.aviso(RegrasQualidade.SRC_AUSENTE_OU_DIVERGENTE, "src",
                                "nao informado", "EPSG:" + alvo.srsUtilizado(),
                                "O arquivo nao informou o sistema de referencia. Foi usado o do "
                                        + "cadastro do conjunto (EPSG:" + alvo.srsUtilizado() + ").")));
            }

            leitorFactory.para(alvo.formato()).ler(leitura, feicao -> {
                contagem.total++;
                FeicaoPadronizada padronizada = padronizador.padronizar(perfil, feicao, alvo.srsUtilizado());

                for (ProblemaFeicao problema : padronizada.problemas()) {
                    if (problema.rejeita()
                            || avisosJaRegistrados.add(problema.regra() + "|" + problema.campo())) {
                        loteQuarentena.add(RegistroPendente.de(padronizada, problema));
                    }
                }

                boolean duplicada = padronizada.identificador() != null
                        && !identificadoresVistos.add(padronizada.identificador());
                if (duplicada) {
                    loteQuarentena.add(RegistroPendente.de(padronizada, ProblemaFeicao.erro(
                            RegrasQualidade.DUPLICADO_NO_ARQUIVO, perfil.campoIdentificador(),
                            padronizada.identificador(), "identificador unico no arquivo",
                            "O identificador '" + padronizada.identificador() + "' aparece mais de "
                                    + "uma vez na camada '" + alvo.rotulo() + "'.")));
                }

                if (padronizada.rejeitada() || duplicada) {
                    contagem.rejeitados++;
                } else {
                    contagem.aprovados++;
                    loteStaging.add(padronizada);
                }

                if (loteStaging.size() >= CargaStagingService.TAMANHO_LOTE) {
                    cargaStaging.gravarLote(execucaoId, alvo.arquivoId(), "PIPELINE", loteStaging);
                    loteStaging.clear();
                }
                if (loteQuarentena.size() >= CargaStagingService.TAMANHO_LOTE) {
                    quarentena.registrarLote(validacaoId, ETAPA, loteQuarentena);
                    loteQuarentena.clear();
                }
            });

        } catch (RuntimeException e) {
            log.error("Camada '{}' do arquivo {} nao pode ser lida: {}",
                    alvo.rotulo(), alvo.arquivoId(), e.getMessage());
            loteQuarentena.add(new RegistroPendente(alvo.rotulo(), null, null,
                    ProblemaFeicao.erro(RegrasQualidade.FORMATO_NAO_SUPORTADO, "arquivo",
                            alvo.formato().rotulo(), "arquivo legivel e completo",
                            "A leitura da camada '" + alvo.rotulo() + "' falhou: " + e.getMessage())));
            contagem.total++;
            contagem.rejeitados++;
        }

        if (!loteStaging.isEmpty()) {
            cargaStaging.gravarLote(execucaoId, alvo.arquivoId(), "PIPELINE", loteStaging);
        }
        if (!loteQuarentena.isEmpty()) {
            quarentena.registrarLote(validacaoId, ETAPA, loteQuarentena);
        }
        encerrarCamada(alvo, validacaoId, contagem);
        return contagem;
    }

    private void encerrarCamada(AlvoValidacao alvo, Long validacaoId, Contagem contagem) {
        quarentena.fecharValidacao(validacaoId, contagem.total, contagem.aprovados, contagem.rejeitados);
        alvosService.marcarStatus(alvo.arquivoId(),
                contagem.aprovados > 0 ? "APROVADO" : "REJEITADO");
        log.info("Camada '{}': {} lida(s), {} aprovada(s), {} rejeitada(s).",
                alvo.rotulo(), contagem.total, contagem.aprovados, contagem.rejeitados);
    }

    // ids das camadas escolhidas na tela, quando houver
    private List<Long> idsEscolhidos(ContextoExecucao ctx) {
        String bruto = ctx.getParametros().get("camadasSelecionadas");
        if (bruto == null || bruto.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String parte : bruto.split(",")) {
            try {
                ids.add(Long.parseLong(parte.trim()));
            } catch (NumberFormatException ignorado) {
                log.warn("Id de camada invalido em camadasSelecionadas: '{}'", parte);
            }
        }
        return ids;
    }

    private Long exigir(Long valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException("A etapa VALIDACAO precisa de " + nome + ".");
        }
        return valor;
    }

    // contador mutavel, porque o leitor entrega as feicoes por Consumer
    private static class Contagem {
        private long total;
        private long aprovados;
        private long rejeitados;

        void somar(Contagem outra) {
            total += outra.total;
            aprovados += outra.aprovados;
            rejeitados += outra.rejeitados;
        }
    }
}
