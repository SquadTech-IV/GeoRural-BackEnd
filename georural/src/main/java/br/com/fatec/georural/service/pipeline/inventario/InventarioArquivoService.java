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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// cataloga o que vem dentro de um arquivo container, em fluxo
@Slf4j
@Service
@RequiredArgsConstructor
public class InventarioArquivoService {

    private static final int PROFUNDIDADE_MAXIMA = 3;

    // cabecalho suficiente para assinatura, header de SQLite e farejar GeoJSON
    private static final int BYTES_CABECALHO = 4096;

    // zip aninhado maior que isso nao e aberto; protecao contra zip bomba
    private static final long LIMITE_ZIP_ANINHADO_BYTES = 4L * 1024 * 1024 * 1024;

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

        if (!formatoDoPai(pai).container()) {
            log.debug("Arquivo {} nao e container; nada a inventariar.", pai.getId());
            return List.of();
        }

        List<MembroArquivo> membros = new ArrayList<>();
        descobrir(() -> abrirZonaBruta(pai), "", 1, membros);

        log.info("Arquivo {} ('{}'): {} conjunto(s) de dados encontrado(s) dentro.",
                pai.getId(), pai.getNome(), membros.size());

        List<ArquivoBruto> criados = new ArrayList<>();
        for (MembroArquivo membro : membros) {
            registrar(pai, membro).ifPresent(criados::add);
        }
        return criados;
    }

    // ---------------- descoberta ----------------

    // percorre o zip em fluxo, sem guardar o conteudo das entradas
    private void descobrir(Supplier<InputStream> fonte, String prefixo, int profundidade,
                           List<MembroArquivo> saida) {
        Map<String, List<EntradaZipLida>> porBase = new LinkedHashMap<>();

        try (InputStream entradaBruta = fonte.get();
             ZipInputStream zis = new ZipInputStream(entradaBruta)) {

            ZipEntry entrada;
            while ((entrada = zis.getNextEntry()) != null) {
                if (entrada.isDirectory()) {
                    continue;
                }

                String nome = entrada.getName();
                String extensao = extensaoDe(nome);

                if ("zip".equals(extensao)) {
                    tratarAninhado(zis, prefixo + nome, profundidade, saida);
                    continue;
                }

                if (!FormatoArquivo.EXTENSOES_PRINCIPAIS.contains(extensao)
                        && !FormatoArquivo.EXTENSOES_AUXILIARES_SHAPEFILE.contains(extensao)) {
                    continue; // leia-me, pdf, etc: nao e dado
                }

                boolean guardarTudo = "prj".equals(extensao) || "cpg".equals(extensao);
                porBase.computeIfAbsent(baseDe(nome), k -> new ArrayList<>())
                       .add(ler(zis, nome, extensao, guardarTudo));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao abrir o arquivo compactado: " + e.getMessage(), e);
        }

        for (List<EntradaZipLida> grupo : porBase.values()) {
            montar(grupo, prefixo).ifPresent(saida::add);
        }
    }

    // le uma entrada do zip calculando hash e tamanho sem guardar o conteudo
    private EntradaZipLida ler(ZipInputStream zis, String nome, String extensao, boolean guardarTudo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] cabecalho = new byte[BYTES_CABECALHO];
            int lidosCabecalho = 0;
            long total = 0;

            byte[] buffer = new byte[64 * 1024];
            java.io.ByteArrayOutputStream completo =
                    guardarTudo ? new java.io.ByteArrayOutputStream() : null;

            int lidos;
            while ((lidos = zis.read(buffer)) != -1) {
                digest.update(buffer, 0, lidos);
                total += lidos;

                if (lidosCabecalho < BYTES_CABECALHO) {
                    int cabe = Math.min(lidos, BYTES_CABECALHO - lidosCabecalho);
                    System.arraycopy(buffer, 0, cabecalho, lidosCabecalho, cabe);
                    lidosCabecalho += cabe;
                }
                if (completo != null) {
                    completo.write(buffer, 0, lidos);
                }
            }

            byte[] recorte = lidosCabecalho == BYTES_CABECALHO
                    ? cabecalho
                    : java.util.Arrays.copyOf(cabecalho, lidosCabecalho);

            return new EntradaZipLida(nome, extensao, total, hexa(digest.digest()), recorte,
                    completo == null ? null : completo.toByteArray());

        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler '" + nome + "' do zip: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM.", e);
        }
    }

    // zip dentro de zip: vai para o disco temporario e e aberto de la
    private void tratarAninhado(ZipInputStream zis, String caminho, int profundidade,
                                List<MembroArquivo> saida) {
        if (profundidade >= PROFUNDIDADE_MAXIMA) {
            log.warn("Zip aninhado '{}' ignorado: profundidade maxima ({}) atingida.",
                    caminho, PROFUNDIDADE_MAXIMA);
            saida.add(MembroArquivo.naoAberto(caminho, nomeSimples(caminho),
                    "Pacote aninhado em " + (profundidade + 1) + "o nivel: acima do limite de "
                            + PROFUNDIDADE_MAXIMA + " niveis, nao foi aberto."));
            return;
        }

        Path temporario;
        long tamanho;
        try {
            temporario = Files.createTempFile("georural-aninhado-", ".zip");
            tamanho = Files.copy(zis, temporario, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao extrair o pacote aninhado '" + caminho + "': " + e.getMessage(), e);
        }

        try {
            if (tamanho > LIMITE_ZIP_ANINHADO_BYTES) {
                log.warn("Zip aninhado '{}' ({} bytes) nao foi aberto: acima do limite.",
                        caminho, tamanho);
                saida.add(MembroArquivo.naoAberto(caminho, nomeSimples(caminho),
                        "Pacote aninhado de " + tamanho + " bytes: acima do limite de "
                                + LIMITE_ZIP_ANINHADO_BYTES + " bytes, nao foi aberto."));
                return;
            }
            descobrir(() -> abrirArquivo(temporario), caminho + "!/", profundidade + 1, saida);
        } finally {
            try {
                Files.deleteIfExists(temporario);
            } catch (IOException ignorado) {
                // temporario: falha na limpeza nao afeta o inventario
            }
        }
    }

    // um grupo vira membro quando tem um arquivo principal
    private Optional<MembroArquivo> montar(List<EntradaZipLida> grupo, String prefixo) {
        EntradaZipLida principal = grupo.stream()
                .filter(e -> FormatoArquivo.EXTENSOES_PRINCIPAIS.contains(e.extensao()))
                .findFirst()
                .orElse(null);
        if (principal == null) {
            return Optional.empty(); // so .dbf/.prj soltos: nao e conjunto de dados
        }

        FormatoDetectado detectado = detectorFormato.detectar(principal.cabecalho(), principal.nome());
        Integer srs = detectado.srsEpsg();
        String observacao = detectado.observacao();

        // Shapefile dentro de zip: o .prj e irmao, nao esta dentro do .shp.
        if (srs == null) {
            EntradaZipLida prj = grupo.stream()
                    .filter(e -> "prj".equals(e.extensao()) && e.conteudo() != null)
                    .findFirst().orElse(null);
            if (prj != null) {
                srs = detectorFormato.epsgDoWkt(new String(prj.conteudo(), StandardCharsets.UTF_8));
            } else if (detectado.formato() == FormatoArquivo.SHAPEFILE) {
                observacao = "Shapefile sem .prj: o sistema de referencia sera o do cadastro do conjunto.";
            }
        }

        long tamanho = grupo.stream().mapToLong(EntradaZipLida::tamanho).sum();

        return Optional.of(MembroArquivo.lido(
                prefixo + principal.nome(),
                nomeSimples(principal.nome()),
                detectado.formato(),
                principal.hash(),
                tamanho,
                srs,
                grupo.stream().map(EntradaZipLida::nome).sorted().toList(),
                observacao));
    }

    // ---------------- registro ----------------

    // cria o registro do membro
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
        filho.setStatus(membro.aberto() ? "RECEBIDO" : "FALHA");
        filho.setDataUpload(LocalDateTime.now());

        ArquivoBruto salvo = arquivoRepository.save(filho);

        MovimentacaoZona mov = new MovimentacaoZona();
        mov.setArquivoBruto(salvo);
        mov.setZona("BRUTA");
        mov.setStatus(membro.aberto() ? "RECEBIDO" : "FALHA");
        mov.setObservacao(cortar("Encontrado dentro de '" + pai.getNome() + "' em "
                + membro.caminhoInterno()
                + (membro.temVariasPartes() ? " (" + membro.partes().size() + " partes)" : "")
                + (membro.observacao() != null ? " | " + membro.observacao() : ""), 400));
        mov.setData(LocalDateTime.now());
        movimentacaoRepository.save(mov);

        if (membro.observacao() != null) {
            log.info("Membro '{}': {}", membro.caminhoInterno(), membro.observacao());
        }
        return Optional.of(salvo);
    }

    // ---------------- apoio ----------------

    // formato do pai lendo so o cabecalho, nunca o objeto inteiro
    private FormatoArquivo formatoDoPai(ArquivoBruto arquivo) {
        try (InputStream in = abrirZonaBruta(arquivo)) {
            byte[] cabecalho = in.readNBytes(BYTES_CABECALHO);
            return detectorFormato.detectar(cabecalho, arquivo.getNome()).formato();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao ler o arquivo da zona bruta: " + arquivo.getPathArmazenamento(), e);
        }
    }

    private InputStream abrirZonaBruta(ArquivoBruto arquivo) {
        return armazenamento.ler(Zona.BRUTA, arquivo.getPathArmazenamento());
    }

    private InputStream abrirArquivo(Path arquivo) {
        try {
            return Files.newInputStream(arquivo);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao abrir arquivo temporario: " + arquivo, e);
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
        String simples = barra < 0 ? caminho : caminho.substring(barra + 1);
        int marca = simples.lastIndexOf("!/");
        return marca < 0 ? simples : simples.substring(marca + 2);
    }

    private String hexa(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            texto.append(String.format("%02x", b));
        }
        return texto.toString();
    }

    private String cortar(String texto, int max) {
        return texto == null || texto.length() <= max ? texto : texto.substring(0, max - 3) + "...";
    }

    // uma entrada do zip ja lida: cabecalho, tamanho e hash, sem o conteudo
    private record EntradaZipLida(
            String nome,
            String extensao,
            long tamanho,
            String hash,
            byte[] cabecalho,
            byte[] conteudo) {
    }
}
