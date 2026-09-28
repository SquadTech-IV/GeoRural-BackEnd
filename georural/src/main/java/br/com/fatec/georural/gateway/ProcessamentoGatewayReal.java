package br.com.fatec.georural.gateway;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;
import br.com.fatec.georural.entity.ArquivoDatalakeConteudo;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ArquivoDatalakeConteudoRepository;
import br.com.fatec.georural.service.treatment.DetectorTipoCamada;
import br.com.fatec.georural.service.treatment.DetectorTipoCamada.TipoCamada;
import br.com.fatec.georural.service.treatment.embargo.Embargo;
import br.com.fatec.georural.service.treatment.property_area.PropertyArea;

@Component
@Primary
public class ProcessamentoGatewayReal implements ProcessamentoGateway {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoGatewayReal.class);

    private final ArquivoDatalakeConteudoRepository conteudoRepository;
    private final DetectorTipoCamada detectorTipoCamada;

    public ProcessamentoGatewayReal(ArquivoDatalakeConteudoRepository conteudoRepository,
                                    DetectorTipoCamada detectorTipoCamada) {
        this.conteudoRepository = conteudoRepository;
        this.detectorTipoCamada = detectorTipoCamada;
    }

    @Override
    public void enviarParaProcessamento(EnvioProcessamentoResponse e) {
        log.info("[PROCESSAMENTO] Iniciando arquivo id={} ({})", e.id(), e.nomeArquivo());

        // 1) pega o binario salvo no banco
        ArquivoDatalakeConteudo conteudo = conteudoRepository.findById(e.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Conteudo nao disponivel para o arquivo: " + e.id()));

        Path pastaTemp = null;
        try {
            // 2) grava o binario numa pasta temporaria
            pastaTemp = Files.createTempDirectory("georural_proc_" + e.id() + "_");
            File shapefile = materializarShapefile(conteudo, e.nomeArquivo(), pastaTemp);

            // 3) descobre o tipo olhando as colunas do shapefile
            TipoCamada tipo = detectorTipoCamada.detectar(shapefile);
            log.info("[PROCESSAMENTO] Tipo detectado: {}", tipo);

            // 4) dispara a pipeline correta
            switch (tipo) {
                case EMBARGO -> new Embargo(shapefile).execute();
                case IMOVEL  -> new PropertyArea(shapefile).execute();
            }

            log.info("[PROCESSAMENTO] Concluido para o arquivo id={}", e.id());

        } catch (IOException ex) {
            throw new RuntimeException(
                    "Erro ao preparar o arquivo para processamento: " + ex.getMessage(), ex);
        } finally {
            // 5) limpa os arquivos temporarios
            apagarPasta(pastaTemp);
        }
    }

    private File materializarShapefile(ArquivoDatalakeConteudo conteudo, String nomeArquivo,
                                       Path pastaTemp) throws IOException {
        boolean ehZip = nomeArquivo != null && nomeArquivo.toLowerCase().endsWith(".zip");

        if (ehZip) {
            descompactar(conteudo.getConteudo(), pastaTemp);
            return encontrarShp(pastaTemp);
        }

        Path destino = pastaTemp.resolve(nomeArquivo);
        Files.write(destino, conteudo.getConteudo());
        File arquivo = destino.toFile();

        if (!arquivo.getName().toLowerCase().endsWith(".shp")) {
            throw new ConflitoException(
                    "Arquivo '" + nomeArquivo + "' nao e um shapefile (.shp) nem um .zip. "
                            + "O processamento atual so aceita shapefile.");
        }
        return arquivo;
    }

    private void descompactar(byte[] zipBytes, Path destino) throws IOException {
        descompactarRecursivo(new ByteArrayInputStream(zipBytes), destino);
    }

    private void descompactarRecursivo(InputStream entradaZip, Path destino) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(entradaZip)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String nome = new File(entry.getName()).getName(); // ignora subpastas do zip
                if (nome.isBlank()) {
                    continue;
                }

                if (nome.toLowerCase().endsWith(".zip")) {
                    // zip dentro de zip: le os bytes e abre recursivamente
                    byte[] zipInterno = zis.readAllBytes();
                    descompactarRecursivo(new ByteArrayInputStream(zipInterno), destino);
                } else {
                    // arquivo normal (.shp, .shx, .dbf, .prj...): grava no destino
                    Path saida = destino.resolve(nome);
                    Files.copy(zis, saida, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private File encontrarShp(Path pasta) throws IOException {
        try (var stream = Files.list(pasta)) {
            List<Path> shps = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".shp"))
                    .toList();

            if (shps.isEmpty()) {
                throw new ConflitoException("Nenhum arquivo .shp encontrado dentro do zip.");
            }


            return shps.stream()
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.contains("imovel");
                    })
                    .findFirst()
                    .orElse(shps.get(0))
                    .toFile();
        }
    }
    private void apagarPasta(Path pasta) {
        if (pasta == null) {
            return;
        }
        try (var stream = Files.walk(pasta)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException ex) {
            log.warn("Nao foi possivel apagar a pasta temporaria {}: {}", pasta, ex.getMessage());
        }
    }
}