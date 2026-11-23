-- =====================================================================
-- GeoRural DataHub - PKG_CALCULO
-- =====================================================================

create or replace package pkg_calculo as

    -- imoveis alcancados por esta rodada; escopo = IMOVEL, RESERVA_LEGAL, VEGETACAO ou EMBARGO
   function imoveis_no_escopo (
      p_ver_id number,
      p_escopo varchar2
   ) return sys.odcinumberlist;

   procedure calcular_irl (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_minimo_pct number,
      p_escopo     varchar2,
      p_calculados out number
   );

-- Na especificacao de PKG_CALCULO:
PROCEDURE calcular_idesmat(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_escopo VARCHAR2, p_calculados OUT NUMBER);

-- No BODY de PKG_CALCULO:
PROCEDURE calcular_idesmat(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_escopo VARCHAR2, p_calculados OUT NUMBER) IS
    v_ids      SYS.ODCINUMBERLIST := imoveis_no_escopo(p_ver_id, p_escopo);
    v_ind      NUMBER := id_indicador('IDESMAT');
    v_m2008    DATE := DATE '2008-07-22';
    v_m2019    DATE := DATE '2019-07-31';
    v_m2020    DATE := DATE '2020-12-31';
    v_srid_eq  NUMBER := 102033;
BEGIN
    IF v_ids.COUNT = 0 THEN
        p_calculados := 0;
        RETURN;
    END IF;

    FOR r IN (SELECT prc_nome, prc_valor FROM PARAMETRO_REGRA_CALCULO WHERE rca_id = p_rca_id) LOOP
        IF r.prc_nome = 'marco_2008' THEN v_m2008 := TO_DATE(SUBSTR(r.prc_valor, 1, 10), 'YYYY-MM-DD'); END IF;
        IF r.prc_nome = 'marco_2019' THEN v_m2019 := TO_DATE(SUBSTR(r.prc_valor, 1, 10), 'YYYY-MM-DD'); END IF;
        IF r.prc_nome = 'marco_2020' THEN v_m2020 := TO_DATE(SUBSTR(r.prc_valor, 1, 10), 'YYYY-MM-DD'); END IF;
        IF r.prc_nome = 'srid_equivalente' THEN v_srid_eq := TO_NUMBER(r.prc_valor); END IF;
    END LOOP;

    MERGE INTO RESULTADO_INDICADOR d
    USING (
        SELECT o.imo_id,
               v_ind AS ind_id,
               LEAST(o.ha_2008 / NULLIF(o.area_imovel, 0) * 100, 100) AS pct_2008,
               LEAST(o.ha_2008, o.area_imovel) AS ha_2008,
               CASE WHEN o.ha_2008 > 0 THEN 'COM_DESMATAMENTO' ELSE 'SEM_DESMATAMENTO' END AS situacao,
               '{"area_imovel_ha":' || TO_CHAR(ROUND(o.area_imovel, 4))
               || ',"srid_equivalente":' || TO_CHAR(v_srid_eq)
               || ',"dissolvido":true'
               || ',"marcos":['
               || '{"marco":"marco_2008","data":"' || TO_CHAR(v_m2008, 'YYYY-MM-DD')
               || '","area_ha":' || TO_CHAR(ROUND(LEAST(o.ha_2008, o.area_imovel), 4))
               || ',"percentual":' || TO_CHAR(ROUND(NVL(LEAST(o.ha_2008 / NULLIF(o.area_imovel, 0) * 100, 100), 0), 4)) || '},'
               || '{"marco":"marco_2019","data":"' || TO_CHAR(v_m2019, 'YYYY-MM-DD')
               || '","area_ha":' || TO_CHAR(ROUND(LEAST(o.ha_2019, o.area_imovel), 4))
               || ',"percentual":' || TO_CHAR(ROUND(NVL(LEAST(o.ha_2019 / NULLIF(o.area_imovel, 0) * 100, 100), 0), 4)) || '},'
               || '{"marco":"marco_2020","data":"' || TO_CHAR(v_m2020, 'YYYY-MM-DD')
               || '","area_ha":' || TO_CHAR(ROUND(LEAST(o.ha_2020, o.area_imovel), 4))
               || ',"percentual":' || TO_CHAR(ROUND(NVL(LEAST(o.ha_2020 / NULLIF(o.area_imovel, 0) * 100, 100), 0), 4)) || '}'
               || ']}' AS detalhes
          FROM (
              SELECT i.imo_id,
                     NVL(PKG_GEO.area_equivalente_ha(i.imo_geometria, v_srid_eq), NVL(i.imo_area_total, 0)) AS area_imovel,
                     NVL(PKG_GEO.area_equivalente_ha(
                         PKG_GEO.intersecao(
                             i.imo_geometria,
                             (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(ds.des_geometria, 0.05))
                                FROM DESMATAMENTO ds
                               WHERE ds.des_geometria IS NOT NULL
                                 AND NVL(ds.des_data_corte, TO_DATE(ds.des_ano_ref || '-12-31', 'YYYY-MM-DD')) > v_m2008
                                 AND SDO_RELATE(ds.des_geometria, i.imo_geometria, 'mask=ANYINTERACT') = 'TRUE')
                         ), v_srid_eq), 0) AS ha_2008,
                     NVL(PKG_GEO.area_equivalente_ha(
                         PKG_GEO.intersecao(
                             i.imo_geometria,
                             (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(ds.des_geometria, 0.05))
                                FROM DESMATAMENTO ds
                               WHERE ds.des_geometria IS NOT NULL
                                 AND NVL(ds.des_data_corte, TO_DATE(ds.des_ano_ref || '-12-31', 'YYYY-MM-DD')) > v_m2019
                                 AND SDO_RELATE(ds.des_geometria, i.imo_geometria, 'mask=ANYINTERACT') = 'TRUE')
                         ), v_srid_eq), 0) AS ha_2019,
                     NVL(PKG_GEO.area_equivalente_ha(
                         PKG_GEO.intersecao(
                             i.imo_geometria,
                             (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(ds.des_geometria, 0.05))
                                FROM DESMATAMENTO ds
                               WHERE ds.des_geometria IS NOT NULL
                                 AND NVL(ds.des_data_corte, TO_DATE(ds.des_ano_ref || '-12-31', 'YYYY-MM-DD')) > v_m2020
                                 AND SDO_RELATE(ds.des_geometria, i.imo_geometria, 'mask=ANYINTERACT') = 'TRUE')
                         ), v_srid_eq), 0) AS ha_2020
                FROM IMOVEL_RURAL i
               WHERE i.imo_id IN (SELECT column_value FROM TABLE(v_ids))
          ) o
    ) f
    ON (d.imo_id = f.imo_id AND d.ind_id = f.ind_id AND d.ver_id = p_ver_id)
    WHEN MATCHED THEN
        UPDATE SET d.rin_valor_percentual = f.pct_2008,
                   d.rin_valor_hectares   = f.ha_2008,
                   d.rin_valor_absoluto   = f.ha_2008,
                   d.rin_situacao         = f.situacao,
                   d.rin_detalhes         = f.detalhes,
                   d.rin_data_calculo     = SYSTIMESTAMP,
                   d.exe_id               = p_exe_id,
                   d.rca_id               = p_rca_id
    WHEN NOT MATCHED THEN
        INSERT (imo_id, ind_id, ver_id, exe_id, rca_id, rin_valor_percentual,
                rin_valor_hectares, rin_valor_absoluto, rin_situacao, rin_detalhes,
                rin_data_calculo, rin_vigente)
        VALUES (f.imo_id, f.ind_id, p_ver_id, p_exe_id, p_rca_id, f.pct_2008,
                f.ha_2008, f.ha_2008, f.situacao, f.detalhes,
                SYSTIMESTAMP, 'N');

    p_calculados := SQL%ROWCOUNT;
END calcular_idesmat;

   procedure calcular_iae (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_escopo     varchar2,
      p_calculados out number
   );

   procedure calcular_icv (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_minimo_pct number,
      p_escopo     varchar2,
      p_calculados out number
   );

   procedure calcular_isap (
      p_ver_id        number,
      p_exe_id        number,
      p_rca_id        number,
      p_escopo        varchar2,
      p_calculados    out number,
      p_camada_ver_id number default null
   );

   procedure calcular_iapp (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_escopo     varchar2,
      p_calculados out number,
      p_app_ver_id number default null,
      p_veg_ver_id number default null
   );

   procedure agregar_municipio (
      p_ver_id    number,
      p_sigla     varchar2,
      p_agregados out number
   );

    -- liga a versao: resultados viram vigentes e a anterior deixa de ser
   procedure publicar (
      p_ver_id     number,
      p_publicados out number
   );

end pkg_calculo;
/

create or replace package body pkg_calculo as

   function id_indicador (
      p_sigla varchar2
   ) return number is
      v_id number;
   begin
      select ind_id
        into v_id
        from indicador
       where ind_sigla = p_sigla;
      return v_id;
   exception
      when no_data_found then
         raise_application_error(
            -20040,
            'Indicador '
            || p_sigla
            || ' nao cadastrado.'
         );
   end id_indicador;

   function imoveis_no_escopo (
      p_ver_id number,
      p_escopo varchar2
   ) return sys.odcinumberlist is
      v_ids sys.odcinumberlist;
   begin
      if p_escopo = 'IMOVEL' then
         select i.imo_id
         bulk collect
           into v_ids
           from imovel_rural i
          where i.ver_id = p_ver_id;

      elsif p_escopo = 'RESERVA_LEGAL' then
         select distinct r.imo_id
         bulk collect
           into v_ids
           from reserva_legal r
          where r.ver_id = p_ver_id;

      elsif p_escopo = 'EMBARGO' then
         select distinct i.imo_id
         bulk collect
           into v_ids
           from imovel_rural i
          where i.imo_geometria is not null
            and exists (
            select 1
              from embargo b
             where b.ver_id = p_ver_id
               and b.emb_geometria is not null
               and sdo_relate(
               b.emb_geometria,
               i.imo_geometria,
               'mask=ANYINTERACT'
            ) = 'TRUE'
         );
      elsif p_escopo = 'VEGETACAO' then
         select distinct i.imo_id
         bulk collect
           into v_ids
           from imovel_rural i
          where i.imo_geometria is not null
            and exists (
            select 1
              from vegetacao_nativa v
             where v.ver_id = p_ver_id
               and v.veg_geometria is not null
               and sdo_relate(
               v.veg_geometria,
               i.imo_geometria,
               'mask=ANYINTERACT'
            ) = 'TRUE'
         );
      elsif p_escopo = 'DESMATAMENTO' then
         select distinct i.imo_id
         bulk collect
           into v_ids
           from imovel_rural i
          where i.imo_geometria is not null
            and exists (
            select 1
              from desmatamento d
             where d.ver_id = p_ver_id
               and d.des_geometria is not null
               and sdo_relate(
               d.des_geometria,
               i.imo_geometria,
               'mask=ANYINTERACT'
            ) = 'TRUE'
         );
      else
         raise_application_error(
            -20041,
            'Escopo de calculo desconhecido: '
            || p_escopo
            || '. Esperado IMOVEL, RESERVA_LEGAL, VEGETACAO ou EMBARGO.'
         );
      end if;

      return nvl(
         v_ids,
         sys.odcinumberlist()
      );
   end imoveis_no_escopo;

   procedure calcular_irl (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_minimo_pct number,
      p_escopo     varchar2,
      p_calculados out number
   ) is
      v_ids sys.odcinumberlist := imoveis_no_escopo(
         p_ver_id,
         p_escopo
      );
      v_ind number := id_indicador('IRL');
   begin
      if v_ids.count = 0 then
         p_calculados := 0;
         return;
      end if;
      merge into resultado_indicador d
      using (
         select o.imo_id,
                v_ind as ind_id,
                o.pct,
                o.area_rl,
                o.area_rl - o.minimo_ha as saldo_ha,
                case
                   when o.pct >= p_minimo_pct then
                      'CONFORME'
                   else
                      'DEFICIT'
                end as situacao,
                '{"area_imovel_ha":'
                || to_char(round(
                   o.area_imovel,
                   4
                ))
                || ',"area_rl_ha":'
                || to_char(round(
                   o.area_rl,
                   4
                ))
                || ',"minimo_pct":'
                || to_char(p_minimo_pct)
                || ',"minimo_ha":'
                || to_char(round(
                   o.minimo_ha,
                   4
                ))
                || ',"saldo_ha":'
                || to_char(round(
                   o.area_rl - o.minimo_ha,
                   4
                ))
                || ',"reservas_consideradas":'
                || to_char(o.reservas)
                || '}' as detalhes
           from (
            select i.imo_id,
                   nvl(
                      i.imo_area_total,
                      0
                   ) as area_imovel,
                   nvl(
                      i.imo_area_total,
                      0
                   ) * p_minimo_pct / 100 as minimo_ha,
                   nvl(
                      (
                         select sum(r.rle_area_ha)
                           from reserva_legal r
                          where r.imo_id = i.imo_id
                      ),
                      0
                   ) as area_rl,
                   nvl(
                      (
                         select count(*)
                           from reserva_legal r
                          where r.imo_id = i.imo_id
                      ),
                      0
                   ) as reservas,
                   nvl(
                      (
                         select sum(r.rle_area_ha)
                           from reserva_legal r
                          where r.imo_id = i.imo_id
                      ),
                      0
                   ) / nullif(
                      i.imo_area_total,
                      0
                   ) * 100 as pct
              from imovel_rural i
             where i.imo_id in (
               select column_value
                 from table ( v_ids )
            )
         ) o
      ) f on ( d.imo_id = f.imo_id
         and d.ind_id = f.ind_id
         and d.ver_id = p_ver_id )
      when matched then update
      set d.rin_valor_percentual = f.pct,
          d.rin_valor_hectares = f.area_rl,
          d.rin_valor_absoluto = f.saldo_ha,
          d.rin_situacao = f.situacao,
          d.rin_detalhes = f.detalhes,
          d.rin_data_calculo = systimestamp,
          d.exe_id = p_exe_id,
          d.rca_id = p_rca_id
      when not matched then
      insert (
         imo_id,
         ind_id,
         ver_id,
         exe_id,
         rca_id,
         rin_valor_percentual,
         rin_valor_hectares,
         rin_valor_absoluto,
         rin_situacao,
         rin_detalhes,
         rin_data_calculo,
         rin_vigente )
      values
         ( f.imo_id,
           f.ind_id,
           p_ver_id,
           p_exe_id,
           p_rca_id,
           f.pct,
           f.area_rl,
           f.saldo_ha,
           f.situacao,
           f.detalhes,
           systimestamp,
           'N' );
      p_calculados := sql%rowcount;
   end calcular_irl;

   procedure calcular_iae (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_escopo     varchar2,
      p_calculados out number
   ) is
      v_ids sys.odcinumberlist := imoveis_no_escopo(
         p_ver_id,
         p_escopo
      );
      v_ind number := id_indicador('IAE');
   begin
      if v_ids.count = 0 then
         p_calculados := 0;
         return;
      end if;
      merge into resultado_indicador d
      using (
         select o.imo_id,
                v_ind as ind_id,
                o.area_embargada / nullif(
                   o.area_imovel,
                   0
                ) * 100 as pct,
                o.area_embargada,
                case
                   when o.area_embargada > 0 then
                      'COM_EMBARGO'
                   else
                      'SEM_EMBARGO'
                end as situacao,
                '{"area_imovel_ha":'
                || to_char(round(
                   o.area_imovel,
                   4
                ))
                || ',"area_embargada_ha":'
                || to_char(round(
                   o.area_embargada,
                   4
                ))
                || ',"embargos_sobrepostos":'
                || to_char(o.embargos)
                || '}' as detalhes
           from (
            select i.imo_id,
                   nvl(
                      i.imo_area_total,
                      0
                   ) as area_imovel,
                   nvl(
                      (
                         select sum(pkg_geo.area_intersecao_ha(
                            i.imo_geometria,
                            b.emb_geometria
                         ))
                           from embargo b
                          where b.emb_geometria is not null
                            and sdo_relate(
                            b.emb_geometria,
                            i.imo_geometria,
                            'mask=ANYINTERACT'
                         ) = 'TRUE'
                      ),
                      0
                   ) as area_embargada,
                   nvl(
                      (
                         select count(*)
                           from embargo b
                          where b.emb_geometria is not null
                            and sdo_relate(
                            b.emb_geometria,
                            i.imo_geometria,
                            'mask=ANYINTERACT'
                         ) = 'TRUE'
                      ),
                      0
                   ) as embargos
              from imovel_rural i
             where i.imo_id in (
               select column_value
                 from table ( v_ids )
            )
         ) o
      ) f on ( d.imo_id = f.imo_id
         and d.ind_id = f.ind_id
         and d.ver_id = p_ver_id )
      when matched then update
      set d.rin_valor_percentual = f.pct,
          d.rin_valor_hectares = f.area_embargada,
          d.rin_valor_absoluto = f.area_embargada,
          d.rin_situacao = f.situacao,
          d.rin_detalhes = f.detalhes,
          d.rin_data_calculo = systimestamp,
          d.exe_id = p_exe_id,
          d.rca_id = p_rca_id
      when not matched then
      insert (
         imo_id,
         ind_id,
         ver_id,
         exe_id,
         rca_id,
         rin_valor_percentual,
         rin_valor_hectares,
         rin_valor_absoluto,
         rin_situacao,
         rin_detalhes,
         rin_data_calculo,
         rin_vigente )
      values
         ( f.imo_id,
           f.ind_id,
           p_ver_id,
           p_exe_id,
           p_rca_id,
           f.pct,
           f.area_embargada,
           f.area_embargada,
           f.situacao,
           f.detalhes,
           systimestamp,
           'N' );
      p_calculados := sql%rowcount;
   end calcular_iae;

   procedure calcular_icv (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_minimo_pct number,
      p_escopo     varchar2,
      p_calculados out number
   ) is
      v_ids sys.odcinumberlist := imoveis_no_escopo(
         p_ver_id,
         p_escopo
      );
      v_ind number := id_indicador('ICV');
   begin
      if v_ids.count = 0 then
         p_calculados := 0;
         return;
      end if;
      merge into resultado_indicador d
      using (
         select o.imo_id,
                v_ind as ind_id,
                least(
                   o.area_vegetacao / nullif(
                      o.area_imovel,
                      0
                   ) * 100,
                   100
                ) as pct,
                o.area_vegetacao,
                case
                   when o.area_vegetacao / nullif(
                      o.area_imovel,
                      0
                   ) * 100 >= p_minimo_pct then
                      'CONSERVADO'
                   else
                      'DEGRADADO'
                end as situacao,
                '{"area_imovel_ha":'
                || to_char(round(
                   o.area_imovel,
                   4
                ))
                || ',"area_vegetacao_ha":'
                || to_char(round(
                   o.area_vegetacao,
                   4
                ))
                || ',"minimo_pct":'
                || to_char(p_minimo_pct)
                || ',"poligonos_considerados":'
                || to_char(o.poligonos)
                || ',"dissolvido":true}' as detalhes
           from (
            select i.imo_id,
                   nvl(
                      i.imo_area_total,
                      0
                   ) as area_imovel,
                   nvl(
                      pkg_geo.area_intersecao_ha(
                         i.imo_geometria,
                         (
                            select sdo_aggr_union(sdoaggrtype(
                               v.veg_geometria,
                               0.05
                            ))
                              from vegetacao_nativa v
                             where v.veg_geometria is not null
                               and sdo_relate(
                               v.veg_geometria,
                               i.imo_geometria,
                               'mask=ANYINTERACT'
                            ) = 'TRUE'
                         )
                      ),
                      0
                   ) as area_vegetacao,
                   nvl(
                      (
                         select count(*)
                           from vegetacao_nativa v
                          where v.veg_geometria is not null
                            and sdo_relate(
                            v.veg_geometria,
                            i.imo_geometria,
                            'mask=ANYINTERACT'
                         ) = 'TRUE'
                      ),
                      0
                   ) as poligonos
              from imovel_rural i
             where i.imo_id in (
               select column_value
                 from table ( v_ids )
            )
         ) o
      ) f on ( d.imo_id = f.imo_id
         and d.ind_id = f.ind_id
         and d.ver_id = p_ver_id )
      when matched then update
      set d.rin_valor_percentual = f.pct,
          d.rin_valor_hectares = f.area_vegetacao,
          d.rin_valor_absoluto = f.area_vegetacao,
          d.rin_situacao = f.situacao,
          d.rin_detalhes = f.detalhes,
          d.rin_data_calculo = systimestamp,
          d.exe_id = p_exe_id,
          d.rca_id = p_rca_id
      when not matched then
      insert (
         imo_id,
         ind_id,
         ver_id,
         exe_id,
         rca_id,
         rin_valor_percentual,
         rin_valor_hectares,
         rin_valor_absoluto,
         rin_situacao,
         rin_detalhes,
         rin_data_calculo,
         rin_vigente )
      values
         ( f.imo_id,
           f.ind_id,
           p_ver_id,
           p_exe_id,
           p_rca_id,
           f.pct,
           f.area_vegetacao,
           f.area_vegetacao,
           f.situacao,
           f.detalhes,
           systimestamp,
           'N' );
      p_calculados := sql%rowcount;
   end calcular_icv;

   procedure calcular_isap (
      p_ver_id        number,
      p_exe_id        number,
      p_rca_id        number,
      p_escopo        varchar2,
      p_calculados    out number,
      p_camada_ver_id number default null
   ) is
      v_ind         number := id_indicador('ISAP');
      v_escopo_lote varchar2(20);
   begin
      if v_ind is null then
         p_calculados := 0;
         return;
      end if;
      v_escopo_lote :=
         case upper(nvl(
            p_escopo,
            'TODOS'
         ))
            when 'IMOVEL' then
               'IMOVEL'
            else
               'TODOS'
         end;

      pkg_isap.calcular_lote(
         p_ver_id        => p_ver_id,
         p_camada_ver_id => p_camada_ver_id,
         p_exe_id        => p_exe_id,
         p_rca_id        => p_rca_id,
         p_escopo        => v_escopo_lote,
         p_calculados    => p_calculados
      );
   end calcular_isap;

   procedure calcular_iapp (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_escopo     varchar2,
      p_calculados out number,
      p_app_ver_id number default null,
      p_veg_ver_id number default null
   ) is
      v_ind         number := id_indicador('IAPP');
      v_escopo_lote varchar2(20);
   begin
      if v_ind is null then
         p_calculados := 0;
         return;
      end if;
      v_escopo_lote :=
         case upper(nvl(
            p_escopo,
            'TODOS'
         ))
            when 'IMOVEL' then
               'IMOVEL'
            else
               'TODOS'
         end;

      pkg_iapp.calcular_lote(
         p_ver_id     => p_ver_id,
         p_app_ver_id => p_app_ver_id,
         p_veg_ver_id => p_veg_ver_id,
         p_exe_id     => p_exe_id,
         p_rca_id     => p_rca_id,
         p_escopo     => v_escopo_lote,
         p_calculados => p_calculados
      );
   end calcular_iapp;

   procedure agregar_municipio (
      p_ver_id    number,
      p_sigla     varchar2,
      p_agregados out number
   ) is
      v_ind number := id_indicador(p_sigla);
      v_boa varchar2(20) :=
         case p_sigla
            when 'IAE' then
               'SEM_EMBARGO'
            when 'ICV' then
               'CONSERVADO'
            else
               'CONFORME'
         end;
   begin
      merge into resultado_municipio d
      using (
         select i.mun_id,
                v_ind as ind_id,
                avg(r.rin_valor_percentual) as media_pct,
                '{"imoveis":'
                || to_char(count(*))
                || ',"media_percentual":'
                || to_char(round(
                   avg(r.rin_valor_percentual),
                   4
                ))
                || ',"soma_hectares":'
                || to_char(round(
                   nvl(
                      sum(r.rin_valor_hectares),
                      0
                   ),
                   4
                ))
                || ',"conformes":'
                || to_char(sum(
                   case
                      when r.rin_situacao = v_boa then
                         1
                      else
                         0
                   end
                ))
                || ',"situacao_boa":"'
                || v_boa
                || '"'
                || '}' as detalhes,
                count(*) as imoveis
           from resultado_indicador r
           join imovel_rural i
         on i.imo_id = r.imo_id
          where r.ver_id = p_ver_id
            and r.ind_id = v_ind
            and i.mun_id is not null
          group by i.mun_id
      ) f on ( d.mun_id = f.mun_id
         and d.ind_id = f.ind_id
         and d.ver_id = p_ver_id )
      when matched then update
      set d.rmu_valor_agregado = f.media_pct,
          d.rmu_detalhes = f.detalhes,
          d.rmu_data_calculo = systimestamp
      when not matched then
      insert (
         mun_id,
         ind_id,
         ver_id,
         rmu_valor_agregado,
         rmu_situacao,
         rmu_detalhes,
         rmu_vigente,
         rmu_data_calculo )
      values
         ( f.mun_id,
           f.ind_id,
           p_ver_id,
           f.media_pct,
           'CALCULADO',
           f.detalhes,
           'N',
           systimestamp );
      p_agregados := sql%rowcount;
   end agregar_municipio;

   procedure publicar (
      p_ver_id     number,
      p_publicados out number
   ) is
      v_con_id   number;
      v_isap_pub number := 0;
      v_iapp_pub number := 0;
   begin
      select con_id
        into v_con_id
        from versao
       where ver_id = p_ver_id;

      update resultado_indicador r
         set
         r.rin_vigente = 'N'
       where r.rin_vigente = 'S'
         and r.ver_id in (
         select ver_id
           from versao
          where con_id = v_con_id
            and ver_id <> p_ver_id
      );

      update resultado_municipio m
         set
         m.rmu_vigente = 'N'
       where m.rmu_vigente = 'S'
         and m.ver_id in (
         select ver_id
           from versao
          where con_id = v_con_id
            and ver_id <> p_ver_id
      );

      update resultado_indicador r
         set
         r.rin_vigente = 'S'
       where r.ver_id = p_ver_id;
      p_publicados := sql%rowcount;
      update resultado_municipio m
         set
         m.rmu_vigente = 'S'
       where m.ver_id = p_ver_id;

      pkg_isap.publicar(
         p_ver_id,
         v_isap_pub
      );
      p_publicados := p_publicados + nvl(
         v_isap_pub,
         0
      );
      pkg_iapp.publicar(
         p_ver_id,
         v_iapp_pub
      );
      p_publicados := p_publicados + nvl(
         v_iapp_pub,
         0
      );
   end publicar;

end pkg_calculo;
/