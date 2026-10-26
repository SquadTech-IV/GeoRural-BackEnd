package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.entity.ResultadoMunicipio;
import br.com.fatec.georural.entity.SituacaoVersao;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.ConflitoException;
import org.springframework.stereotype.Service;

@Service
public class ImutabilidadeVersaoService {

    /** True se a versao ja foi publicada (VIGENTE ou ANTERIOR) e, portanto, esta imutavel. */
    public boolean imutavel(Versao versao) {
        return versao != null
                && versao.getSituacao() != null
                && SituacaoVersao.de(versao.getSituacao()).ehImutavel();
    }

    /** Bloqueia a alteracao de uma versao ja publicada. */
    public void garantirVersaoEditavel(Versao versao) {
        if (imutavel(versao)) {
            throw new ConflitoException("Versao " + versao.getId()
                    + " esta publicada (" + versao.getSituacao()
                    + ") e nao pode mais ser alterada.");
        }
    }

    /** Bloqueia a alteracao de um ResultadoIndicador vinculado a uma versao publicada. */
    public void garantirEditavel(ResultadoIndicador resultado) {
        if (resultado != null && imutavel(resultado.getVersao())) {
            throw new ConflitoException("ResultadoIndicador " + resultado.getId()
                    + " pertence a uma versao publicada e nao pode ser alterado.");
        }
    }

    /** Bloqueia a alteracao de um ResultadoMunicipio vinculado a uma versao publicada. */
    public void garantirEditavel(ResultadoMunicipio resultado) {
        if (resultado != null && imutavel(resultado.getVersao())) {
            throw new ConflitoException("ResultadoMunicipio " + resultado.getId()
                    + " pertence a uma versao publicada e nao pode ser alterado.");
        }
    }
}