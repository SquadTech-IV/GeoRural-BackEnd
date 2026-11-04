package br.com.fatec.georural.dto.request.versao;

// motivo obrigatorio: reverter uma publicacao precisa ficar registrado
public record ReversaoRequest(String motivo) {
}
