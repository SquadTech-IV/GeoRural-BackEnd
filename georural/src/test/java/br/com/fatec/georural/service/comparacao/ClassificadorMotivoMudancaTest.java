package br.com.fatec.georural.service.comparacao;

import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.EntradaOrigem;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.MotivoMudanca;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Parametro;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Regra;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.SituacaoImovel;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.TipoMotivo;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.VersaoPublicada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** US#19.04 - um cenario para cada tipo de motivo. */
class ClassificadorMotivoMudancaTest {

    private static final String FORMULA = "area_embargada / area_total * 100";

    private static Regra regra(long id, String versao, String formula, String rl) {
        return new Regra(id, versao, formula, List.of(new Parametro("RL_MINIMA", "Reserva legal", rl, "%")));
    }

    private static EntradaOrigem entrada(long versaoId, long numero, String hash) {
        return new EntradaOrigem(versaoId, numero, "2026-0" + numero, hash, 10L, "Embargos IBAMA", "IBAMA");
    }

    private static VersaoPublicada versao(long id, Regra regra, List<EntradaOrigem> entradas) {
        return new VersaoPublicada(id, id, "h" + id, "2026-01", "ANTERIOR", null,
                1L, "IAE", 2L, "SquadTech", 100L + id, "run-" + id, regra, entradas);
    }

    private static List<TipoMotivo> tipos(List<MotivoMudanca> motivos) {
        return motivos.stream().map(MotivoMudanca::tipo).toList();
    }

    @Test
    @DisplayName("Mudanca de dado de origem: mesma regra, hash de entrada diferente")
    void dadoDeOrigem() {
        Regra r = regra(1, "1.0", FORMULA, "20");
        var a = versao(1, r, List.of(entrada(50, 1, "aaa")));
        var b = versao(2, r, List.of(entrada(51, 2, "bbb")));

        var motivos = ClassificadorMotivoMudanca.classificar(a, b);

        assertEquals(List.of(TipoMotivo.DADO_DE_ORIGEM), tipos(motivos));
        assertTrue(motivos.get(0).detalhes().get(0).contains("Embargos IBAMA"));
    }

    @Test
    @DisplayName("Mudanca de regra: formula diferente")
    void regra() {
        var entradas = List.of(entrada(50, 1, "aaa"));
        var a = versao(1, regra(1, "1.0", FORMULA, "20"), entradas);
        var b = versao(2, regra(2, "2.0", "area_embargada / area_imovel * 100", "20"), entradas);

        assertEquals(List.of(TipoMotivo.REGRA), tipos(ClassificadorMotivoMudanca.classificar(a, b)));
    }

    @Test
    @DisplayName("Mudanca de parametro: mesma formula, valor de parametro diferente")
    void parametro() {
        var entradas = List.of(entrada(50, 1, "aaa"));
        var a = versao(1, regra(1, "1.0", FORMULA, "20"), entradas);
        var b = versao(2, regra(2, "1.1", FORMULA, "40"), entradas);

        var motivos = ClassificadorMotivoMudanca.classificar(a, b);

        assertEquals(List.of(TipoMotivo.PARAMETRO), tipos(motivos));
        assertEquals(List.of("RL_MINIMA: 20 → 40"), motivos.get(0).detalhes());
    }

    @Test
    @DisplayName("Formula com espacos diferentes nao conta como mudanca de regra")
    void formulaComEspacosDiferentes() {
        var entradas = List.of(entrada(50, 1, "aaa"));
        var a = versao(1, regra(1, "1.0", FORMULA, "20"), entradas);
        var b = versao(2, regra(2, "1.1", "area_embargada  /  area_total * 100 ", "20"), entradas);

        assertTrue(ClassificadorMotivoMudanca.classificar(a, b).isEmpty());
    }

    @Test
    @DisplayName("Varios motivos ao mesmo tempo seguem a ordem REGRA, PARAMETRO, DADO_DE_ORIGEM")
    void variosMotivos() {
        var a = versao(1, regra(1, "1.0", FORMULA, "20"), List.of(entrada(50, 1, "aaa")));
        var b = versao(2, regra(2, "2.0", "outra formula", "40"), List.of(entrada(51, 2, "bbb")));

        assertEquals(List.of(TipoMotivo.REGRA, TipoMotivo.PARAMETRO, TipoMotivo.DADO_DE_ORIGEM),
                tipos(ClassificadorMotivoMudanca.classificar(a, b)));
    }

    @Test
    @DisplayName("Mesma regra e mesmas entradas: nenhum motivo")
    void nenhumMotivo() {
        Regra r = regra(1, "1.0", FORMULA, "20");
        var entradas = List.of(entrada(50, 1, "aaa"));

        assertTrue(ClassificadorMotivoMudanca.classificar(versao(1, r, entradas), versao(2, r, entradas)).isEmpty());
    }

    @Test
    @DisplayName("Parametro que existe so em uma das regras aparece como (ausente)")
    void parametroAusente() {
        var a = new Regra(1L, "1.0", FORMULA, List.of());
        var b = new Regra(2L, "1.1", FORMULA, List.of(new Parametro("FATOR", null, "2", null)));

        assertEquals(List.of("FATOR: (ausente) → 2"), ClassificadorMotivoMudanca.diferencasDeParametros(
                a.parametros(), b.parametros()));
    }

    @Test
    @DisplayName("Motivo por imovel: igual sem motivo, so em uma versao = origem, alterado herda os da versao")
    void motivosDoImovel() {
        var motivos = List.of(new MotivoMudanca(TipoMotivo.PARAMETRO, "x", List.of()));

        assertTrue(ClassificadorMotivoMudanca.motivosDoImovel(SituacaoImovel.IGUAL, motivos).isEmpty());
        assertEquals(List.of(TipoMotivo.DADO_DE_ORIGEM),
                ClassificadorMotivoMudanca.motivosDoImovel(SituacaoImovel.APENAS_EM_B, motivos));
        assertEquals(List.of(TipoMotivo.DADO_DE_ORIGEM),
                ClassificadorMotivoMudanca.motivosDoImovel(SituacaoImovel.APENAS_EM_A, motivos));
        assertEquals(List.of(TipoMotivo.PARAMETRO),
                ClassificadorMotivoMudanca.motivosDoImovel(SituacaoImovel.ALTERADO, motivos));
        assertEquals(List.of(TipoMotivo.NAO_IDENTIFICADO),
                ClassificadorMotivoMudanca.motivosDoImovel(SituacaoImovel.ALTERADO, List.of()));
    }
}
