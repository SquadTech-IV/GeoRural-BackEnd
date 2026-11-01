package br.com.fatec.georural.service.pipeline.inventario;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import br.com.fatec.georural.service.pipeline.formato.DetectorFormato;
import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import br.com.fatec.georural.service.pipeline.formato.FormatoDetectado;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Slf4j
@Service
@RequiredArgsConstructor
public class InventarioArquivoService {

    /** Ate onde descer em zip dentro de zip. */
    private static final int PROFUNDIDADE_MAXIMA = 3;
    /** Acima disso, o zip aninhado e registrado mas nao aberto, para nao estourar memoria. */
    private static final long LIMITE_ZIP_ANINHADO_BYTES = 200L * 1024 * 1024;

    private final ArquivoBrutoRepository arquivoRepository;
    private final MovimentacaoZonaRepository movimentacaoRepository;
    private final ArmazenamentoZonaPort armazenamento;
    private final DetectorFormato detectorFormato;

    @Transactional
    public List<ArquivoBruto> inventariar(ArquivoBruto pai) {
        if (pai.getPai() != null) {
            log.debug("Arquivo {} ja e membro de outro; nao e inventariado de novo.", pai.getId());
            return List.of();
        }

        byte[] conteudo = lerDaZonaBruta(pai);
        FormatoDetectado formatoPai = detectorFormato.detectar(conteudo, pai.getNome());

        if (!formatoPai.formato().container()) {
            log.debug("Arquivo {} ({}) nao e container; nada a inventariar.",
                    pai.getId(), formatoPai.formato());
            return List.of();
        }

        List<MembroArquivo> membros = descobrir(conteudo, "", 1);
        log.info("Arquivo {} ('{}'): {} conjunto(s) de dados encontrado(s) dentro.",
                pai.getId(), pai.getNome(), membros.size());

        List<ArquivoBruto> criados = new ArrayList<>();
        for (MembroArquivo membro : membros) {
            registrar(pai, membro).ifPresent(criados::add);
        }
        return criados;
    }

    // ---------------- descoberta ----------------

    /**
     * Percorre o zip e agrupa as entradas por nome base, para um shapefile de
     * quatro arquivos virar UM conjunto de dados, e nao quatro.
     */
    private List<MembroArquivo> descobrir(byte[] zip, String prefixo, int profundidade) {
        Map<String, List<EntradaZip>> porBase = new LinkedHashMap<>();
        List<MembroArquivo> aninhados = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entrada;
            while ((entrada = zis.getNextEntry()) != null) {
                if (entrada.isDirectory()) {
                    continue;
                }
                String nome = entrada.getName();
                String extensao = extensaoDe(nome);
                byte[] bytes = zis.readAllBytes();

                if ("zip".equals(extensao)) {
                    aninhados.addAll(tratarAninhado(bytes, prefixo + nome, profundidade));
                    continue;
                }
                if (!FormatoArquivo.EXTENSOES_PRINCIPAIS.contains(extensao)
                        && !FormatoArquivo.EXTENSOES_AUXILIARES_SHAPEFILE.contains(extensao)) {
                    continue; // leia-me, pdf, etc: nao e dado
                }
                porBase.computeIfAbsent(baseDe(nome), k -> new ArrayList<>())
                       .add(new EntradaZip(nome, extensao, bytes));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao abrir o arquivo compactado: " + e.getMessage(), e);
        }

        List<MembroArquivo> membros = new ArrayList<>();
        for (Map.Entry<String, List<EntradaZip>> grupo : porBase.entrySet()) {
            montar(grupo.getValue(), prefixo).ifPresent(membros::add);
        }
        membros.addAll(aninhados);
        return membros;
    }

    private List<MembroArquivo> tratarAninhado(byte[] bytes, String caminho, int profundidade) {
        if (profundidade >= PROFUNDIDADE_MAXIMA) {
            log.warn("Zip aninhado '{}' ignorado: profundidade maxima ({}) atingida.",
                    caminho, PROFUNDIDADE_MAXIMA);
            return List.of();
        }
        if (bytes.length > LIMITE_ZIP_ANINHADO_BYTES) {
            log.warn("Zip aninhado '{}' ({} bytes) nao foi aberto: acima do limite.", caminho, bytes.length);
            return List.of();
        }
        return descobrir(bytes, caminho + "!/", profundidade + 1);
    }

    /** Um grupo vira membro quando tem um arquivo principal (.shp, .csv, .geojson...). */
    private Optional<MembroArquivo> montar(List<EntradaZip> grupo, String prefixo) {
        EntradaZip principal = grupo.stream()
                .filter(e -> FormatoArquivo.EXTENSOES_PRINCIPAIS.contains(e.extensao()))
                .findFirst()
                .orElse(null);
        if (principal == null) {
            return Optional.empty(); // so .dbf/.prj soltos: nao e conjunto de dados
        }

        FormatoDetectado detectado = detectorFormato.detectar(principal.bytes(), principal.nome());
        Integer srs = detectado.srsEpsg();
        String observacao = detectado.observacao();

        // Shapefile dentro de zip: o .prj e irmao, nao esta dentro do .shp.
        if (srs == null) {
            EntradaZip prj = grupo.stream().filter(e -> "prj".equals(e.extensao())).findFirst().orElse(null);
            if (prj != null) {
                srs = detectorFormato.epsgDoWkt(new String(prj.bytes(), java.nio.charset.StandardCharsets.UTF_8));
            } else if (detectado.formato() == FormatoArquivo.SHAPEFILE) {
                observacao = "Shapefile sem .prj: o sistema de referencia sera o do cadastro do conjunto.";
            }
        }

        long tamanho = grupo.stream().mapToLong(e -> e.bytes().length).sum();

        return Optional.of(new MembroArquivo(
                prefixo + principal.nome(),
                nomeSimples(principal.nome()),
                detectado.formato(),
                sha256(principal.bytes()),
                tamanho,
                srs,
                grupo.stream().map(EntradaZip::nome).sorted().toList(),
                observacao));
    }

    // ---------------- registro ----------------

    /** Cria o registro do membro. Se ja existir (retry), nao duplica. */
    private Optional<ArquivoBruto> registrar(ArquivoBruto pai, MembroArquivo membro) {
        Long conjuntoId = pai.getConjunto().getId();

        Optional<ArquivoBruto> existente = arquivoRepository
                .buscarPorIdentidade(conjuntoId, pai.getCompetencia(), membro.hash());
        if (existente.isPresent()) {
            log.debug("Membro '{}' ja registrado (id={}).", membro.caminhoInterno(), existente.get().getId());
            return Optional.empty();
        }

        ArquivoBruto filho = new ArquivoBruto();
        filho.setConjunto(pai.getConjunto());
        filho.setUsuario(pai.getUsuario());
        filho.setNome(membro.nome());
        filho.setFormato(pai.getFormato());
        filho.setFormatoDetectado(membro.formato().name());
        // Mesmo objeto do pai: a zona bruta e imutavel e nada e regravado.
        filho.setPathArmazenamento(pai.getPathArmazenamento());
        filho.setCaminhoInterno(membro.caminhoInterno());
        filho.setPai(pai);
        filho.setTamanhoBytes(membro.tamanhoBytes());
        filho.setHash(membro.hash());
        filho.setAlgoritmoHash("SHA-256");
        filho.setCanalEntrada(pai.getCanalEntrada());
        filho.setCompetencia(pai.getCompetencia());
        filho.setSrsDeclarado(membro.srsEpsg());
        filho.setSrsUtilizado(membro.srsEpsg() != null
                ? membro.srsEpsg()
                : (pai.getConjunto().getSrs() != null ? pai.getConjunto().getSrs() : 4674));
        filho.setStatus("RECEBIDO");
        filho.setDataUpload(LocalDateTime.now());

        ArquivoBruto salvo = arquivoRepository.save(filho);

        MovimentacaoZona mov = new MovimentacaoZona();
        mov.setArquivoBruto(salvo);
        mov.setZona("BRUTA");
        mov.setStatus("RECEBIDO");
        mov.setObservacao("Encontrado dentro de '" + pai.getNome() + "' em " + membro.caminhoInterno()
                + (membro.temVariasPartes() ? " (" + membro.partes().size() + " partes)" : ""));
        mov.setData(LocalDateTime.now());
        movimentacaoRepository.save(mov);

        if (membro.observacao() != null) {
            log.info("Membro '{}': {}", membro.caminhoInterno(), membro.observacao());
        }
        return Optional.of(salvo);
    }

    // ---------------- apoio ----------------

    private byte[] lerDaZonaBruta(ArquivoBruto arquivo) {
        try (InputStream in = armazenamento.ler(Zona.BRUTA, arquivo.getPathArmazenamento())) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao ler o arquivo da zona bruta: " + arquivo.getPathArmazenamento(), e);
        }
    }

    private String extensaoDe(String nome) {
        int p = nome.lastIndexOf('.');
        if (p < 0) return "";
        String ext = nome.substring(p + 1).toLowerCase(Locale.ROOT);
        return "tiff".equals(ext) ? "tif" : ext;
    }

    private String baseDe(String nome) {
        int p = nome.lastIndexOf('.');
        return p < 0 ? nome : nome.substring(0, p);
    }

    private String nomeSimples(String caminho) {
        int barra = Math.max(caminho.lastIndexOf('/'), caminho.lastIndexOf('\\'));
        return barra < 0 ? caminho : caminho.substring(barra + 1);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(d.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel.", e);
        }
    }

    private record EntradaZip(String nome, String extensao, byte[] bytes) { }
}
