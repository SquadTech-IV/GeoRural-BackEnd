-- Os 5 UUIDs que a equipe ja tinha gravado continuam valendo: o realm novo usa
-- exatamente eles. So a conta do admin esta com o texto COLE_AQUI_O_ID_DO_KEYCLOAK
-- no lugar do sub, e o 1111... completa o padrao (1=ADMIN, 2=GESTOR, 3=OPERADOR,
-- 4=ANALISTA, 5=AUDITOR, 6=operador2).


-- ---------------------------------------------------------------- 1
UPDATE USUARIO
   SET usu_keycloak_id = '11111111-1111-1111-1111-111111111111'
 WHERE usu_email = 'admin@georural.invalid';

COMMIT;


-- ---------------------------------------------------------------- 2
-- Conferencia: as 6 contas, com o sub e o perfil.
-- O par (usu_keycloak_id, per_funcao) TEM que casar com o realm, senao o
-- ValidacaoUsuarioJwtFilter rejeita com 401.
SELECT u.usu_id, u.usu_email, u.usu_keycloak_id, p.per_funcao, u.usu_status
  FROM USUARIO u
  JOIN PERFIL p ON p.per_id = u.per_id
 ORDER BY u.usu_id;
