package br.com.fatec.georural.service.treatment.storage;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

// ASSUNÇÃO: nomes baseados no código comentado do seu SavingState.
// Ajuste o pacote/campos se a entidade for diferente.
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import lombok.RequiredArgsConstructor;

/**
 * Grava a saída tratada (aprovada, padronizada e recortada) na zona tratada,
 * calcula o SHA-256 do arquivo gerado, registra a transformação e a
 * movimentação de zona (US09.03).
 */
@Service
@RequiredArgsConstructor
public class ZonaTratadaService {

    private final ZonaTratadaStorage storage;
    private final MovimentacaoZonaRepository movimentacaoRepository;

    // Registra os módulos do classpath (inclui jackson-datatype-jsr310) para serializar datas Java 8
    // que possam vir nos atributos das feições.
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public record Resultado(String caminho, String hash) {
    }

    /**
     * @param tipo   "EMBARGO" ou "IMOVEL" (compõe o caminho do objeto)
     * @param linhas registros já limpos; geometria em WKT
     */
    public Resultado gravar(
            BaseTreatment ctx,
            String tipo,
            List<Map<String, Object>> linhas) {

        try {

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            for (Map<String, Object> linha : linhas) {
                out.write(mapper.writeValueAsBytes(linha));
                out.write('\n');
            }

            byte[] bytes = out.toByteArray();

            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));

            // ASSUNÇÃO: ExecucaoPipeline possui getId()
            String chave = "tratada/%s/v%d/exec-%s/saida.jsonl".formatted(
                    tipo.toLowerCase(),
                    ctx.getVersao().getNumero(),
                    ctx.getExecucaoPipeline().getId());

            String caminho = storage.gravar(chave, bytes);

            ctx.registrarTransformacao(
                    "GRAVACAO_ZONA_TRATADA",
                    linhas.size(),
                    linhas.size(),
                    "caminho=" + caminho + ", sha256=" + hash);

            // Ideal: colunas próprias (caminho, hash) na MOVIMENTACAO_ZONA.
            // Enquanto isso, vão na observação (confira o tamanho da coluna).
            MovimentacaoZona movimentacao = new MovimentacaoZona();
            movimentacao.setArquivoBruto(ctx.getArquivoBruto());
            movimentacao.setExecucao(ctx.getExecucaoPipeline());
            movimentacao.setZona("TRATADA");
            movimentacao.setStatus("CONCLUIDO");
            movimentacao.setObservacao(
                    "Dados tratados gravados | caminho=" + caminho
                            + " | sha256=" + hash);
            movimentacao.setData(LocalDateTime.now());
            movimentacaoRepository.save(movimentacao);

            return new Resultado(caminho, hash);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Erro ao gravar na zona tratada: " + e.getMessage(),
                    e);
        }
    }
}
