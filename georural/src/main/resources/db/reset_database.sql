-- GeoRural DataHub - Database Script - Reset completo do banco.

BEGIN
    FOR v IN (SELECT view_name FROM user_views) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP VIEW "' || v.view_name || '"';
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END LOOP;

    FOR t IN (SELECT table_name FROM user_tables WHERE secondary = 'N') LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP TABLE "' || t.table_name || '" CASCADE CONSTRAINTS PURGE';
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END LOOP;

    DELETE FROM user_sdo_geom_metadata;
    COMMIT;
END;
/