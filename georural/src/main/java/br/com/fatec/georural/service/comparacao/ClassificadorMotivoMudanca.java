package br.com.fatec.georural.service.comparacao;

import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.EntradaOrigem;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.MotivoMudanca;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Parametro;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Regra;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.SituacaoImovel;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.TipoMotivo;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.VersaoPublicada;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * US#19.03 - Decide por que dois resultados mudaram, comparando o que identifica cada versao:
 *  - REGRA: a formula do calculo mudou;
 *  - PARAMETRO: a formula e a mesma (ou nao), mas algum parametro tem valor diferente;
 *  - DADO_DE_ORIGEM: as versoes de dados de entrada (hash) usadas no calculo sao outras.
 * Mais de um motivo pode valer ao mesmo tempo; a lista segue a ordem REGRA, PARAMETRO, DADO_DE_ORIGEM.
 */
public final class ClassificadorMotivoMudanca {

    private ClassificadorMotivoMudanca() {
    }

    /** Motivos da mudanca entre as versoes (vale para todo imovel cujo valor mudou). */
    public static List<MotivoMudanca> classificar(VersaoPublicada a, VersaoPublicada b) {
        List<MotivoMudanca> motivos = new ArrayList<>();
        Regra ra = a.regra();
        Regra rb = b.regra();

        // Regras sao imutaveis: mesmo id = mesma formula e mesmos parametros.
        if (ra != null && rb != null && !Objects.equals(ra.id(), rb.id())) {
            if (!normalizar(ra.formula()).equals(normalizar(rb.formula()))) {
                motivos.add(new MotivoMudanca(TipoMotivo.REGRA,
                        "A fórmula de cálculo mudou entre as versões.",
                        List.of("Regra " + ra.versao() + " → " + rb.versao(),
                                "Fórmula A: " + ra.formula(),
                                "Fórmula B: " + rb.formula())));
            }
            List<String> parametros = diferencasDeParametros(ra.parametros(), rb.parametros());
            if (!parametros.isEmpty()) {
                motivos.add(new MotivoMudanca(TipoMotivo.PARAMETRO,
                        "Parâmetros da regra com valor diferente.", parametros));
            }
        }

        List<String> origem = diferencasDeOrigem(a.entradas(), b.entradas());
        if (!origem.isEmpty()) {
            motivos.add(new MotivoMudanca(TipoMotivo.DADO_DE_ORIGEM,
                    "Os dados de origem usados no cálculo são outros.", origem));
        }
        return motivos;
    }

    /** Tipos atribuidos a um imovel: so os imoveis alterados herdam os motivos da versao. */
    public static List<TipoMotivo> motivosDoImovel(SituacaoImovel situacao, List<MotivoMudanca> motivosDaVersao) {
        return switch (situacao) {
            case IGUAL -> List.of();
            // o imovel entrou ou saiu da base de origem
            case APENAS_EM_A, APENAS_EM_B -> List.of(TipoMotivo.DADO_DE_ORIGEM);
            case ALTERADO -> motivosDaVersao.isEmpty()
                    ? List.of(TipoMotivo.NAO_IDENTIFICADO)
                    : motivosDaVersao.stream().map(MotivoMudanca::tipo).toList();
        };
    }

    static List<String> diferencasDeParametros(List<Parametro> a, List<Parametro> b) {
        Map<String, String> mapaA = paraMapa(a);
        Map<String, String> mapaB = paraMapa(b);
        Set<String> nomes = new LinkedHashSet<>(mapaA.keySet());
        nomes.addAll(mapaB.keySet());

        List<String> diferencas = new ArrayList<>();
        for (String nome : nomes) {
            String va = mapaA.get(nome);
            String vb = mapaB.get(nome);
            if (!Objects.equals(va, vb)) {
                diferencas.add(nome + ": " + (va == null ? "(ausente)" : va)
                        + " → " + (vb == null ? "(ausente)" : vb));
            }
        }
        return diferencas;
    }

    static List<String> diferencasDeOrigem(List<EntradaOrigem> a, List<EntradaOrigem> b) {
        Map<Long, List<EntradaOrigem>> porConjuntoA = agrupar(a);
        Map<Long, List<EntradaOrigem>> porConjuntoB = agrupar(b);
        Set<Long> conjuntos = new LinkedHashSet<>(porConjuntoA.keySet());
        conjuntos.addAll(porConjuntoB.keySet());

        List<String> diferencas = new ArrayList<>();
        for (Long conjuntoId : conjuntos) {
            List<EntradaOrigem> ea = porConjuntoA.getOrDefault(conjuntoId, List.of());
            List<EntradaOrigem> eb = porConjuntoB.getOrDefault(conjuntoId, List.of());
            if (!hashes(ea).equals(hashes(eb))) {
                String nome = !ea.isEmpty() ? ea.get(0).conjuntoNome() : eb.get(0).conjuntoNome();
                diferencas.add(nome + ": " + rotulo(ea) + " → " + rotulo(eb));
            }
        }
        return diferencas;
    }

    private static Map<Long, List<EntradaOrigem>> agrupar(List<EntradaOrigem> entradas) {
        Map<Long, List<EntradaOrigem>> mapa = new LinkedHashMap<>();
        if (entradas != null) {
            for (EntradaOrigem e : entradas) {
                mapa.computeIfAbsent(e.conjuntoId(), k -> new ArrayList<>()).add(e);
            }
        }
        return mapa;
    }

    private static Set<String> hashes(List<EntradaOrigem> entradas) {
        Set<String> conjunto = new LinkedHashSet<>();
        for (EntradaOrigem e : entradas) {
            conjunto.add(e.hash() == null ? "versao-" + e.versaoId() : e.hash().trim());
        }
        return conjunto;
    }

    private static String rotulo(List<EntradaOrigem> entradas) {
        if (entradas.isEmpty()) {
            return "(sem entrada)";
        }
        List<String> partes = new ArrayList<>();
        for (EntradaOrigem e : entradas) {
            partes.add("v" + e.numero() + (e.competencia() == null ? "" : " (" + e.competencia() + ")"));
        }
        return String.join(", ", partes);
    }

    private static Map<String, String> paraMapa(List<Parametro> parametros) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (parametros != null) {
            for (Parametro p : parametros) {
                mapa.put(p.nome(), p.valor() == null ? null : p.valor().trim());
            }
        }
        return mapa;
    }

    private static String normalizar(String formula) {
        return formula == null ? "" : formula.replaceAll("\\s+", " ").trim();
    }
}
