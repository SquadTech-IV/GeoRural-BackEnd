-- GeoRural DataHub - Database Script - Reset completo do banco.

BEGIN
    FOR v IN (SELECT view_name FROM user_views) LOOP
        EXECUTE IMMEDIATE 'DROP VIEW "' || v.view_name || '"';
    END LOOP;
    FOR t IN (SELECT table_name FROM user_tables) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE "' || t.table_name || '" CASCADE CONSTRAINTS PURGE';
    END LOOP;
    DELETE FROM user_sdo_geom_metadata;
    COMMIT;
END;
/
