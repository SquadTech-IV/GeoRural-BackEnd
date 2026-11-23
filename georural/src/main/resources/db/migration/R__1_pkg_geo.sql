-- PKG_GEO (repetivel): operacoes geoespaciais no banco
create or replace package pkg_geo as
   c_tol constant number := 0.05;
   function area_ha (
      p_geom sdo_geometry
   ) return number;
   function area_intersecao_ha (
      p_a sdo_geometry,
      p_b sdo_geometry
   ) return number;
   function intersecao (
      p_a sdo_geometry,
      p_b sdo_geometry
   ) return sdo_geometry;

    -- limite da UF, com cache de sessao
   function limite_uf (
      p_uf varchar2 default 'PR'
   ) return sdo_geometry;
   procedure limpar_cache;

   function recorte_uf (
      p_geom sdo_geometry,
      p_uf   varchar2 default 'PR'
   ) return sdo_geometry;
   function dentro_da_uf (
      p_geom sdo_geometry,
      p_uf   varchar2 default 'PR'
   ) return varchar2;

    -- 'TRUE' quando a geometria esta inteiramente dentro da UF
   function contida_na_uf (
      p_geom sdo_geometry,
      p_uf   varchar2 default 'PR'
   ) return varchar2;

   function saneada (
      p_geom sdo_geometry
   ) return sdo_geometry;
   function valida (
      p_geom sdo_geometry
   ) return varchar2;

    -- coordenadas de um ponto; o recorte por SDO_INTERSECTION devolve o ponto sem
    -- SDO_POINT (em SDO_ORDINATES), por isso os dois lugares sao lidos
   function ponto_x (
      p_geom sdo_geometry
   ) return number;
   function ponto_y (
      p_geom sdo_geometry
   ) return number;
   function area_equivalente_ha (
      p_geom    sdo_geometry,
      p_srid_eq number default 102033
   ) return number;
end pkg_geo;
/

create or replace package body pkg_geo as

   g_uf_codigo varchar2(20);
   g_uf_geom   sdo_geometry;

   procedure limpar_cache is
   begin
      g_uf_codigo := null;
      g_uf_geom := null;
   end limpar_cache;

   function limite_uf (
      p_uf varchar2 default 'PR'
   ) return sdo_geometry is
   begin
      if
         g_uf_codigo = p_uf
         and g_uf_geom is not null
      then
         return g_uf_geom;
      end if;
      select lim_geometria
        into g_uf_geom
        from limite_territorial
       where lim_tipo = 'UF'
         and lim_codigo = p_uf;

      g_uf_codigo := p_uf;
      return g_uf_geom;
   exception
      when no_data_found then
         g_uf_codigo := null;
         g_uf_geom := null;
         raise_application_error(
            -20020,
            'Limite da UF '
            || p_uf
            || ' nao carregado em LIMITE_TERRITORIAL. '
            || 'Processe a malha municipal do IBGE e chame '
            || 'POST /api/pipeline/limite-territorial/recarregar.'
         );
   end limite_uf;

    -- Corrige geometrias invalidas (auto-intersecao, orientacao de aneis).
   function saneada (
      p_geom sdo_geometry
   ) return sdo_geometry is
   begin
      if p_geom is null then
         return null;
      end if;
      return sdo_util.rectify_geometry(
         p_geom,
         c_tol
      );
   end saneada;

    -- Area em hectares. NULL se a geometria for nula.
   function area_ha (
      p_geom sdo_geometry
   ) return number is
   begin
      if p_geom is null then
         return null;
      end if;
      return sdo_geom.sdo_area(
         p_geom,
         c_tol,
         'unit=HECTARE'
      );
   exception
      when others then
         begin
            return sdo_geom.sdo_area(
               saneada(p_geom),
               c_tol,
               'unit=HECTARE'
            );
         exception
            when others then
               return null;
         end;
   end area_ha;

   function intersecao (
      p_a sdo_geometry,
      p_b sdo_geometry
   ) return sdo_geometry is
   begin
      if p_a is null
      or p_b is null then
         return null;
      end if;
      return sdo_geom.sdo_intersection(
         p_a,
         p_b,
         c_tol
      );
   exception
      when others then
         begin
            return sdo_geom.sdo_intersection(
               saneada(p_a),
               saneada(p_b),
               c_tol
            );
         exception
            when others then
               return null;
         end;
   end intersecao;

    -- Area sobreposta entre duas geometrias, em hectares. 0 quando nao se tocam.
   function area_intersecao_ha (
      p_a sdo_geometry,
      p_b sdo_geometry
   ) return number is
      v_inter sdo_geometry;
   begin
      if p_a is null
      or p_b is null then
         return 0;
      end if;
      if sdo_geom.relate(
         p_a,
         'ANYINTERACT',
         p_b,
         c_tol
      ) <> 'TRUE' then
         return 0;
      end if;
      v_inter := intersecao(
         p_a,
         p_b
      );
      if v_inter is null then
         return 0;
      end if;
      return nvl(
         area_ha(v_inter),
         0
      );
   exception
      when others then
         return 0;
   end area_intersecao_ha;

    -- RELATE decide sem construir geometria nova
   function contida_na_uf (
      p_geom sdo_geometry,
      p_uf   varchar2 default 'PR'
   ) return varchar2 is
      v_limite sdo_geometry;
   begin
      if p_geom is null then
         return 'FALSE';
      end if;
      v_limite := limite_uf(p_uf);
      if sdo_geom.relate(
         v_limite,
         'CONTAINS+COVERS',
         p_geom,
         c_tol
      ) = 'FALSE' then
         return 'FALSE';
      end if;
      return 'TRUE';
   exception
      when others then
         return 'FALSE';
   end contida_na_uf;

   function recorte_uf (
      p_geom sdo_geometry,
      p_uf   varchar2 default 'PR'
   ) return sdo_geometry is
   begin
      if p_geom is null then
         return null;
      end if;
      return intersecao(
         p_geom,
         limite_uf(p_uf)
      );
   end recorte_uf;

   function dentro_da_uf (
      p_geom sdo_geometry,
      p_uf   varchar2 default 'PR'
   ) return varchar2 is
   begin
      if p_geom is null then
         return 'FALSE';
      end if;
      return sdo_geom.relate(
         p_geom,
         'ANYINTERACT',
         limite_uf(p_uf),
         c_tol
      );
   exception
      when others then
         return 'FALSE';
   end dentro_da_uf;

   function valida (
      p_geom sdo_geometry
   ) return varchar2 is
   begin
      if p_geom is null then
         return 'NULA';
      end if;
      return sdo_geom.validate_geometry_with_context(
         p_geom,
         c_tol
      );
   end valida;

   function ponto_x (
      p_geom sdo_geometry
   ) return number is
   begin
      if p_geom is null then
         return null;
      end if;
      if p_geom.sdo_point is not null then
         return p_geom.sdo_point.x;
      end if;
      if
         p_geom.sdo_ordinates is not null
         and p_geom.sdo_ordinates.count >= 2
      then
         return p_geom.sdo_ordinates(1);
      end if;
      return null;
   end ponto_x;

   function ponto_y (
      p_geom sdo_geometry
   ) return number is
   begin
      if p_geom is null then
         return null;
      end if;
      if p_geom.sdo_point is not null then
         return p_geom.sdo_point.y;
      end if;
      if
         p_geom.sdo_ordinates is not null
         and p_geom.sdo_ordinates.count >= 2
      then
         return p_geom.sdo_ordinates(2);
      end if;
      return null;
   end ponto_y;

   function area_equivalente_ha (
      p_geom    sdo_geometry,
      p_srid_eq number default 102033
   ) return number is
      v_proj sdo_geometry;
   begin
      if p_geom is null then
         return null;
      end if;
      begin
         v_proj := sdo_cs.transform(
            p_geom,
            p_srid_eq
         );
         return sdo_geom.sdo_area(
            v_proj,
            c_tol
         ) / 10000;
      exception
         when others then
            return area_ha(p_geom);
      end;
   end area_equivalente_ha;

end pkg_geo;
/
