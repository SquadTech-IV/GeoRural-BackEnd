package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.regra.CriarVersaoRegraRequest;
import br.com.fatec.georural.dto.request.regra.ParametroInput;
import br.com.fatec.georural.dto.response.regra.RegraCalculoResponse;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ParametroRegraCalculo;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ParametroRegraCalculoRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;

import br.com.fatec.georural.service.auditoria.SessaoOracle;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.security.UsuarioAutenticado;
import br.com.fatec.georural.security.UsuarioAutenticadoProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RegraCalculoService {

    private final RegraCalculoRepository regraCalculoRepository;
    private final ParametroRegraCalculoRepository parametroRegraCalculoRepository;
    private final IndicadorService indicadorService;
    private final ParametroRegraValidador parametroRegraValidador;
    private final SessaoOracle sessaoOracle;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final UsuarioRepository usuarioRepository;

    public RegraCalculoService(RegraCalculoRepository regraCalculoRepository,
                               ParametroRegraCalculoRepository parametroRegraCalculoRepository,
                               IndicadorService indicadorService,
                               ParametroRegraValidador parametroRegraValidador,
                               SessaoOracle sessaoOracle,
                               UsuarioAutenticadoProvider usuarioAutenticadoProvider,
                               UsuarioRepository usuarioRepository) {
        this.regraCalculoRepository = regraCalculoRepository;
        this.parametroRegraCalculoRepository = parametroRegraCalculoRepository;
        this.indicadorService = indicadorService;
        this.parametroRegraValidador = parametroRegraValidador;
        this.sessaoOracle = sessaoOracle;
        this.usuarioAutenticadoProvider = usuarioAutenticadoProvider;
        this.usuarioRepository = usuarioRepository;
    }


    /** Regra vigente do indicador. */
    @Transactional(readOnly = true)
    public RegraCalculoResponse buscarVigente(String sigla) {
        Indicador indicador = indicadorService.buscarEntidade(sigla); // 404 se a sigla nao existir
        RegraCalculo vigente = regraCalculoRepository
                .findByIndicadorAndVigente(indicador, "S")
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhuma regra vigente para o indicador " + sigla + "."));
        return RegraCalculoResponse.de(vigente, parametrosDe(vigente));
    }

    /** Historico completo do indicador. */
    @Transactional(readOnly = true)
    public List<RegraCalculoResponse> listarHistorico(String sigla) {
        Indicador indicador = indicadorService.buscarEntidade(sigla); // 404 se a sigla nao existir
        return regraCalculoRepository.findByIndicadorOrderByDataCriacaoDesc(indicador).stream()
                .map(regra -> RegraCalculoResponse.de(regra, parametrosDe(regra)))
                .toList();
    }

    /** Versao especifica do indicador */
    @Transactional(readOnly = true)
    public RegraCalculoResponse buscarVersao(String sigla, String versao) {
        Indicador indicador = indicadorService.buscarEntidade(sigla); // 404 se a sigla nao existir
        String versaoNormalizada = normalizarVersao(versao);
        RegraCalculo regra = regraCalculoRepository
                .findByIndicadorAndVersao(indicador, versaoNormalizada)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Versao " + versao + " nao encontrada para o indicador " + sigla + "."));
        return RegraCalculoResponse.de(regra, parametrosDe(regra));
    }


    // O autor vem de quem esta logado, nunca do que o cliente mandou. Antes o nome
    // era digitado na tela e gravado como veio, o que permitia assinar a alteracao
    // de uma regra com o nome de outra pessoa. O campo autor do request ficou sem
    // uso de proposito: mesmo que um cliente antigo mande, e descartado.
    private String autorDaAlteracao() {
        String nome = usuarioAutenticadoProvider.obterOpcional()
                .map(UsuarioAutenticado::usuId)
                .flatMap(usuarioRepository::findById)
                .map(Usuario::getNome)
                .filter(valor -> valor != null && !valor.isBlank())
                .orElse(null);

        if (nome == null) {
            throw new RequisicaoInvalidaException(
                    "Nao foi possivel identificar o usuario logado para registrar o autor"
                            + " da alteracao.");
        }

        return nome.trim();
    }

    @Transactional
    public RegraCalculoResponse criarNovaVersao(String sigla, CriarVersaoRegraRequest request) {
        // diz ao Oracle quem esta logado, para o gatilho de auditoria gravar o autor
        sessaoOracle.identificarUsuario();


        if (isBlank(request.motivo())) {
            throw new RequisicaoInvalidaException("O motivo e obrigatorio para criar uma nova versao.");
        }

        parametroRegraValidador.validar(request.parametros());

        Indicador indicador = indicadorService.buscarEntidade(sigla); // 404 se a sigla nao existir

        RegraCalculo atual = regraCalculoRepository
                .findByIndicadorAndVigente(indicador, "S")
                .orElse(null);

        if (atual != null && semAlteracao(request, atual)) {
            throw new RequisicaoInvalidaException(
                    "Nenhuma alteracao em relacao a versao vigente; a nova versao so e criada se algum valor mudar.");
        }

        if (atual != null) {
            // UPDATE so em rca_vigente: o save do JPA mandaria todas as colunas e o
            // trigger trg_regra_calculo_imutavel dispara em UPDATE OF rca_formula/rca_versao,
            // pela coluna estar no SET e nao pelo valor mudar
            regraCalculoRepository.marcarVigencia(atual.getId(), "N");
        }

        long vigentes = regraCalculoRepository.countByIndicadorAndVigente(indicador, "S");
        if (vigentes > 0) {
            throw new EstadoInconsistenteException(
                    "Ja existe regra vigente para o indicador " + sigla + "; estado inconsistente.");
        }

        long qtdVersoes = regraCalculoRepository.countByIndicador(indicador);
        String novoNumero = "v" + (qtdVersoes + 1);

        RegraCalculo nova = new RegraCalculo();
        nova.setIndicador(indicador);
        nova.setVersao(novoNumero);
        nova.setFormula(resolverFormula(request, atual));
        nova.setDescricao(request.descricao() != null ? request.descricao()
                : (atual != null ? atual.getDescricao() : null));
        nova.setAutor(autorDaAlteracao());
        nova.setMotivo(request.motivo().trim());
        nova.setVigente("S");
        nova.setDataCriacao(LocalDateTime.now());
        RegraCalculo salva = regraCalculoRepository.save(nova);

        // rotulo, editavel, minimo e maximo vem do parametro de mesmo nome da versao
        // anterior; sem isso a regra nova perderia o que a tela usa para editar
        Map<String, ParametroRegraCalculo> anteriores = new HashMap<>();
        if (atual != null) {
            for (ParametroRegraCalculo anterior : parametrosDe(atual)) {
                anteriores.put(anterior.getNome(), anterior);
            }
        }

        List<ParametroRegraCalculo> parametros = new ArrayList<>();
        if (request.parametros() != null) {
            for (ParametroInput entrada : request.parametros()) {
                ParametroRegraCalculo parametro = new ParametroRegraCalculo();
                parametro.setRegraCalculo(salva);
                parametro.setNome(entrada.nome().trim());
                parametro.setTipo(entrada.tipo().trim().toUpperCase());
                parametro.setUnidade(entrada.unidade());
                parametro.setValor(entrada.valor().trim());

                ParametroRegraCalculo base = anteriores.get(entrada.nome().trim());
                if (base != null) {
                    parametro.setRotulo(base.getRotulo());
                    parametro.setEditavel(base.getEditavel());
                    parametro.setMinimo(base.getMinimo());
                    parametro.setMaximo(base.getMaximo());
                }
                parametros.add(parametro);
            }
            parametroRegraCalculoRepository.saveAll(parametros);
        }

        return RegraCalculoResponse.de(salva, parametros);
    }

    private List<ParametroRegraCalculo> parametrosDe(RegraCalculo regra) {
        return parametroRegraCalculoRepository.findByRegraCalculo(regra);
    }

    private String normalizarVersao(String versao) {
        if (versao == null) {
            return null;
        }
        String v = versao.trim().toLowerCase();
        if (v.matches("\\d+")) {
            v = "v" + v;
        }
        return v;
    }

    private boolean semAlteracao(CriarVersaoRegraRequest request, RegraCalculo atual) {
        String novaFormula = resolverFormula(request, atual);
        String novaDescricao = request.descricao() != null ? request.descricao() : atual.getDescricao();
        if (!Objects.equals(novaFormula, atual.getFormula())) {
            return false;
        }
        if (!Objects.equals(novaDescricao, atual.getDescricao())) {
            return false;
        }
        Map<String, String> novos = mapaDosInputs(request.parametros());
        Map<String, String> atuais = mapaDasEntidades(parametrosDe(atual));
        return novos.equals(atuais);
    }

    private Map<String, String> mapaDosInputs(List<ParametroInput> inputs) {
        Map<String, String> mapa = new HashMap<>();
        if (inputs != null) {
            for (ParametroInput p : inputs) {
                mapa.put(p.nome().trim(), p.valor().trim());
            }
        }
        return mapa;
    }

    private Map<String, String> mapaDasEntidades(List<ParametroRegraCalculo> params) {
        Map<String, String> mapa = new HashMap<>();
        for (ParametroRegraCalculo p : params) {
            mapa.put(p.getNome(), p.getValor());
        }
        return mapa;
    }

    private String resolverFormula(CriarVersaoRegraRequest request, RegraCalculo atual) {
        if (request.formula() != null) {
            if (request.formula().isBlank()) {
                throw new RequisicaoInvalidaException("A formula nao pode ser vazia.");
            }
            return request.formula().trim();
        }
        if (atual != null) {
            return atual.getFormula();
        }
        throw new RequisicaoInvalidaException(
                "A formula e obrigatoria quando o indicador ainda nao tem regra vigente.");
    }

    private boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}