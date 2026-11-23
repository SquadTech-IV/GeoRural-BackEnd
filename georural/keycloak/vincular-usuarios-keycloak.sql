-- Liga os usuarios do Oracle aos usuarios do Keycloak.
--
-- Por que isso e necessario: o ValidacaoUsuarioJwtFilter busca a conta pelo 'sub'
-- do token, via PKG_AUTH.consultar_por_sub. O V2__dados_iniciais.sql inseriu os
-- usuarios sem usu_keycloak_id, entao hoje nenhum deles e encontrado - a API
-- responde 401 "Usuario nao cadastrado na base para sub=..." mesmo com token valido.
--
-- Os UUIDs abaixo sao os ids fixos declarados no georural-realm.json. Por isso dao
-- para escrever direto, sem copiar nada do console do Keycloak.
--
-- Rode no SQL Developer Web (Database Actions), como ADMIN.


-- ---------------------------------------------------------------- 1
-- Veja como esta hoje. A coluna usu_keycloak_id deve vir toda nula.
SELECT u.usu_id, u.usu_nome, u.usu_email, u.usu_keycloak_id, p.per_funcao
  FROM USUARIO u
  JOIN PERFIL p ON p.per_id = u.per_id
 ORDER BY u.usu_id;


-- ---------------------------------------------------------------- 2
-- Liga cada conta pelo e-mail.
-- Se algum UPDATE afetar 0 linhas, o e-mail no seu banco e diferente do que eu
-- supus: me mande o resultado do passo 1 e eu ajusto.
UPDATE USUARIO SET usu_keycloak_id = '9e0a1111-0000-4000-8000-00000000ad31'
 WHERE usu_email = 'admin@georural.invalid';

UPDATE USUARIO SET usu_keycloak_id = '9e0a2222-0000-4000-8000-0000000065e5'
 WHERE usu_email = 'gestor@georural.invalid';

UPDATE USUARIO SET usu_keycloak_id = '9e0a3333-0000-4000-8000-0000000009e4'
 WHERE usu_email = 'operador@georural.invalid';

UPDATE USUARIO SET usu_keycloak_id = '9e0a4444-0000-4000-8000-0000000004a1'
 WHERE usu_email = 'analista@georural.invalid';

UPDATE USUARIO SET usu_keycloak_id = '9e0a5555-0000-4000-8000-00000000a4d1'
 WHERE usu_email = 'auditor@georural.invalid';

COMMIT;


-- ---------------------------------------------------------------- 3
-- Conferencia. Tem que voltar 5 linhas, com o usu_keycloak_id preenchido e o
-- per_funcao casando com o papel que o UUID indica.
-- O per_funcao TEM que bater: o filtro rejeita com 401 se o papel do token
-- divergir do perfil cadastrado.
SELECT u.usu_id, u.usu_email, u.usu_keycloak_id, p.per_funcao, u.usu_status
  FROM USUARIO u
  JOIN PERFIL p ON p.per_id = u.per_id
 WHERE u.usu_keycloak_id IS NOT NULL
 ORDER BY u.usu_id;


-- ---------------------------------------------------------------- 4
-- Qualquer conta que sobrou sem vinculo. Essas nao conseguem entrar.
-- Nao e problema se forem contas de teste criadas a mao.
SELECT usu_id, usu_nome, usu_email
  FROM USUARIO
 WHERE usu_keycloak_id IS NULL;
