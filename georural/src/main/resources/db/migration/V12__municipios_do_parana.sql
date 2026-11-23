-- US#21 · nomes dos 399 municipios do Parana (fonte: IBGE, codigo de 7 digitos)
-- Entra so o par codigo + nome. A geometria e a area continuam nulas e sao preenchidas
-- depois, pelo MERGE de PKG_TRATAMENTO.carregar_municipio, quando a malha municipal for
-- processada: ele casa por mun_codigo_origem e faz UPDATE em vez de INSERT.
-- MERGE sem WHEN MATCHED: nunca sobrescreve um municipio que ja veio de arquivo.

MERGE INTO MUNICIPIO d
USING (
      SELECT '4100103' cod, 'Abatiá' nome FROM DUAL
      UNION ALL
      SELECT '4100202' cod, 'Adrianópolis' nome FROM DUAL
      UNION ALL
      SELECT '4100301' cod, 'Agudos do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4100400' cod, 'Almirante Tamandaré' nome FROM DUAL
      UNION ALL
      SELECT '4100459' cod, 'Altamira do Paraná' nome FROM DUAL
      UNION ALL
      SELECT '4100509' cod, 'Altônia' nome FROM DUAL
      UNION ALL
      SELECT '4100608' cod, 'Alto Paraná' nome FROM DUAL
      UNION ALL
      SELECT '4100707' cod, 'Alto Piquiri' nome FROM DUAL
      UNION ALL
      SELECT '4100806' cod, 'Alvorada do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4100905' cod, 'Amaporã' nome FROM DUAL
      UNION ALL
      SELECT '4101002' cod, 'Ampére' nome FROM DUAL
      UNION ALL
      SELECT '4101051' cod, 'Anahy' nome FROM DUAL
      UNION ALL
      SELECT '4101101' cod, 'Andirá' nome FROM DUAL
      UNION ALL
      SELECT '4101150' cod, 'Ângulo' nome FROM DUAL
      UNION ALL
      SELECT '4101200' cod, 'Antonina' nome FROM DUAL
      UNION ALL
      SELECT '4101309' cod, 'Antônio Olinto' nome FROM DUAL
      UNION ALL
      SELECT '4101408' cod, 'Apucarana' nome FROM DUAL
      UNION ALL
      SELECT '4101507' cod, 'Arapongas' nome FROM DUAL
      UNION ALL
      SELECT '4101606' cod, 'Arapoti' nome FROM DUAL
      UNION ALL
      SELECT '4101655' cod, 'Arapuã' nome FROM DUAL
      UNION ALL
      SELECT '4101705' cod, 'Araruna' nome FROM DUAL
      UNION ALL
      SELECT '4101804' cod, 'Araucária' nome FROM DUAL
      UNION ALL
      SELECT '4101853' cod, 'Ariranha do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4101903' cod, 'Assaí' nome FROM DUAL
      UNION ALL
      SELECT '4102000' cod, 'Assis Chateaubriand' nome FROM DUAL
      UNION ALL
      SELECT '4102109' cod, 'Astorga' nome FROM DUAL
      UNION ALL
      SELECT '4102208' cod, 'Atalaia' nome FROM DUAL
      UNION ALL
      SELECT '4102307' cod, 'Balsa Nova' nome FROM DUAL
      UNION ALL
      SELECT '4102406' cod, 'Bandeirantes' nome FROM DUAL
      UNION ALL
      SELECT '4102505' cod, 'Barbosa Ferraz' nome FROM DUAL
      UNION ALL
      SELECT '4102604' cod, 'Barracão' nome FROM DUAL
      UNION ALL
      SELECT '4102703' cod, 'Barra do Jacaré' nome FROM DUAL
      UNION ALL
      SELECT '4102752' cod, 'Bela Vista da Caroba' nome FROM DUAL
      UNION ALL
      SELECT '4102802' cod, 'Bela Vista do Paraíso' nome FROM DUAL
      UNION ALL
      SELECT '4102901' cod, 'Bituruna' nome FROM DUAL
      UNION ALL
      SELECT '4103008' cod, 'Boa Esperança' nome FROM DUAL
      UNION ALL
      SELECT '4103024' cod, 'Boa Esperança do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4103040' cod, 'Boa Ventura de São Roque' nome FROM DUAL
      UNION ALL
      SELECT '4103057' cod, 'Boa Vista da Aparecida' nome FROM DUAL
      UNION ALL
      SELECT '4103107' cod, 'Bocaiúva do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4103156' cod, 'Bom Jesus do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4103206' cod, 'Bom Sucesso' nome FROM DUAL
      UNION ALL
      SELECT '4103222' cod, 'Bom Sucesso do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4103305' cod, 'Borrazópolis' nome FROM DUAL
      UNION ALL
      SELECT '4103354' cod, 'Braganey' nome FROM DUAL
      UNION ALL
      SELECT '4103370' cod, 'Brasilândia do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4103404' cod, 'Cafeara' nome FROM DUAL
      UNION ALL
      SELECT '4103453' cod, 'Cafelândia' nome FROM DUAL
      UNION ALL
      SELECT '4103479' cod, 'Cafezal do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4103503' cod, 'Califórnia' nome FROM DUAL
      UNION ALL
      SELECT '4103602' cod, 'Cambará' nome FROM DUAL
      UNION ALL
      SELECT '4103701' cod, 'Cambé' nome FROM DUAL
      UNION ALL
      SELECT '4103800' cod, 'Cambira' nome FROM DUAL
      UNION ALL
      SELECT '4103909' cod, 'Campina da Lagoa' nome FROM DUAL
      UNION ALL
      SELECT '4103958' cod, 'Campina do Simão' nome FROM DUAL
      UNION ALL
      SELECT '4104006' cod, 'Campina Grande do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4104055' cod, 'Campo Bonito' nome FROM DUAL
      UNION ALL
      SELECT '4104105' cod, 'Campo do Tenente' nome FROM DUAL
      UNION ALL
      SELECT '4104204' cod, 'Campo Largo' nome FROM DUAL
      UNION ALL
      SELECT '4104253' cod, 'Campo Magro' nome FROM DUAL
      UNION ALL
      SELECT '4104303' cod, 'Campo Mourão' nome FROM DUAL
      UNION ALL
      SELECT '4104402' cod, 'Cândido de Abreu' nome FROM DUAL
      UNION ALL
      SELECT '4104428' cod, 'Candói' nome FROM DUAL
      UNION ALL
      SELECT '4104451' cod, 'Cantagalo' nome FROM DUAL
      UNION ALL
      SELECT '4104501' cod, 'Capanema' nome FROM DUAL
      UNION ALL
      SELECT '4104600' cod, 'Capitão Leônidas Marques' nome FROM DUAL
      UNION ALL
      SELECT '4104659' cod, 'Carambeí' nome FROM DUAL
      UNION ALL
      SELECT '4104709' cod, 'Carlópolis' nome FROM DUAL
      UNION ALL
      SELECT '4104808' cod, 'Cascavel' nome FROM DUAL
      UNION ALL
      SELECT '4104907' cod, 'Castro' nome FROM DUAL
      UNION ALL
      SELECT '4105003' cod, 'Catanduvas' nome FROM DUAL
      UNION ALL
      SELECT '4105102' cod, 'Centenário do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4105201' cod, 'Cerro Azul' nome FROM DUAL
      UNION ALL
      SELECT '4105300' cod, 'Céu Azul' nome FROM DUAL
      UNION ALL
      SELECT '4105409' cod, 'Chopinzinho' nome FROM DUAL
      UNION ALL
      SELECT '4105508' cod, 'Cianorte' nome FROM DUAL
      UNION ALL
      SELECT '4105607' cod, 'Cidade Gaúcha' nome FROM DUAL
      UNION ALL
      SELECT '4105706' cod, 'Clevelândia' nome FROM DUAL
      UNION ALL
      SELECT '4105805' cod, 'Colombo' nome FROM DUAL
      UNION ALL
      SELECT '4105904' cod, 'Colorado' nome FROM DUAL
      UNION ALL
      SELECT '4106001' cod, 'Congonhinhas' nome FROM DUAL
      UNION ALL
      SELECT '4106100' cod, 'Conselheiro Mairinck' nome FROM DUAL
      UNION ALL
      SELECT '4106209' cod, 'Contenda' nome FROM DUAL
      UNION ALL
      SELECT '4106308' cod, 'Corbélia' nome FROM DUAL
      UNION ALL
      SELECT '4106407' cod, 'Cornélio Procópio' nome FROM DUAL
      UNION ALL
      SELECT '4106456' cod, 'Coronel Domingos Soares' nome FROM DUAL
      UNION ALL
      SELECT '4106506' cod, 'Coronel Vivida' nome FROM DUAL
      UNION ALL
      SELECT '4106555' cod, 'Corumbataí do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4106571' cod, 'Cruzeiro do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4106605' cod, 'Cruzeiro do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4106704' cod, 'Cruzeiro do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4106803' cod, 'Cruz Machado' nome FROM DUAL
      UNION ALL
      SELECT '4106852' cod, 'Cruzmaltina' nome FROM DUAL
      UNION ALL
      SELECT '4106902' cod, 'Curitiba' nome FROM DUAL
      UNION ALL
      SELECT '4107009' cod, 'Curiúva' nome FROM DUAL
      UNION ALL
      SELECT '4107108' cod, 'Diamante do Norte' nome FROM DUAL
      UNION ALL
      SELECT '4107124' cod, 'Diamante do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4107157' cod, 'Diamante D''Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4107207' cod, 'Dois Vizinhos' nome FROM DUAL
      UNION ALL
      SELECT '4107256' cod, 'Douradina' nome FROM DUAL
      UNION ALL
      SELECT '4107306' cod, 'Doutor Camargo' nome FROM DUAL
      UNION ALL
      SELECT '4107405' cod, 'Enéas Marques' nome FROM DUAL
      UNION ALL
      SELECT '4107504' cod, 'Engenheiro Beltrão' nome FROM DUAL
      UNION ALL
      SELECT '4107520' cod, 'Esperança Nova' nome FROM DUAL
      UNION ALL
      SELECT '4107538' cod, 'Entre Rios do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4107546' cod, 'Espigão Alto do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4107553' cod, 'Farol' nome FROM DUAL
      UNION ALL
      SELECT '4107603' cod, 'Faxinal' nome FROM DUAL
      UNION ALL
      SELECT '4107652' cod, 'Fazenda Rio Grande' nome FROM DUAL
      UNION ALL
      SELECT '4107702' cod, 'Fênix' nome FROM DUAL
      UNION ALL
      SELECT '4107736' cod, 'Fernandes Pinheiro' nome FROM DUAL
      UNION ALL
      SELECT '4107751' cod, 'Figueira' nome FROM DUAL
      UNION ALL
      SELECT '4107801' cod, 'Floraí' nome FROM DUAL
      UNION ALL
      SELECT '4107850' cod, 'Flor da Serra do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4107900' cod, 'Floresta' nome FROM DUAL
      UNION ALL
      SELECT '4108007' cod, 'Florestópolis' nome FROM DUAL
      UNION ALL
      SELECT '4108106' cod, 'Flórida' nome FROM DUAL
      UNION ALL
      SELECT '4108205' cod, 'Formosa do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4108304' cod, 'Foz do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4108320' cod, 'Francisco Alves' nome FROM DUAL
      UNION ALL
      SELECT '4108403' cod, 'Francisco Beltrão' nome FROM DUAL
      UNION ALL
      SELECT '4108452' cod, 'Foz do Jordão' nome FROM DUAL
      UNION ALL
      SELECT '4108502' cod, 'General Carneiro' nome FROM DUAL
      UNION ALL
      SELECT '4108551' cod, 'Godoy Moreira' nome FROM DUAL
      UNION ALL
      SELECT '4108601' cod, 'Goioerê' nome FROM DUAL
      UNION ALL
      SELECT '4108650' cod, 'Goioxim' nome FROM DUAL
      UNION ALL
      SELECT '4108700' cod, 'Grandes Rios' nome FROM DUAL
      UNION ALL
      SELECT '4108809' cod, 'Guaíra' nome FROM DUAL
      UNION ALL
      SELECT '4108908' cod, 'Guairaçá' nome FROM DUAL
      UNION ALL
      SELECT '4108957' cod, 'Guamiranga' nome FROM DUAL
      UNION ALL
      SELECT '4109005' cod, 'Guapirama' nome FROM DUAL
      UNION ALL
      SELECT '4109104' cod, 'Guaporema' nome FROM DUAL
      UNION ALL
      SELECT '4109203' cod, 'Guaraci' nome FROM DUAL
      UNION ALL
      SELECT '4109302' cod, 'Guaraniaçu' nome FROM DUAL
      UNION ALL
      SELECT '4109401' cod, 'Guarapuava' nome FROM DUAL
      UNION ALL
      SELECT '4109500' cod, 'Guaraqueçaba' nome FROM DUAL
      UNION ALL
      SELECT '4109609' cod, 'Guaratuba' nome FROM DUAL
      UNION ALL
      SELECT '4109658' cod, 'Honório Serpa' nome FROM DUAL
      UNION ALL
      SELECT '4109708' cod, 'Ibaiti' nome FROM DUAL
      UNION ALL
      SELECT '4109757' cod, 'Ibema' nome FROM DUAL
      UNION ALL
      SELECT '4109807' cod, 'Ibiporã' nome FROM DUAL
      UNION ALL
      SELECT '4109906' cod, 'Icaraíma' nome FROM DUAL
      UNION ALL
      SELECT '4110003' cod, 'Iguaraçu' nome FROM DUAL
      UNION ALL
      SELECT '4110052' cod, 'Iguatu' nome FROM DUAL
      UNION ALL
      SELECT '4110078' cod, 'Imbaú' nome FROM DUAL
      UNION ALL
      SELECT '4110102' cod, 'Imbituva' nome FROM DUAL
      UNION ALL
      SELECT '4110201' cod, 'Inácio Martins' nome FROM DUAL
      UNION ALL
      SELECT '4110300' cod, 'Inajá' nome FROM DUAL
      UNION ALL
      SELECT '4110409' cod, 'Indianópolis' nome FROM DUAL
      UNION ALL
      SELECT '4110508' cod, 'Ipiranga' nome FROM DUAL
      UNION ALL
      SELECT '4110607' cod, 'Iporã' nome FROM DUAL
      UNION ALL
      SELECT '4110656' cod, 'Iracema do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4110706' cod, 'Irati' nome FROM DUAL
      UNION ALL
      SELECT '4110805' cod, 'Iretama' nome FROM DUAL
      UNION ALL
      SELECT '4110904' cod, 'Itaguajé' nome FROM DUAL
      UNION ALL
      SELECT '4110953' cod, 'Itaipulândia' nome FROM DUAL
      UNION ALL
      SELECT '4111001' cod, 'Itambaracá' nome FROM DUAL
      UNION ALL
      SELECT '4111100' cod, 'Itambé' nome FROM DUAL
      UNION ALL
      SELECT '4111209' cod, 'Itapejara d''Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4111258' cod, 'Itaperuçu' nome FROM DUAL
      UNION ALL
      SELECT '4111308' cod, 'Itaúna do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4111407' cod, 'Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4111506' cod, 'Ivaiporã' nome FROM DUAL
      UNION ALL
      SELECT '4111555' cod, 'Ivaté' nome FROM DUAL
      UNION ALL
      SELECT '4111605' cod, 'Ivatuba' nome FROM DUAL
      UNION ALL
      SELECT '4111704' cod, 'Jaboti' nome FROM DUAL
      UNION ALL
      SELECT '4111803' cod, 'Jacarezinho' nome FROM DUAL
      UNION ALL
      SELECT '4111902' cod, 'Jaguapitã' nome FROM DUAL
      UNION ALL
      SELECT '4112009' cod, 'Jaguariaíva' nome FROM DUAL
      UNION ALL
      SELECT '4112108' cod, 'Jandaia do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4112207' cod, 'Janiópolis' nome FROM DUAL
      UNION ALL
      SELECT '4112306' cod, 'Japira' nome FROM DUAL
      UNION ALL
      SELECT '4112405' cod, 'Japurá' nome FROM DUAL
      UNION ALL
      SELECT '4112504' cod, 'Jardim Alegre' nome FROM DUAL
      UNION ALL
      SELECT '4112603' cod, 'Jardim Olinda' nome FROM DUAL
      UNION ALL
      SELECT '4112702' cod, 'Jataizinho' nome FROM DUAL
      UNION ALL
      SELECT '4112751' cod, 'Jesuítas' nome FROM DUAL
      UNION ALL
      SELECT '4112801' cod, 'Joaquim Távora' nome FROM DUAL
      UNION ALL
      SELECT '4112900' cod, 'Jundiaí do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4112959' cod, 'Juranda' nome FROM DUAL
      UNION ALL
      SELECT '4113007' cod, 'Jussara' nome FROM DUAL
      UNION ALL
      SELECT '4113106' cod, 'Kaloré' nome FROM DUAL
      UNION ALL
      SELECT '4113205' cod, 'Lapa' nome FROM DUAL
      UNION ALL
      SELECT '4113254' cod, 'Laranjal' nome FROM DUAL
      UNION ALL
      SELECT '4113304' cod, 'Laranjeiras do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4113403' cod, 'Leópolis' nome FROM DUAL
      UNION ALL
      SELECT '4113429' cod, 'Lidianópolis' nome FROM DUAL
      UNION ALL
      SELECT '4113452' cod, 'Lindoeste' nome FROM DUAL
      UNION ALL
      SELECT '4113502' cod, 'Loanda' nome FROM DUAL
      UNION ALL
      SELECT '4113601' cod, 'Lobato' nome FROM DUAL
      UNION ALL
      SELECT '4113700' cod, 'Londrina' nome FROM DUAL
      UNION ALL
      SELECT '4113734' cod, 'Luiziana' nome FROM DUAL
      UNION ALL
      SELECT '4113759' cod, 'Lunardelli' nome FROM DUAL
      UNION ALL
      SELECT '4113809' cod, 'Lupionópolis' nome FROM DUAL
      UNION ALL
      SELECT '4113908' cod, 'Mallet' nome FROM DUAL
      UNION ALL
      SELECT '4114005' cod, 'Mamborê' nome FROM DUAL
      UNION ALL
      SELECT '4114104' cod, 'Mandaguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4114203' cod, 'Mandaguari' nome FROM DUAL
      UNION ALL
      SELECT '4114302' cod, 'Mandirituba' nome FROM DUAL
      UNION ALL
      SELECT '4114351' cod, 'Manfrinópolis' nome FROM DUAL
      UNION ALL
      SELECT '4114401' cod, 'Mangueirinha' nome FROM DUAL
      UNION ALL
      SELECT '4114500' cod, 'Manoel Ribas' nome FROM DUAL
      UNION ALL
      SELECT '4114609' cod, 'Marechal Cândido Rondon' nome FROM DUAL
      UNION ALL
      SELECT '4114708' cod, 'Maria Helena' nome FROM DUAL
      UNION ALL
      SELECT '4114807' cod, 'Marialva' nome FROM DUAL
      UNION ALL
      SELECT '4114906' cod, 'Marilândia do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4115002' cod, 'Marilena' nome FROM DUAL
      UNION ALL
      SELECT '4115101' cod, 'Mariluz' nome FROM DUAL
      UNION ALL
      SELECT '4115200' cod, 'Maringá' nome FROM DUAL
      UNION ALL
      SELECT '4115309' cod, 'Mariópolis' nome FROM DUAL
      UNION ALL
      SELECT '4115358' cod, 'Maripá' nome FROM DUAL
      UNION ALL
      SELECT '4115408' cod, 'Marmeleiro' nome FROM DUAL
      UNION ALL
      SELECT '4115457' cod, 'Marquinho' nome FROM DUAL
      UNION ALL
      SELECT '4115507' cod, 'Marumbi' nome FROM DUAL
      UNION ALL
      SELECT '4115606' cod, 'Matelândia' nome FROM DUAL
      UNION ALL
      SELECT '4115705' cod, 'Matinhos' nome FROM DUAL
      UNION ALL
      SELECT '4115739' cod, 'Mato Rico' nome FROM DUAL
      UNION ALL
      SELECT '4115754' cod, 'Mauá da Serra' nome FROM DUAL
      UNION ALL
      SELECT '4115804' cod, 'Medianeira' nome FROM DUAL
      UNION ALL
      SELECT '4115853' cod, 'Mercedes' nome FROM DUAL
      UNION ALL
      SELECT '4115903' cod, 'Mirador' nome FROM DUAL
      UNION ALL
      SELECT '4116000' cod, 'Miraselva' nome FROM DUAL
      UNION ALL
      SELECT '4116059' cod, 'Missal' nome FROM DUAL
      UNION ALL
      SELECT '4116109' cod, 'Moreira Sales' nome FROM DUAL
      UNION ALL
      SELECT '4116208' cod, 'Morretes' nome FROM DUAL
      UNION ALL
      SELECT '4116307' cod, 'Munhoz de Melo' nome FROM DUAL
      UNION ALL
      SELECT '4116406' cod, 'Nossa Senhora das Graças' nome FROM DUAL
      UNION ALL
      SELECT '4116505' cod, 'Nova Aliança do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4116604' cod, 'Nova América da Colina' nome FROM DUAL
      UNION ALL
      SELECT '4116703' cod, 'Nova Aurora' nome FROM DUAL
      UNION ALL
      SELECT '4116802' cod, 'Nova Cantu' nome FROM DUAL
      UNION ALL
      SELECT '4116901' cod, 'Nova Esperança' nome FROM DUAL
      UNION ALL
      SELECT '4116950' cod, 'Nova Esperança do Sudoeste' nome FROM DUAL
      UNION ALL
      SELECT '4117008' cod, 'Nova Fátima' nome FROM DUAL
      UNION ALL
      SELECT '4117057' cod, 'Nova Laranjeiras' nome FROM DUAL
      UNION ALL
      SELECT '4117107' cod, 'Nova Londrina' nome FROM DUAL
      UNION ALL
      SELECT '4117206' cod, 'Nova Olímpia' nome FROM DUAL
      UNION ALL
      SELECT '4117214' cod, 'Nova Santa Bárbara' nome FROM DUAL
      UNION ALL
      SELECT '4117222' cod, 'Nova Santa Rosa' nome FROM DUAL
      UNION ALL
      SELECT '4117255' cod, 'Nova Prata do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4117271' cod, 'Nova Tebas' nome FROM DUAL
      UNION ALL
      SELECT '4117297' cod, 'Novo Itacolomi' nome FROM DUAL
      UNION ALL
      SELECT '4117305' cod, 'Ortigueira' nome FROM DUAL
      UNION ALL
      SELECT '4117404' cod, 'Ourizona' nome FROM DUAL
      UNION ALL
      SELECT '4117453' cod, 'Ouro Verde do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4117503' cod, 'Paiçandu' nome FROM DUAL
      UNION ALL
      SELECT '4117602' cod, 'Palmas' nome FROM DUAL
      UNION ALL
      SELECT '4117701' cod, 'Palmeira' nome FROM DUAL
      UNION ALL
      SELECT '4117800' cod, 'Palmital' nome FROM DUAL
      UNION ALL
      SELECT '4117909' cod, 'Palotina' nome FROM DUAL
      UNION ALL
      SELECT '4118006' cod, 'Paraíso do Norte' nome FROM DUAL
      UNION ALL
      SELECT '4118105' cod, 'Paranacity' nome FROM DUAL
      UNION ALL
      SELECT '4118204' cod, 'Paranaguá' nome FROM DUAL
      UNION ALL
      SELECT '4118303' cod, 'Paranapoema' nome FROM DUAL
      UNION ALL
      SELECT '4118402' cod, 'Paranavaí' nome FROM DUAL
      UNION ALL
      SELECT '4118451' cod, 'Pato Bragado' nome FROM DUAL
      UNION ALL
      SELECT '4118501' cod, 'Pato Branco' nome FROM DUAL
      UNION ALL
      SELECT '4118600' cod, 'Paula Freitas' nome FROM DUAL
      UNION ALL
      SELECT '4118709' cod, 'Paulo Frontin' nome FROM DUAL
      UNION ALL
      SELECT '4118808' cod, 'Peabiru' nome FROM DUAL
      UNION ALL
      SELECT '4118857' cod, 'Perobal' nome FROM DUAL
      UNION ALL
      SELECT '4118907' cod, 'Pérola' nome FROM DUAL
      UNION ALL
      SELECT '4119004' cod, 'Pérola d''Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4119103' cod, 'Piên' nome FROM DUAL
      UNION ALL
      SELECT '4119152' cod, 'Pinhais' nome FROM DUAL
      UNION ALL
      SELECT '4119202' cod, 'Pinhalão' nome FROM DUAL
      UNION ALL
      SELECT '4119251' cod, 'Pinhal de São Bento' nome FROM DUAL
      UNION ALL
      SELECT '4119301' cod, 'Pinhão' nome FROM DUAL
      UNION ALL
      SELECT '4119400' cod, 'Piraí do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4119509' cod, 'Piraquara' nome FROM DUAL
      UNION ALL
      SELECT '4119608' cod, 'Pitanga' nome FROM DUAL
      UNION ALL
      SELECT '4119657' cod, 'Pitangueiras' nome FROM DUAL
      UNION ALL
      SELECT '4119707' cod, 'Planaltina do Paraná' nome FROM DUAL
      UNION ALL
      SELECT '4119806' cod, 'Planalto' nome FROM DUAL
      UNION ALL
      SELECT '4119905' cod, 'Ponta Grossa' nome FROM DUAL
      UNION ALL
      SELECT '4119954' cod, 'Pontal do Paraná' nome FROM DUAL
      UNION ALL
      SELECT '4120002' cod, 'Porecatu' nome FROM DUAL
      UNION ALL
      SELECT '4120101' cod, 'Porto Amazonas' nome FROM DUAL
      UNION ALL
      SELECT '4120150' cod, 'Porto Barreiro' nome FROM DUAL
      UNION ALL
      SELECT '4120200' cod, 'Porto Rico' nome FROM DUAL
      UNION ALL
      SELECT '4120309' cod, 'Porto Vitória' nome FROM DUAL
      UNION ALL
      SELECT '4120333' cod, 'Prado Ferreira' nome FROM DUAL
      UNION ALL
      SELECT '4120358' cod, 'Pranchita' nome FROM DUAL
      UNION ALL
      SELECT '4120408' cod, 'Presidente Castelo Branco' nome FROM DUAL
      UNION ALL
      SELECT '4120507' cod, 'Primeiro de Maio' nome FROM DUAL
      UNION ALL
      SELECT '4120606' cod, 'Prudentópolis' nome FROM DUAL
      UNION ALL
      SELECT '4120655' cod, 'Quarto Centenário' nome FROM DUAL
      UNION ALL
      SELECT '4120705' cod, 'Quatiguá' nome FROM DUAL
      UNION ALL
      SELECT '4120804' cod, 'Quatro Barras' nome FROM DUAL
      UNION ALL
      SELECT '4120853' cod, 'Quatro Pontes' nome FROM DUAL
      UNION ALL
      SELECT '4120903' cod, 'Quedas do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4121000' cod, 'Querência do Norte' nome FROM DUAL
      UNION ALL
      SELECT '4121109' cod, 'Quinta do Sol' nome FROM DUAL
      UNION ALL
      SELECT '4121208' cod, 'Quitandinha' nome FROM DUAL
      UNION ALL
      SELECT '4121257' cod, 'Ramilândia' nome FROM DUAL
      UNION ALL
      SELECT '4121307' cod, 'Rancho Alegre' nome FROM DUAL
      UNION ALL
      SELECT '4121356' cod, 'Rancho Alegre D''Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4121406' cod, 'Realeza' nome FROM DUAL
      UNION ALL
      SELECT '4121505' cod, 'Rebouças' nome FROM DUAL
      UNION ALL
      SELECT '4121604' cod, 'Renascença' nome FROM DUAL
      UNION ALL
      SELECT '4121703' cod, 'Reserva' nome FROM DUAL
      UNION ALL
      SELECT '4121752' cod, 'Reserva do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4121802' cod, 'Ribeirão Claro' nome FROM DUAL
      UNION ALL
      SELECT '4121901' cod, 'Ribeirão do Pinhal' nome FROM DUAL
      UNION ALL
      SELECT '4122008' cod, 'Rio Azul' nome FROM DUAL
      UNION ALL
      SELECT '4122107' cod, 'Rio Bom' nome FROM DUAL
      UNION ALL
      SELECT '4122156' cod, 'Rio Bonito do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4122172' cod, 'Rio Branco do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4122206' cod, 'Rio Branco do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4122305' cod, 'Rio Negro' nome FROM DUAL
      UNION ALL
      SELECT '4122404' cod, 'Rolândia' nome FROM DUAL
      UNION ALL
      SELECT '4122503' cod, 'Roncador' nome FROM DUAL
      UNION ALL
      SELECT '4122602' cod, 'Rondon' nome FROM DUAL
      UNION ALL
      SELECT '4122651' cod, 'Rosário do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4122701' cod, 'Sabáudia' nome FROM DUAL
      UNION ALL
      SELECT '4122800' cod, 'Salgado Filho' nome FROM DUAL
      UNION ALL
      SELECT '4122909' cod, 'Salto do Itararé' nome FROM DUAL
      UNION ALL
      SELECT '4123006' cod, 'Salto do Lontra' nome FROM DUAL
      UNION ALL
      SELECT '4123105' cod, 'Santa Amélia' nome FROM DUAL
      UNION ALL
      SELECT '4123204' cod, 'Santa Cecília do Pavão' nome FROM DUAL
      UNION ALL
      SELECT '4123303' cod, 'Santa Cruz de Monte Castelo' nome FROM DUAL
      UNION ALL
      SELECT '4123402' cod, 'Santa Fé' nome FROM DUAL
      UNION ALL
      SELECT '4123501' cod, 'Santa Helena' nome FROM DUAL
      UNION ALL
      SELECT '4123600' cod, 'Santa Inês' nome FROM DUAL
      UNION ALL
      SELECT '4123709' cod, 'Santa Isabel do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4123808' cod, 'Santa Izabel do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4123824' cod, 'Santa Lúcia' nome FROM DUAL
      UNION ALL
      SELECT '4123857' cod, 'Santa Maria do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4123907' cod, 'Santa Mariana' nome FROM DUAL
      UNION ALL
      SELECT '4123956' cod, 'Santa Mônica' nome FROM DUAL
      UNION ALL
      SELECT '4124004' cod, 'Santana do Itararé' nome FROM DUAL
      UNION ALL
      SELECT '4124020' cod, 'Santa Tereza do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4124053' cod, 'Santa Terezinha de Itaipu' nome FROM DUAL
      UNION ALL
      SELECT '4124103' cod, 'Santo Antônio da Platina' nome FROM DUAL
      UNION ALL
      SELECT '4124202' cod, 'Santo Antônio do Caiuá' nome FROM DUAL
      UNION ALL
      SELECT '4124301' cod, 'Santo Antônio do Paraíso' nome FROM DUAL
      UNION ALL
      SELECT '4124400' cod, 'Santo Antônio do Sudoeste' nome FROM DUAL
      UNION ALL
      SELECT '4124509' cod, 'Santo Inácio' nome FROM DUAL
      UNION ALL
      SELECT '4124608' cod, 'São Carlos do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4124707' cod, 'São Jerônimo da Serra' nome FROM DUAL
      UNION ALL
      SELECT '4124806' cod, 'São João' nome FROM DUAL
      UNION ALL
      SELECT '4124905' cod, 'São João do Caiuá' nome FROM DUAL
      UNION ALL
      SELECT '4125001' cod, 'São João do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4125100' cod, 'São João do Triunfo' nome FROM DUAL
      UNION ALL
      SELECT '4125209' cod, 'São Jorge d''Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4125308' cod, 'São Jorge do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4125357' cod, 'São Jorge do Patrocínio' nome FROM DUAL
      UNION ALL
      SELECT '4125407' cod, 'São José da Boa Vista' nome FROM DUAL
      UNION ALL
      SELECT '4125456' cod, 'São José das Palmeiras' nome FROM DUAL
      UNION ALL
      SELECT '4125506' cod, 'São José dos Pinhais' nome FROM DUAL
      UNION ALL
      SELECT '4125555' cod, 'São Manoel do Paraná' nome FROM DUAL
      UNION ALL
      SELECT '4125605' cod, 'São Mateus do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4125704' cod, 'São Miguel do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4125753' cod, 'São Pedro do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4125803' cod, 'São Pedro do Ivaí' nome FROM DUAL
      UNION ALL
      SELECT '4125902' cod, 'São Pedro do Paraná' nome FROM DUAL
      UNION ALL
      SELECT '4126009' cod, 'São Sebastião da Amoreira' nome FROM DUAL
      UNION ALL
      SELECT '4126108' cod, 'São Tomé' nome FROM DUAL
      UNION ALL
      SELECT '4126207' cod, 'Sapopema' nome FROM DUAL
      UNION ALL
      SELECT '4126256' cod, 'Sarandi' nome FROM DUAL
      UNION ALL
      SELECT '4126272' cod, 'Saudade do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4126306' cod, 'Sengés' nome FROM DUAL
      UNION ALL
      SELECT '4126355' cod, 'Serranópolis do Iguaçu' nome FROM DUAL
      UNION ALL
      SELECT '4126405' cod, 'Sertaneja' nome FROM DUAL
      UNION ALL
      SELECT '4126504' cod, 'Sertanópolis' nome FROM DUAL
      UNION ALL
      SELECT '4126603' cod, 'Siqueira Campos' nome FROM DUAL
      UNION ALL
      SELECT '4126652' cod, 'Sulina' nome FROM DUAL
      UNION ALL
      SELECT '4126678' cod, 'Tamarana' nome FROM DUAL
      UNION ALL
      SELECT '4126702' cod, 'Tamboara' nome FROM DUAL
      UNION ALL
      SELECT '4126801' cod, 'Tapejara' nome FROM DUAL
      UNION ALL
      SELECT '4126900' cod, 'Tapira' nome FROM DUAL
      UNION ALL
      SELECT '4127007' cod, 'Teixeira Soares' nome FROM DUAL
      UNION ALL
      SELECT '4127106' cod, 'Telêmaco Borba' nome FROM DUAL
      UNION ALL
      SELECT '4127205' cod, 'Terra Boa' nome FROM DUAL
      UNION ALL
      SELECT '4127304' cod, 'Terra Rica' nome FROM DUAL
      UNION ALL
      SELECT '4127403' cod, 'Terra Roxa' nome FROM DUAL
      UNION ALL
      SELECT '4127502' cod, 'Tibagi' nome FROM DUAL
      UNION ALL
      SELECT '4127601' cod, 'Tijucas do Sul' nome FROM DUAL
      UNION ALL
      SELECT '4127700' cod, 'Toledo' nome FROM DUAL
      UNION ALL
      SELECT '4127809' cod, 'Tomazina' nome FROM DUAL
      UNION ALL
      SELECT '4127858' cod, 'Três Barras do Paraná' nome FROM DUAL
      UNION ALL
      SELECT '4127882' cod, 'Tunas do Paraná' nome FROM DUAL
      UNION ALL
      SELECT '4127908' cod, 'Tuneiras do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4127957' cod, 'Tupãssi' nome FROM DUAL
      UNION ALL
      SELECT '4127965' cod, 'Turvo' nome FROM DUAL
      UNION ALL
      SELECT '4128005' cod, 'Ubiratã' nome FROM DUAL
      UNION ALL
      SELECT '4128104' cod, 'Umuarama' nome FROM DUAL
      UNION ALL
      SELECT '4128203' cod, 'União da Vitória' nome FROM DUAL
      UNION ALL
      SELECT '4128302' cod, 'Uniflor' nome FROM DUAL
      UNION ALL
      SELECT '4128401' cod, 'Uraí' nome FROM DUAL
      UNION ALL
      SELECT '4128500' cod, 'Wenceslau Braz' nome FROM DUAL
      UNION ALL
      SELECT '4128534' cod, 'Ventania' nome FROM DUAL
      UNION ALL
      SELECT '4128559' cod, 'Vera Cruz do Oeste' nome FROM DUAL
      UNION ALL
      SELECT '4128609' cod, 'Verê' nome FROM DUAL
      UNION ALL
      SELECT '4128625' cod, 'Alto Paraíso' nome FROM DUAL
      UNION ALL
      SELECT '4128633' cod, 'Doutor Ulysses' nome FROM DUAL
      UNION ALL
      SELECT '4128658' cod, 'Virmond' nome FROM DUAL
      UNION ALL
      SELECT '4128708' cod, 'Vitorino' nome FROM DUAL
      UNION ALL
      SELECT '4128807' cod, 'Xambrê' nome FROM DUAL
) o
   ON (d.mun_codigo_origem = o.cod)
 WHEN NOT MATCHED THEN
      INSERT (mun_codigo_origem, mun_nome, mun_uf)
      VALUES (o.cod, o.nome, 'PR');

COMMIT;
