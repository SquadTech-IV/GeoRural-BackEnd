package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.indicador.ConsolidadoMunicipiosResponse.Faixa;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;


public final class FaixasDeComparacao {

    public static final int QUANTIDADE_PADRAO = 5;
    public static final int MINIMO_DE_FAIXAS = 2;
    public static final int MAXIMO_DE_FAIXAS = 9;

    private static final int ESCALA = 4;

    public enum Estrategia {
        /** Faixas de largura igual. Estavel entre versoes quando o indicador tem escala. */
        INTERVALO,
        /** Faixas com quantidade parecida de municipios, cortadas nos dados. */
        QUANTIL,
        /** Sem faixa. */
        NENHUMA;

        public static Estrategia de(String valor) {
            if (valor == null || valor.isBlank()) {
                return INTERVALO;
            }

            try {
                return valueOf(valor.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RequisicaoInvalidaException(
                        "faixas invalida: " + valor + ". Use INTERVALO, QUANTIL ou NENHUMA.");
            }
        }
    }

    /** As faixas e de onde a escala saiu. */
    public record Resultado(
            List<Faixa> faixas,
            BigDecimal escalaMin,
            BigDecimal escalaMax,
            String origemDaEscala,
            List<String> avisos) {

        /** Em qual faixa um valor cai. Null quando nao ha faixa ou valor. */
        public Integer faixaDe(BigDecimal valor) {
            if (valor == null || faixas.isEmpty()) {
                return null;
            }

            for (Faixa faixa : faixas) {
                if (valor.compareTo(faixa.ate()) < 0) {
                    return faixa.ordem();
                }
            }

            return faixas.get(faixas.size() - 1).ordem();
        }

        public String rotuloDe(BigDecimal valor) {
            Integer ordem = faixaDe(valor);

            if (ordem == null) {
                return null;
            }

            return faixas.stream()
                    .filter(f -> f.ordem() == ordem)
                    .map(Faixa::rotulo)
                    .findFirst()
                    .orElse(null);
        }
    }

    private FaixasDeComparacao() {
    }

    public static Resultado calcular(
            Estrategia estrategia,
            int quantidade,
            List<BigDecimal> valoresOrdenados,
            BigDecimal escalaMinDoIndicador,
            BigDecimal escalaMaxDoIndicador,
            SentidoIndicador sentido) {

        if (estrategia == Estrategia.NENHUMA) {
            return new Resultado(List.of(), null, null, "NENHUMA", List.of());
        }

        if (quantidade < MINIMO_DE_FAIXAS || quantidade > MAXIMO_DE_FAIXAS) {
            throw new RequisicaoInvalidaException(
                    "qtdFaixas tem que estar entre " + MINIMO_DE_FAIXAS
                            + " e " + MAXIMO_DE_FAIXAS + ".");
        }

        if (valoresOrdenados.isEmpty()) {
            return new Resultado(List.of(), null, null, "SEM_DADOS",
                    List.of("Nenhum municipio com valor: sem faixas para calcular."));
        }

        if (estrategia == Estrategia.QUANTIL) {
            return porQuantil(quantidade, valoresOrdenados, sentido);
        }

        return porIntervalo(quantidade, valoresOrdenados,
                escalaMinDoIndicador, escalaMaxDoIndicador, sentido);
    }

    private static Resultado porIntervalo(
            int quantidade,
            List<BigDecimal> valores,
            BigDecimal escalaMinDoIndicador,
            BigDecimal escalaMaxDoIndicador,
            SentidoIndicador sentido) {

        List<String> avisos = new ArrayList<>();
        BigDecimal minimo;
        BigDecimal maximo;
        String origem;

        boolean temEscalaCadastrada = escalaMinDoIndicador != null
                && escalaMaxDoIndicador != null
                && escalaMaxDoIndicador.compareTo(escalaMinDoIndicador) > 0;

        if (temEscalaCadastrada) {
            minimo = escalaMinDoIndicador;
            maximo = escalaMaxDoIndicador;
            origem = "INDICADOR";
        } else {
            minimo = valores.get(0);
            maximo = valores.get(valores.size() - 1);
            origem = "DADOS";
            avisos.add("O indicador nao tem escala cadastrada (ind_escala_min e "
                    + "ind_escala_max): as faixas sairam do minimo e do maximo "
                    + "desta consulta e mudam de lugar quando os dados mudam.");
        }

        // tudo igual, ou escala degenerada: uma faixa so
        if (maximo.compareTo(minimo) == 0) {
            Faixa unica = new Faixa(1, rotulo(minimo, maximo), minimo, maximo, 1,
                    valores.size());
            avisos.add("Todos os municipios tem o mesmo valor: uma faixa so.");
            return new Resultado(List.of(unica), minimo, maximo, origem, avisos);
        }

        BigDecimal largura = maximo.subtract(minimo)
                .divide(BigDecimal.valueOf(quantidade), ESCALA, RoundingMode.HALF_UP);

        List<BigDecimal> limites = new ArrayList<>();
        limites.add(minimo);
        for (int i = 1; i < quantidade; i++) {
            limites.add(minimo.add(largura.multiply(BigDecimal.valueOf(i))));
        }
        limites.add(maximo);

        List<BigDecimal> foraDaEscala = valores.stream()
                .filter(v -> v.compareTo(minimo) < 0 || v.compareTo(maximo) > 0)
                .toList();

        if (!foraDaEscala.isEmpty()) {
            avisos.add(foraDaEscala.size() + " municipio(s) com valor fora da escala "
                    + rotulo(minimo, maximo)
                    + ": entraram na faixa da ponta mais proxima.");
        }

        return new Resultado(montar(limites, valores, sentido), minimo, maximo, origem,
                avisos);
    }

    private static Resultado porQuantil(
            int quantidade, List<BigDecimal> valores, SentidoIndicador sentido) {

        List<String> avisos = new ArrayList<>();
        BigDecimal minimo = valores.get(0);
        BigDecimal maximo = valores.get(valores.size() - 1);

        if (maximo.compareTo(minimo) == 0) {
            Faixa unica = new Faixa(1, rotulo(minimo, maximo), minimo, maximo, 1,
                    valores.size());
            avisos.add("Todos os municipios tem o mesmo valor: uma faixa so.");
            return new Resultado(List.of(unica), minimo, maximo, "DADOS", avisos);
        }


        List<BigDecimal> cortes = new ArrayList<>();
        for (int i = 1; i < quantidade; i++) {
            int posicao = (int) Math.ceil((double) i * valores.size() / quantidade);
            BigDecimal corte = valores.get(Math.min(posicao, valores.size() - 1));

            if (corte.compareTo(minimo) > 0 && corte.compareTo(maximo) < 0
                    && !cortes.contains(corte)) {
                cortes.add(corte);
            }
        }

        List<BigDecimal> limites = new ArrayList<>();
        limites.add(minimo);
        limites.addAll(cortes);
        limites.add(maximo);

        if (cortes.size() + 1 < quantidade) {
            avisos.add("Pedidas " + quantidade + " faixas, saíram " + (cortes.size() + 1)
                    + ": os valores repetem e um corte nao pode partir valor igual.");
        }

        return new Resultado(montar(limites, valores, sentido), minimo, maximo, "DADOS",
                avisos);
    }

    /** Monta as faixas a partir dos limites e conta quantos municipios caem em cada. */
    private static List<Faixa> montar(
            List<BigDecimal> limites, List<BigDecimal> valores, SentidoIndicador sentido) {

        int total = limites.size() - 1;
        List<Faixa> faixas = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            BigDecimal de = limites.get(i);
            BigDecimal ate = limites.get(i + 1);
            int ordem = i + 1;
            boolean ultima = ordem == total;

            long quantos = valores.stream()
                    .filter(v -> dentro(v, de, ate, ultima, ordem == 1))
                    .count();

            // 1 e sempre a pior: quando menor e melhor, a ordem se inverte
            int posicaoNaQualidade = sentido.maiorEMelhor() ? ordem : total - ordem + 1;

            faixas.add(new Faixa(ordem, rotulo(de, ate), de, ate, posicaoNaQualidade,
                    quantos));
        }

        return faixas;
    }

    private static boolean dentro(BigDecimal valor, BigDecimal de, BigDecimal ate,
                                  boolean ultima, boolean primeira) {
        // valor abaixo da escala conta na primeira faixa, acima conta na ultima
        if (primeira && valor.compareTo(de) < 0) {
            return true;
        }

        if (ultima) {
            return valor.compareTo(de) >= 0;
        }

        return valor.compareTo(de) >= 0 && valor.compareTo(ate) < 0;
    }

    private static String rotulo(BigDecimal de, BigDecimal ate) {
        return numero(de) + " a " + numero(ate);
    }

    private static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
