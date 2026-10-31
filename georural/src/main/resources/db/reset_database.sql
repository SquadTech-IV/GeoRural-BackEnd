-- =====================================================================
-- GeoRural DataHub - Reset completo do banco
-- =====================================================================

--     SELECT COUNT(*) FROM flyway_schema_history;   -- deve dar ORA-00942

BEGIN
    -- pacotes PL/SQL
    FOR p IN (SELECT object_name FROM user_objects WHERE object_type = 'PACKAGE') LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP PACKAGE "' || p.object_name || '"';
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END LOOP;

    -- views
    FOR v IN (SELECT view_name FROM user_views) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP VIEW "' || v.view_name || '"';
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END LOOP;

    -- tabelas (inclui FLYWAY_SCHEMA_HISTORY)
    FOR t IN (SELECT table_name FROM user_tables WHERE secondary = 'N') LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP TABLE "' || t.table_name || '" CASCADE CONSTRAINTS PURGE';
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END LOOP;

    -- metadata espacial
    BEGIN
        DELETE FROM user_sdo_geom_metadata;
    EXCEPTION WHEN OTHERS THEN NULL;
    END;

    COMMIT;
END;
/
