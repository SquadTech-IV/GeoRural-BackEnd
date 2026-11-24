-- =====================================================================
-- GeoRural DataHub - PKG_CALCULO
-- =====================================================================

create or replace package pkg_calculo as

    -- imoveis alcancados por esta rodada; escopo = IMOVEL, RESERVA_LEGAL, VEGETACAO ou EMBARGO
   function imoveis_no_escopo (
      p_ver_id number,
      p_escopo varchar2
   ) return sys.odcinumberlist;

   -- id do indicador pela sigla; usado tambem pelo PKG_CALCULO_ICV
   function id_indicador (
      p_sigla varchar2
   ) return number;

   procedure calcular_irl (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_minimo_pct number,
      p_escopo     varchar2,
      p_calculados out number
   );

   PROCEDURE calcular_idesmat(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                              p_escopo VARCHAR2, p_calculados OUT NUMBER);

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

   -- indice de focos de calor: focos no periodo / area * fator
   procedure calcular_ifc (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_inicio     date,
      p_fim        date,
      p_fator      number,
      p_escopo     varchar2,
      p_calculados out number
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
      -- a versao rotula o RESULTADO, nao o imovel. IMOVEL_RURAL tem uma linha por CAR
      -- (uk_imovel_car) e o carregar_imovel reescreve o ver_id dela a cada carga: filtrar
      -- por ver_id aqui tirava do calculo todo imovel que a ultima carga nao tocou.
      -- As camadas sao carregadas por delete-then-insert, entao o que esta na tabela e
      -- sempre a geracao corrente -- nao ha o que recortar por versao.
      -- p_ver_id fica na assinatura porque o chamador e o MERGE do resultado usam ela.
      if p_escopo = 'IMOVEL' then
         select i.imo_id
         bulk collect
           into v_ids
           from imovel_rural i;

      elsif p_escopo = 'RESERVA_LEGAL' then
         select distinct r.imo_id
         bulk collect
           into v_ids
           from reserva_legal r;

      elsif p_escopo = 'EMBARGO' then
         select distinct i.imo_id
         bulk collect
           into v_ids
           from imovel_rural i
          where i.imo_geometria is not null
            and exists (
            select 1
              from embargo b
             where b.emb_geometria is not null
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
             where v.veg_geometria is not null
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
             where d.des_geometria is not null
               and sdo_relate(
               d.des_geometria,
               i.imo_geometria,
               'mask=ANYINTERACT'
            ) = 'TRUE'
         );
      elsif p_escopo = 'APP' then
         select distinct a.imo_id
         bulk collect
           into v_ids
           from app a
          where a.imo_id is not null;

      elsif p_escopo = 'FOCO_CALOR' then
         select distinct i.imo_id
         bulk collect
           into v_ids
           from imovel_rural i
          where i.imo_geometria is not null
            and exists (
            select 1
              from foco_calor f
             where f.foc_geometria is not null
               and sdo_relate(
               f.foc_geometria,
               i.imo_geometria,
               'mask=ANYINTERACT'
            ) = 'TRUE'
         );
      else
         raise_application_error(
            -20041,
            'Escopo de calculo desconhecido: '
            || p_escopo
            || '. Esperado IMOVEL, RESERVA_LEGAL, VEGETACAO, EMBARGO, DESMATAMENTO, APP ou FOCO_CALOR.'
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
                || nvl(to_char(round(
                   o.area_imovel,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"area_rl_ha":'
                || nvl(to_char(round(
                   o.area_rl,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"minimo_pct":'
                || nvl(to_char(p_minimo_pct, 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"minimo_ha":'
                || nvl(to_char(round(
                   o.minimo_ha,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"saldo_ha":'
                || nvl(to_char(round(
                   o.area_rl - o.minimo_ha,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"reservas_consideradas":'
                || nvl(to_char(o.reservas, 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"dissolvido":true}' as detalhes
           from (
            select m.imo_id,
                   m.area_imovel,
                   m.minimo_ha,
                   m.area_rl,
                   m.reservas,
                   m.area_rl / nullif(
                      m.area_imovel,
                      0
                   ) * 100 as pct
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
                      -- area de reserva legal MEDIDA dentro do imovel. Somar rle_area_ha
                      -- contava duas vezes poligono sobreposto e contava inteira a reserva
                      -- que passa da divisa. Dissolve antes de medir e recorta pelo
                      -- perimetro -- o mesmo metodo do ICV. Sem geometria, usa a declarada.
                      case
                         when exists (
                            select 1
                              from reserva_legal r
                             where r.imo_id = i.imo_id
                               and r.rle_geometria is not null
                         ) then
                            nvl(
                               pkg_geo.area_intersecao_ha(
                                  i.imo_geometria,
                                  (
                                     select sdo_aggr_union(sdoaggrtype(
                                        r.rle_geometria,
                                        0.05
                                     ))
                                       from reserva_legal r
                                      where r.imo_id = i.imo_id
                                        and r.rle_geometria is not null
                                  )
                               ),
                               0
                            )
                         else
                            nvl(
                               (
                                  select sum(r.rle_area_ha)
                                    from reserva_legal r
                                   where r.imo_id = i.imo_id
                               ),
                               0
                            )
                      end as area_rl,
                      nvl(
                         (
                            select count(*)
                              from reserva_legal r
                             where r.imo_id = i.imo_id
                         ),
                         0
                      ) as reservas
                 from imovel_rural i
                where i.imo_id in (
                  select column_value
                    from table ( v_ids )
               )
              ) m
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
          d.rin_data_calculo = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
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
           CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
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
                || nvl(to_char(round(
                   o.area_imovel,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"area_embargada_ha":'
                || nvl(to_char(round(
                   o.area_embargada,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"embargos_sobrepostos":'
                || nvl(to_char(o.embargos, 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"dissolvido":true}' as detalhes
           from (
            select i.imo_id,
                   nvl(
                      i.imo_area_total,
                      0
                   ) as area_imovel,
                   -- dissolve os embargos antes de medir: somar a intersecao de cada um
                   -- contava duas vezes a parte onde dois embargos se sobrepoem
                   nvl(
                      pkg_geo.area_intersecao_ha(
                         i.imo_geometria,
                         (
                            select sdo_aggr_union(sdoaggrtype(
                               b.emb_geometria,
                               0.05
                            ))
                              from embargo b
                             where b.emb_geometria is not null
                               and sdo_relate(
                               b.emb_geometria,
                               i.imo_geometria,
                               'mask=ANYINTERACT'
                            ) = 'TRUE'
                         )
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
          d.rin_data_calculo = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
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
           CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
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
                || nvl(to_char(round(
                   o.area_imovel,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"area_vegetacao_ha":'
                || nvl(to_char(round(
                   o.area_vegetacao,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"minimo_pct":'
                || nvl(to_char(p_minimo_pct, 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"poligonos_considerados":'
                || nvl(to_char(o.poligonos, 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
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
          d.rin_data_calculo = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
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
           CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
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
      v_ind number := id_indicador('ISAP');
   begin
      if v_ind is null then
         p_calculados := 0;
         return;
      end if;
      -- p_escopo NAO estreita o conjunto de imoveis aqui. O pkg_isap e o
      -- pkg_iapp, quando recebem escopo diferente de 'TODOS', filtram
      -- imovel_rural.ver_id = p_ver_id -- e o carregar_imovel reescreve esse
      -- ver_id a cada carga (uma linha por CAR, uk_imovel_car), entao esse
      -- filtro derrubava todo imovel que a ultima carga nao tocou. A versao
      -- rotula o RESULTADO, nao o imovel: mesmo motivo do imoveis_no_escopo.
      pkg_isap.calcular_lote(
         p_ver_id        => p_ver_id,
         p_camada_ver_id => p_camada_ver_id,
         p_exe_id        => p_exe_id,
         p_rca_id        => p_rca_id,
         p_escopo        => 'TODOS',
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
      v_ind number := id_indicador('IAPP');
   begin
      if v_ind is null then
         p_calculados := 0;
         return;
      end if;
      -- p_escopo NAO estreita o conjunto de imoveis aqui. O pkg_isap e o
      -- pkg_iapp, quando recebem escopo diferente de 'TODOS', filtram
      -- imovel_rural.ver_id = p_ver_id -- e o carregar_imovel reescreve esse
      -- ver_id a cada carga (uma linha por CAR, uk_imovel_car), entao esse
      -- filtro derrubava todo imovel que a ultima carga nao tocou. A versao
      -- rotula o RESULTADO, nao o imovel: mesmo motivo do imoveis_no_escopo.
      pkg_iapp.calcular_lote(
         p_ver_id     => p_ver_id,
         p_app_ver_id => p_app_ver_id,
         p_veg_ver_id => p_veg_ver_id,
         p_exe_id     => p_exe_id,
         p_rca_id     => p_rca_id,
         p_escopo     => 'TODOS',
         p_calculados => p_calculados
      );
   end calcular_iapp;

   procedure calcular_ifc (
      p_ver_id     number,
      p_exe_id     number,
      p_rca_id     number,
      p_inicio     date,
      p_fim        date,
      p_fator      number,
      p_escopo     varchar2,
      p_calculados out number
   ) is
      v_ids   sys.odcinumberlist := imoveis_no_escopo(
         p_ver_id,
         p_escopo
      );
      v_ind   number := id_indicador('IFC');
      v_fator number := nvl(
         p_fator,
         1000
      );
   begin
      -- a conta: focos no periodo dividido pela area do imovel, vezes o fator.
      -- Veio do CalculoIFCService em Java, que era a unica implementacao e foi
      -- removido junto com as outras do pacote service: aqui e o unico lugar agora.
      -- focos no periodo / area do imovel * fator. Imovel sem area valida nao tem
      -- indice (null), para nao parecer "sem ocorrencia" com um zero.
      if v_ind is null then
         raise_application_error(
            -20043,
            'Indicador IFC nao cadastrado na tabela INDICADOR.'
         );
      end if;
      if v_ids.count = 0 then
         p_calculados := 0;
         return;
      end if;
      merge into resultado_indicador d
      using (
         select o.imo_id,
                v_ind as ind_id,
                case
                   when o.area_imovel > 0 then
                      round(
                         o.focos / o.area_imovel * v_fator,
                         4
                      )
                end as indice,
                o.focos,
                case
                   when o.area_imovel <= 0 then
                      'AREA_INVALIDA'
                   when o.focos > 0 then
                      'COM_FOCOS'
                   else
                      'SEM_FOCOS'
                end as situacao,
                '{"area_imovel_ha":'
                || nvl(to_char(round(
                   o.area_imovel,
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"focos":'
                || nvl(to_char(o.focos, 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"fator_area":'
                || nvl(to_char(v_fator, 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"periodo_inicio":"'
                || to_char(p_inicio, 'YYYY-MM-DD')
                || '","periodo_fim":"'
                || to_char(p_fim, 'YYYY-MM-DD')
                || '"}' as detalhes
           from (
            select i.imo_id,
                   nvl(
                      i.imo_area_total,
                      0
                   ) as area_imovel,
                   nvl(
                      (
                         select count(*)
                           from foco_calor f
                          where f.foc_geometria is not null
                            and f.foc_data between p_inicio and p_fim
                            and sdo_relate(
                            f.foc_geometria,
                            i.imo_geometria,
                            'mask=ANYINTERACT'
                         ) = 'TRUE'
                      ),
                      0
                   ) as focos
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
      set d.rin_valor_percentual = f.indice,
          d.rin_valor_absoluto = f.focos,
          d.rin_situacao = f.situacao,
          d.rin_detalhes = f.detalhes,
          d.rin_data_calculo = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
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
           f.indice,
           f.focos,
           f.situacao,
           f.detalhes,
           CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
           'N' );
      p_calculados := sql%rowcount;
   end calcular_ifc;

   procedure agregar_municipio (
      p_ver_id    number,
      p_sigla     varchar2,
      p_agregados out number
   ) is
      v_ind number := id_indicador(p_sigla);
      -- qual rin_situacao conta como "boa" em cada indicador. Faltavam IDESMAT, IFC,
      -- ISAP e IAPP: caiam no 'CONFORME' do else, que nenhum deles grava, e o campo
      -- conformes do rmu_detalhes vinha sempre zero na tela de municipios.
      v_boa varchar2(20) :=
         case p_sigla
            when 'IAE' then
               'SEM_EMBARGO'
            when 'ICV' then
               'CONSERVADO'
            when 'IDESMAT' then
               'SEM_DESMATAMENTO'
            when 'IFC' then
               'SEM_FOCOS'
            when 'ISAP' then
               'SEM_SOBREPOSICAO'
            when 'IAPP' then
               -- o IAPP grava CALCULADO/SEM_APP/CAMADA_INDISPONIVEL, que sao estados
               -- da apuracao e nao veredito de conformidade: a qualidade dele esta no
               -- percentual. Sem situacao boa, conformes fica 0 e situacao_boa vazia.
               null
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
                || nvl(to_char(count(*), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"media_percentual":'
                || nvl(to_char(round(
                   avg(r.rin_valor_percentual),
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"soma_hectares":'
                || nvl(to_char(round(
                   nvl(
                      sum(r.rin_valor_hectares),
                      0
                   ),
                   4
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"conformes":'
                || nvl(to_char(sum(
                   case
                      when r.rin_situacao = v_boa then
                         1
                      else
                         0
                   end
                ), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                || ',"situacao_boa":'
                -- o IAPP nao tem situacao boa (v_boa nulo): emite o literal null,
                -- que e JSON valido, em vez de uma string vazia que mentiria
                || case
                      when v_boa is null then
                         'null'
                      else
                         '"' || v_boa || '"'
                   end
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
          d.rmu_data_calculo = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP)
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
           CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP) );
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

   PROCEDURE calcular_idesmat(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                              p_escopo VARCHAR2, p_calculados OUT NUMBER) IS
       v_ids      SYS.ODCINUMBERLIST := imoveis_no_escopo(p_ver_id, p_escopo);
       v_ind      NUMBER := id_indicador('IDESMAT');
       v_m2008    DATE := DATE '2008-07-22';
       v_m2019    DATE := DATE '2019-07-31';
       v_m2020    DATE := DATE '2020-12-31';
   BEGIN
       IF v_ids.COUNT = 0 THEN
           p_calculados := 0;
           RETURN;
       END IF;

       FOR r IN (SELECT prc_nome, prc_valor FROM PARAMETRO_REGRA_CALCULO WHERE rca_id = p_rca_id) LOOP
           IF r.prc_nome = 'marco_2008' THEN v_m2008 := TO_DATE(SUBSTR(r.prc_valor, 1, 10), 'YYYY-MM-DD'); END IF;
           IF r.prc_nome = 'marco_2019' THEN v_m2019 := TO_DATE(SUBSTR(r.prc_valor, 1, 10), 'YYYY-MM-DD'); END IF;
           IF r.prc_nome = 'marco_2020' THEN v_m2020 := TO_DATE(SUBSTR(r.prc_valor, 1, 10), 'YYYY-MM-DD'); END IF;
       END LOOP;

       MERGE INTO RESULTADO_INDICADOR d
       USING (
           SELECT o.imo_id,
                  v_ind AS ind_id,
                  LEAST(o.ha_2008 / NULLIF(o.area_imovel, 0) * 100, 100) AS pct_2008,
                  LEAST(o.ha_2008, o.area_imovel) AS ha_2008,
                  CASE WHEN o.ha_2008 > 0 THEN 'COM_DESMATAMENTO' ELSE 'SEM_DESMATAMENTO' END AS situacao,
                  '{"area_imovel_ha":' || nvl(to_char(ROUND(o.area_imovel, 4), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                  || ',"dissolvido":true'
                  || ',"marcos":['
                  || '{"marco":"marco_2008","data":"' || TO_CHAR(v_m2008, 'YYYY-MM-DD')
                  || '","area_ha":' || nvl(to_char(ROUND(LEAST(o.ha_2008, o.area_imovel), 4), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                  || ',"percentual":' || nvl(to_char(ROUND(NVL(LEAST(o.ha_2008 / NULLIF(o.area_imovel, 0) * 100, 100), 0), 4), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null') || '},'
                  || '{"marco":"marco_2019","data":"' || TO_CHAR(v_m2019, 'YYYY-MM-DD')
                  || '","area_ha":' || nvl(to_char(ROUND(LEAST(o.ha_2019, o.area_imovel), 4), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                  || ',"percentual":' || nvl(to_char(ROUND(NVL(LEAST(o.ha_2019 / NULLIF(o.area_imovel, 0) * 100, 100), 0), 4), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null') || '},'
                  || '{"marco":"marco_2020","data":"' || TO_CHAR(v_m2020, 'YYYY-MM-DD')
                  || '","area_ha":' || nvl(to_char(ROUND(LEAST(o.ha_2020, o.area_imovel), 4), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null')
                  || ',"percentual":' || nvl(to_char(ROUND(NVL(LEAST(o.ha_2020 / NULLIF(o.area_imovel, 0) * 100, 100), 0), 4), 'FM99999999999999990.0999999', 'NLS_NUMERIC_CHARACTERS = ''.,'''), 'null') || '}'
                  || ']}' AS detalhes
             FROM (
                 SELECT i.imo_id,
                        -- mesma fonte dos outros seis indicadores. O imo_area_total e o
                        -- area_ha da propria geometria, gravado na carga: recalcular aqui
                        -- so faria o IDESMAT discordar dos demais e custaria uma medicao
                        -- de geometria por imovel em arquivo grande.
                        NVL(i.imo_area_total, 0) AS area_imovel,
                        NVL(PKG_GEO.area_ha(
                            PKG_GEO.intersecao(
                                i.imo_geometria,
                                (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(ds.des_geometria, 0.05))
                                   FROM DESMATAMENTO ds
                                  WHERE ds.des_geometria IS NOT NULL
                                    AND NVL(ds.des_data_corte, TO_DATE(ds.des_ano_ref || '-12-31', 'YYYY-MM-DD')) > v_m2008
                                    AND SDO_RELATE(ds.des_geometria, i.imo_geometria, 'mask=ANYINTERACT') = 'TRUE')
                            )), 0) AS ha_2008,
                        NVL(PKG_GEO.area_ha(
                            PKG_GEO.intersecao(
                                i.imo_geometria,
                                (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(ds.des_geometria, 0.05))
                                   FROM DESMATAMENTO ds
                                  WHERE ds.des_geometria IS NOT NULL
                                    AND NVL(ds.des_data_corte, TO_DATE(ds.des_ano_ref || '-12-31', 'YYYY-MM-DD')) > v_m2019
                                    AND SDO_RELATE(ds.des_geometria, i.imo_geometria, 'mask=ANYINTERACT') = 'TRUE')
                            )), 0) AS ha_2019,
                        NVL(PKG_GEO.area_ha(
                            PKG_GEO.intersecao(
                                i.imo_geometria,
                                (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(ds.des_geometria, 0.05))
                                   FROM DESMATAMENTO ds
                                  WHERE ds.des_geometria IS NOT NULL
                                    AND NVL(ds.des_data_corte, TO_DATE(ds.des_ano_ref || '-12-31', 'YYYY-MM-DD')) > v_m2020
                                    AND SDO_RELATE(ds.des_geometria, i.imo_geometria, 'mask=ANYINTERACT') = 'TRUE')
                            )), 0) AS ha_2020
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
                      d.rin_data_calculo     = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP),
                      d.exe_id               = p_exe_id,
                      d.rca_id               = p_rca_id
       WHEN NOT MATCHED THEN
           INSERT (imo_id, ind_id, ver_id, exe_id, rca_id, rin_valor_percentual,
                   rin_valor_hectares, rin_valor_absoluto, rin_situacao, rin_detalhes,
                   rin_data_calculo, rin_vigente)
           VALUES (f.imo_id, f.ind_id, p_ver_id, p_exe_id, p_rca_id, f.pct_2008,
                   f.ha_2008, f.ha_2008, f.situacao, f.detalhes,
                   CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP), 'N');

       p_calculados := SQL%ROWCOUNT;
   END calcular_idesmat;

end pkg_calculo;
/