-- =====================================================================
-- GeoRural DataHub - registro de erros da API (US#30.05)
-- =====================================================================
-- O cartao deixava a escolha entre o log da nuvem e uma tabela propria.
-- Sem a US#11 no ar nao existe log centralizado, entao e tabela propria.
--
-- Esta tabela e descartavel: nada do negocio depende dela, e as linhas antigas
-- sao apagadas conforme georural.monitoramento.erros.retencao-dias.
-- Nao tem FK para USUARIO de proposito: quem chamou nao e conhecido enquanto a
-- autenticacao (US#13) nao existir, e uma FK obrigaria a limpar na ordem.

CREATE TABLE ERRO_API (
    err_id            NUMBER        GENERATED ALWAYS AS IDENTITY,
    err_data_hora     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    err_metodo        VARCHAR2(10)  NOT NULL,
    err_rota          VARCHAR2(500) NOT NULL,
    err_status        NUMBER(3)     NOT NULL,
    err_excecao       VARCHAR2(200),
    err_mensagem      VARCHAR2(2000),
    -- so os quadros do proprio projeto, cortados; nunca a pilha inteira
    err_pilha         CLOB,
    err_ip_origem     VARCHAR2(45),
    -- id de correlacao para achar a mesma requisicao no log do servidor
    err_correlacao    VARCHAR2(64),
    err_duracao_ms    NUMBER(10),
    CONSTRAINT pk_erro_api PRIMARY KEY (err_id),
    CONSTRAINT ck_err_status CHECK (err_status BETWEEN 100 AND 599)
);

COMMENT ON TABLE ERRO_API IS
    'Erros de requisicao da API, para a tela de monitoramento (US#30). Descartavel.';

-- a tela abre sempre pelos mais recentes
CREATE INDEX idx_err_data ON ERRO_API (err_data_hora DESC);
-- e filtra por faixa de status
CREATE INDEX idx_err_status_data ON ERRO_API (err_status, err_data_hora DESC);
