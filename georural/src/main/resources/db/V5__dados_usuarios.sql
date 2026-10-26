-- Usuários de teste
INSERT INTO USUARIO (per_id, usu_nome, usu_email, usu_senha_hash)
    VALUES (1, 'Usuário ADMIN', 'admin@exemplo.invalid', 'SEM-LOGIN');

INSERT INTO USUARIO (per_id, usu_nome, usu_email, usu_senha_hash)
    VALUES (2, 'Usuário OPERADOR', 'operador@exemplo.invalid', 'SEM-LOGIN');

INSERT INTO USUARIO (per_id, usu_nome, usu_email, usu_senha_hash)
    VALUES (3, 'Usuário ANALISTA', 'analista@exemplo.invalid', 'SEM-LOGIN');

INSERT INTO USUARIO (per_id, usu_nome, usu_email, usu_senha_hash)
    VALUES (4, 'Usuário GESTOR', 'gestor@exemplo.invalid', 'SEM-LOGIN');

INSERT INTO USUARIO (per_id, usu_nome, usu_email, usu_senha_hash)
    VALUES (5, 'Usuário AUDITOR', 'auditor@exemplo.invalid', 'SEM-LOGIN');
COMMIT;