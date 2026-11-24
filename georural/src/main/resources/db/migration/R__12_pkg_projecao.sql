-- =====================================================================
-- GeoRural DataHub - PKG_PROJECAO
-- Copia o resultado do IAPP e do ISAP para a RESULTADO_INDICADOR.
--
-- Por que existe: o IAPP grava na RESULTADO_IAPP e o ISAP na
-- RESULTADO_ISAP_RESUMO, cada um com as suas colunas. Todos os endpoints
-- e telas de consulta (resultados, metricas, series, mapa, municipio)
-- leem a RESULTADO_INDICADOR. Sem esta copia, dois dos sete indicadores
-- calculam e nao aparecem em lugar nenhum.
--
-- Chamada pela etapa CALCULO depois de cada um dos dois.
-- =====================================================================

create or replace package pkg_projecao as

   -- copia o resultado de uma sigla para a RESULTADO_INDICADOR
   procedure projetar (
      p_ver_id    number,
      p_exe_id    number,
      p_sigla     varchar2,
      p_gravados  out number
   );

   -- As duas abaixo estao na especificacao de proposito: elas sao chamadas de
   -- dentro de um SELECT, e o Oracle so resolve funcao de pacote em SQL pela
   -- interface publica (senao da PLS-00231).

   -- numero para texto JSON; nulo sai como o literal null, nao como vazio
   function num_json (
      p_valor number
   ) return varchar2;

   -- texto para texto JSON, com aspas e escape
   function txt_json (
      p_valor varchar2
   ) return varchar2;

end pkg_projecao;
/

create or replace package body pkg_projecao as

   -- mascara que nao depende do NLS da sessao: sem ela o separador decimal
   -- pode sair virgula e o CLOB quebra o CHECK (rin_detalhes IS JSON)
   c_mascara constant varchar2(40) := 'FM99999999999999990.0999999';
   c_nls     constant varchar2(40) := 'NLS_NUMERIC_CHARACTERS = ''.,''';

   -- numero para texto JSON; nulo sai como o literal null, nao como vazio
   function num_json (
      p_valor number
   ) return varchar2 is
   begin
      if p_valor is null then
         return 'null';
      end if;
      return to_char(p_valor, c_mascara, c_nls);
   end num_json;

   -- texto para texto JSON, com as aspas e o escape do que quebraria o JSON
   function txt_json (
      p_valor varchar2
   ) return varchar2 is
   begin
      if p_valor is null then
         return 'null';
      end if;
      return '"' || replace(replace(p_valor, '\', '\\'), '"', '\"') || '"';
   end txt_json;

   function id_sigla (
      p_sigla varchar2
   ) return number is
      v_id number;
   begin
      select i.ind_id
        into v_id
        from indicador i
       where upper(i.ind_sigla) = upper(p_sigla);
      return v_id;
   exception
      when no_data_found then
         return null;
   end id_sigla;

   -- -----------------------------------------------------------------
   -- IAPP: o percentual e a conformidade (app vegetada / app total),
   -- por isso vai direto em rin_valor_percentual sem inverter sinal.
   -- O passivo em hectares vai em rin_valor_hectares.
   -- -----------------------------------------------------------------
   procedure projetar_iapp (
      p_ver_id   number,
      p_exe_id   number,
      p_ind_id   number,
      p_gravados out number
   ) is
   begin
      merge into resultado_indicador d
      using (
         select a.imo_id,
                p_ind_id                        as ind_id,
                a.ver_id,
                nvl(a.exe_id, p_exe_id)         as exe_id,
                a.rca_id,
                a.iap_iapp_pct                  as pct,
                -- hectares = app vegetada e absoluto = passivo: e o mesmo
                -- mapeamento que o ImovelIndicadoresService fazia em Java,
                -- mantido para a tela nao mudar de numero
                a.iap_app_vegetada_ha           as ha,
                a.iap_passivo_ha                as absoluto,
                a.iap_status                    as situacao,
                '{"app_total_ha":'              || num_json(a.iap_app_total_ha)
                || ',"app_vegetada_ha":'        || num_json(a.iap_app_vegetada_ha)
                || ',"passivo_ha":'             || num_json(a.iap_passivo_ha)
                || ',"conformidade_pct":'       || num_json(a.iap_iapp_pct)
                || ',"area_imovel_ha":'         || num_json(a.iap_area_imovel_ha)
                || ',"filtro_tipos_app":'       || txt_json(a.iap_filtro_tipos_app)
                || ',"filtro_classes_veg":'     || txt_json(a.iap_filtro_classes_veg)
                || ',"competencia_app":'        || txt_json(a.iap_competencia_app)
                || ',"competencia_veg":'        || txt_json(a.iap_competencia_veg)
                || ',"mensagem":'               || txt_json(a.iap_mensagem)
                || ',"origem":"RESULTADO_IAPP"}' as detalhes
           from resultado_iapp a
          where a.ver_id = p_ver_id
      ) s
      on ( d.imo_id = s.imo_id
       and d.ind_id = s.ind_id
       and d.ver_id = s.ver_id )
      when matched then update
         set d.exe_id               = s.exe_id,
             d.rca_id               = s.rca_id,
             d.rin_valor_percentual = s.pct,
             d.rin_valor_hectares   = s.ha,
             d.rin_valor_absoluto   = s.absoluto,
             d.rin_situacao         = s.situacao,
             d.rin_detalhes         = s.detalhes,
             d.rin_data_calculo     = systimestamp
      when not matched then insert (
         d.imo_id, d.ind_id, d.ver_id, d.exe_id, d.rca_id,
         d.rin_valor_percentual, d.rin_valor_hectares, d.rin_valor_absoluto,
         d.rin_situacao, d.rin_vigente, d.rin_detalhes
      ) values (
         s.imo_id, s.ind_id, s.ver_id, s.exe_id, s.rca_id,
         s.pct, s.ha, s.absoluto,
         s.situacao, 'N', s.detalhes
      );

      p_gravados := sql%rowcount;
   end projetar_iapp;

   -- -----------------------------------------------------------------
   -- ISAP: a RESULTADO_ISAP_RESUMO tem uma linha por categoria, e a
   -- RESULTADO_INDICADOR e unica por (imovel, indicador, versao) --
   -- entao so a categoria TOTAL vira resultado, e as categorias que
   -- de fato sobrepoem vao listadas no detalhe.
   -- -----------------------------------------------------------------
   procedure projetar_isap (
      p_ver_id   number,
      p_exe_id   number,
      p_ind_id   number,
      p_gravados out number
   ) is
   begin
      merge into resultado_indicador d
      using (
         select r.imo_id,
                p_ind_id                        as ind_id,
                r.ver_id,
                nvl(r.exe_id, p_exe_id)         as exe_id,
                r.rca_id,
                r.isr_pct_sobreposicao          as pct,
                r.isr_area_sobreposta_ha        as ha,
                r.isr_area_sobreposta_ha        as absoluto,
                r.isr_status                    as situacao,
                '{"area_sobreposta_ha":'        || num_json(r.isr_area_sobreposta_ha)
                || ',"pct_sobreposicao":'       || num_json(r.isr_pct_sobreposicao)
                || ',"area_imovel_ha":'         || num_json(r.isr_area_imovel_ha)
                || ',"sobrepoe":'               || txt_json(r.isr_sobrepoe)
                || ',"filtro_aplicado":'        || txt_json(r.isr_filtro_aplicado)
                || ',"competencia_camada":'     || txt_json(r.isr_competencia_camada)
                || ',"categorias_sobrepostas":' || txt_json(
                      ( select listagg(c.isr_categoria, ',')
                                  within group (order by c.isr_categoria)
                          from resultado_isap_resumo c
                         where c.imo_id        = r.imo_id
                           and c.ver_id        = r.ver_id
                           and c.isr_categoria <> 'TOTAL'
                           and c.isr_sobrepoe   = 'S' ) )
                || ',"origem":"RESULTADO_ISAP_RESUMO"}' as detalhes
           from resultado_isap_resumo r
          where r.ver_id        = p_ver_id
            and r.isr_categoria = 'TOTAL'
      ) s
      on ( d.imo_id = s.imo_id
       and d.ind_id = s.ind_id
       and d.ver_id = s.ver_id )
      when matched then update
         set d.exe_id               = s.exe_id,
             d.rca_id               = s.rca_id,
             d.rin_valor_percentual = s.pct,
             d.rin_valor_hectares   = s.ha,
             d.rin_valor_absoluto   = s.absoluto,
             d.rin_situacao         = s.situacao,
             d.rin_detalhes         = s.detalhes,
             d.rin_data_calculo     = systimestamp
      when not matched then insert (
         d.imo_id, d.ind_id, d.ver_id, d.exe_id, d.rca_id,
         d.rin_valor_percentual, d.rin_valor_hectares, d.rin_valor_absoluto,
         d.rin_situacao, d.rin_vigente, d.rin_detalhes
      ) values (
         s.imo_id, s.ind_id, s.ver_id, s.exe_id, s.rca_id,
         s.pct, s.ha, s.absoluto,
         s.situacao, 'N', s.detalhes
      );

      p_gravados := sql%rowcount;
   end projetar_isap;

   procedure projetar (
      p_ver_id   number,
      p_exe_id   number,
      p_sigla    varchar2,
      p_gravados out number
   ) is
      v_ind number := id_sigla(p_sigla);
   begin
      p_gravados := 0;
      if v_ind is null or p_ver_id is null then
         return;
      end if;

      case upper(p_sigla)
         when 'IAPP' then
            projetar_iapp(p_ver_id, p_exe_id, v_ind, p_gravados);
         when 'ISAP' then
            projetar_isap(p_ver_id, p_exe_id, v_ind, p_gravados);
         else
            -- os outros cinco ja gravam direto na RESULTADO_INDICADOR
            p_gravados := 0;
      end case;
   end projetar;

end pkg_projecao;
/
