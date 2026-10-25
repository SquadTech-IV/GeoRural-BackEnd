package br.com.fatec.georural.service.storage;

/**
 * Situações de {@code arq_status} usadas pelo recebimento de arquivos (US#07).
 *
 * <p>As demais situações da tabela (VALIDANDO, APROVADO, REJEITADO, PROCESSADO) pertencem a fluxos posteriores.
 */
public final class SituacaoArquivoBruto {

    /** Registro criado e vaga reservada; o objeto ainda pode não existir na zona bruta. */
    public static final String RECEBENDO = "RECEBENDO";

    /** Objeto gravado na zona bruta e registro concluído. */
    public static final String RECEBIDO = "RECEBIDO";

    /** Envio que não chegou à zona bruta. Não conta para a unicidade de (conjunto, hash). */
    public static final String FALHA = "FALHA";

    private SituacaoArquivoBruto() {
    }
}