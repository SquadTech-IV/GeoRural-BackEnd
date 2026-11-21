package br.com.fatec.georural.service.monitoramento;

// estado de um componente da plataforma
public enum EstadoComponente {

    NO_AR("No ar"),
    // responde, mas fora do que se espera (lento, ou com aviso)
    DEGRADADO("Degradado"),
    FORA("Fora do ar"),
    // nao da para dizer: falta configuracao para alcancar o componente
    NAO_CONFIGURADO("Nao configurado");

    private final String rotulo;

    EstadoComponente(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    public boolean saudavel() {
        return this == NO_AR;
    }
}
