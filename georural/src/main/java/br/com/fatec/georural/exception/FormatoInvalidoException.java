package br.com.fatec.georural.exception;

// extensao nao aceita o conteudo que nao confere com o formato declarado.
// Estende RequisicaoInvalidaException: continua virando HTTP 400 no GlobalExceptionHandler,
// e no envio em lote permite separar "formato errado" de "recusado por regra de cadastro".
public class FormatoInvalidoException extends RequisicaoInvalidaException {

    public FormatoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
