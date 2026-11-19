-- =====================================================================
-- GeoRural DataHub - View e Package de Autorizacao (US#13.03 / US#13.04)
-- =====================================================================

CREATE OR REPLACE VIEW vw_usuario_autorizacao AS
SELECT u.usu_id,
       u.usu_keycloak_id,
       u.usu_nome,
       u.usu_email,
       u.usu_status,
       p.per_id,
       p.per_funcao,
       p.per_nome
  FROM USUARIO u
  JOIN PERFIL  p ON p.per_id = u.per_id;

CREATE OR REPLACE PACKAGE PKG_AUTH AS
    -- Dado o sub do Keycloak, devolve usu_id, usu_status e per_funcao.
    PROCEDURE consultar_por_sub(
        p_sub        IN  VARCHAR2,
        p_usu_id     OUT NUMBER,
        p_usu_status OUT VARCHAR2,
        p_per_funcao OUT VARCHAR2
    );

    -- Percorre arq_pai_id recursivamente e devolve o usu_id do arquivo raiz.
    FUNCTION dono_arquivo_raiz(p_arq_bruto_id IN NUMBER) RETURN NUMBER;

    -- Resolve o usu_id do arquivo raiz a partir de um registro de QUARENTENA.
    FUNCTION dono_quarentena(p_qua_id IN NUMBER) RETURN NUMBER;
END PKG_AUTH;
/

CREATE OR REPLACE PACKAGE BODY PKG_AUTH AS

    PROCEDURE consultar_por_sub(
        p_sub        IN  VARCHAR2,
        p_usu_id     OUT NUMBER,
        p_usu_status OUT VARCHAR2,
        p_per_funcao OUT VARCHAR2
    ) IS
    BEGIN
        p_usu_id     := NULL;
        p_usu_status := NULL;
        p_per_funcao := NULL;

        IF p_sub IS NULL THEN
            RETURN;
        END IF;

        SELECT u.usu_id, u.usu_status, p.per_funcao
          INTO p_usu_id, p_usu_status, p_per_funcao
          FROM USUARIO u
          JOIN PERFIL  p ON p.per_id = u.per_id
         WHERE u.usu_keycloak_id = p_sub;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_usu_id     := NULL;
            p_usu_status := NULL;
            p_per_funcao := NULL;
    END consultar_por_sub;

    FUNCTION dono_arquivo_raiz(p_arq_bruto_id IN NUMBER) RETURN NUMBER IS
        v_usu_id NUMBER;
    BEGIN
        IF p_arq_bruto_id IS NULL THEN
            RETURN NULL;
        END IF;

        SELECT usu_id
          INTO v_usu_id
          FROM (
              SELECT usu_id, arq_pai_id
                FROM ARQUIVO_BRUTO
               START WITH arq_bruto_id = p_arq_bruto_id
             CONNECT BY PRIOR arq_pai_id = arq_bruto_id
          )
         WHERE arq_pai_id IS NULL
           AND ROWNUM = 1;

        RETURN v_usu_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN NULL;
    END dono_arquivo_raiz;

    FUNCTION dono_quarentena(p_qua_id IN NUMBER) RETURN NUMBER IS
        v_arq_id NUMBER;
    BEGIN
        IF p_qua_id IS NULL THEN
            RETURN NULL;
        END IF;

        SELECT v.arq_bruto_id
          INTO v_arq_id
          FROM QUARENTENA q
          JOIN VALIDACAO  v ON v.val_id = q.val_id
         WHERE q.qua_id = p_qua_id;

        RETURN dono_arquivo_raiz(v_arq_id);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN NULL;
    END dono_quarentena;

END PKG_AUTH;
/