package br.com.fatec.georural.service.monitoramento;

// um componente que sabe dizer se esta no ar.
public interface VerificadorComponente {

    String nome();

    ResultadoVerificacao verificar();
}
