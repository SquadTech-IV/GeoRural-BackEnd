

from __future__ import annotations

import logging
import os
import re
import unicodedata
from typing import Any, Dict, Optional

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

log = logging.getLogger(__name__)

# Nome da Connection do Airflow: host = URL base, password = token.
CONEXAO = "georural_api"

# exe_dag_run_id e VARCHAR2(120) no banco.
LIMITE_DAG_RUN_ID = 120

# Arquivo grande pode levar horas. O timeout de leitura nao pode cortar isso.
TIMEOUT_CONEXAO_SEG = 15
TIMEOUT_LEITURA_PADRAO_SEG = 4 * 60 * 60

# Consulta e rapida; nao precisa do timeout generoso da etapa.
TIMEOUT_CONSULTA_SEG = 60

ETAPAS_ENCADEADAS = ("INGESTAO", "VALIDACAO", "TRATAMENTO", "CALCULO", "QUALIDADE")

# Cadencias da varredura. Uma DAG de descoberta por cadencia agendada.
# As chaves saem do conjunto fechado que o CadastroValidacaoService aceita em
# con_periodicidade: DIARIA, SEMANAL, MENSAL, TRIMESTRAL, ANUAL, SOB_DEMANDA e
# UNICA.
CADENCIAS = {
    "diaria": "0 5 * * *",
    "semanal": "0 5 * * 1",
    "mensal": "0 5 1 * *",
    "trimestral": "0 5 1 1,4,7,10 *",
    "anual": "0 5 1 1 *",
}

# SOB_DEMANDA e UNICA nao tem agenda: quem varre esses conjuntos e a DAG manual.
CADENCIA_MANUAL = "sob_demanda"

# Cadencia que recebe conjunto sem periodicidade ou com valor que nao reconheco.
CADENCIA_PADRAO = "diaria"

# con_periodicidade e VARCHAR2(20) sem CHECK no banco: o que o aplicativo grava
# vem da lista fechada acima, mas um INSERT feito direto no Oracle pode ter
# gravado outra coisa. Por isso aceito variantes, e o que sobra cai na cadencia
# padrao com aviso no log.
TERMOS_DE_CADENCIA = {
    "diaria": ("DIARIA", "DIARIO", "DIARIAMENTE", "DAILY"),
    "semanal": ("SEMANAL", "SEMANALMENTE", "WEEKLY"),
    "mensal": ("MENSAL", "MENSALMENTE", "MONTHLY"),
    "trimestral": ("TRIMESTRAL", "TRIMESTRALMENTE", "QUARTERLY"),
    "anual": ("ANUAL", "ANUALMENTE", "YEARLY", "ANNUAL"),
    CADENCIA_MANUAL: (
        "SOB_DEMANDA", "UNICA", "UNICO", "AVULSA", "ON_DEMAND", "MANUAL",
    ),
}


class FalhaDefinitiva(Exception):
    """Erro que nao melhora se tentar de novo: requisicao errada ou conflito."""


class EtapaSemImplementacao(Exception):
    """A API respondeu 501: a etapa existe no fluxo mas nao foi implementada."""


class FalhaTemporaria(Exception):
    """Erro que pode ser transitorio: 5xx ou a rede caiu."""


def _por_variavel(nome: str) -> Optional[str]:
    """Le uma Variable do Airflow, caindo para variavel de ambiente."""
    try:
        from airflow.models import Variable

        valor = Variable.get(nome, default_var=None)
        if valor:
            return valor
    except Exception:
        log.debug("Variable %s indisponivel; usando o ambiente.", nome)

    return os.environ.get(nome.upper())


def _por_conexao() -> tuple[Optional[str], Optional[str]]:
    """Le host e token da Connection do Airflow, se ela existir."""
    try:
        # O Airflow 3 moveu o BaseHook para airflow.sdk e avisa de deprecacao em
        # airflow.hooks.base; o 2.x so tem airflow.hooks.base.
        try:
            from airflow.sdk.bases.hook import BaseHook
        except ImportError:
            from airflow.hooks.base import BaseHook

        conexao = BaseHook.get_connection(CONEXAO)
        base = conexao.host or ""
        if base and not base.startswith("http"):
            esquema = conexao.schema or "http"
            porta = f":{conexao.port}" if conexao.port else ""
            base = f"{esquema}://{base}{porta}"
        return (base or None), (conexao.password or None)
    except Exception:
        log.debug("Connection %s indisponivel; usando Variables.", CONEXAO)
        return None, None


# Guarda o que ja foi resolvido: cada etapa faria a mesma consulta de novo, e
# uma Variable que nao existe deixa uma linha de erro no log a cada consulta.
_RESOLVIDO: dict = {}


def base_da_api() -> str:
    if "base" not in _RESOLVIDO:
        base, _ = _por_conexao()
        base = base or _por_variavel("georural_api_base") or "http://localhost:8080"
        _RESOLVIDO["base"] = base.rstrip("/")

    return _RESOLVIDO["base"]


def token_da_api() -> Optional[str]:
    if "token" not in _RESOLVIDO:
        _, token = _por_conexao()
        _RESOLVIDO["token"] = token or _por_variavel("georural_api_token")

    return _RESOLVIDO["token"]


def esquecer_resolvido() -> None:
    """Descarta o que foi resolvido. Serve para teste e para trocar de ambiente."""
    _RESOLVIDO.clear()


def timeout_de_leitura() -> int:
    if "timeout" in _RESOLVIDO:
        return _RESOLVIDO["timeout"]

    bruto = _por_variavel("georural_timeout_etapa_seg")
    try:
        _RESOLVIDO["timeout"] = int(bruto) if bruto else TIMEOUT_LEITURA_PADRAO_SEG
        return _RESOLVIDO["timeout"]
    except ValueError:
        log.warning("georural_timeout_etapa_seg invalido (%s); usando o padrao.", bruto)
        return TIMEOUT_LEITURA_PADRAO_SEG


def sessao() -> requests.Session:
    """Sessao que repete so erro de conexao, quando nada chegou a rodar.

    Repetir leitura seria perigoso: a etapa pode ter comecado a escrever.
    """
    s = requests.Session()
    tentativas = Retry(
        total=None,
        connect=3,
        read=0,
        status=0,
        other=0,
        backoff_factor=2,
        allowed_methods=frozenset(["GET", "POST"]),
    )
    adaptador = HTTPAdapter(max_retries=tentativas)
    s.mount("http://", adaptador)
    s.mount("https://", adaptador)
    return s


def montar_corpo(
    conjunto_id: Any,
    arquivo_bruto_id: Any,
    competencia: Optional[str],
    dag_run_id: str,
    parametros: Optional[Dict[str, Any]],
    usuario_id: Any = None,
) -> Dict[str, Any]:
    """Corpo do ExecucaoPipelineRequest.

    usuarioId fica de fora por padrao: com usuario, a rodada conta como manual
    no painel da operacao.
    """
    if conjunto_id is None:
        raise FalhaDefinitiva("Informe o conjuntoId no conf da DAG.")

    corpo: Dict[str, Any] = {
        "conjuntoId": int(conjunto_id),
        "dagRunId": _cortar(dag_run_id),
    }

    if arquivo_bruto_id is not None:
        corpo["arquivoBrutoId"] = int(arquivo_bruto_id)

    if competencia:
        corpo["competencia"] = str(competencia)

    if usuario_id is not None:
        corpo["usuarioId"] = int(usuario_id)

    if parametros:
        corpo["parametros"] = {str(k): str(v) for k, v in parametros.items()}

    return corpo


def _cortar(dag_run_id: str) -> str:
    texto = str(dag_run_id or "")

    if len(texto) > LIMITE_DAG_RUN_ID:
        log.warning(
            "dag_run_id tem %s caracteres e a coluna aceita %s; cortando.",
            len(texto),
            LIMITE_DAG_RUN_ID,
        )
        return texto[:LIMITE_DAG_RUN_ID]

    return texto


def _normalizar_texto(valor: Optional[str]) -> str:
    """Mesma normalizacao do CadastroValidacaoService: sem acento, maiusculo, _."""
    texto = unicodedata.normalize("NFD", str(valor or "").strip())
    texto = "".join(c for c in texto if not unicodedata.combining(c))

    return re.sub(r"[\s-]+", "_", texto.upper())


def normalizar_cadencia(periodicidade: Optional[str]) -> tuple[str, bool]:
    """Diz em que cadencia um conjunto entra.

    Devolve (cadencia, reconhecida). Nao reconhecida cai na padrao, para que
    conjunto com o cadastro em branco nao deixe de ser varrido nunca.
    """
    texto = _normalizar_texto(periodicidade)

    if not texto:
        return CADENCIA_PADRAO, False

    for cadencia, termos in TERMOS_DE_CADENCIA.items():
        if texto in termos:
            return cadencia, True

    return CADENCIA_PADRAO, False


def montar_run_id(conjunto_id: Any, arquivo_id: Any, sufixo: Optional[str] = None) -> str:
    """run_id da rodada disparada, deterministico por arquivo.

    Sem sufixo, o mesmo arquivo nunca e disparado duas vezes: o Airflow recusa
    run_id repetido. E o que impede a varredura de ficar reprocessando em laco
    um arquivo que falha sempre.
    """
    base = f"georural-c{conjunto_id}-a{arquivo_id}"

    if sufixo:
        base = f"{base}-{sufixo}"

    return _cortar(base)


def listar_conjuntos(
    base: Optional[str] = None,
    token: Optional[str] = None,
    http: Optional[requests.Session] = None,
) -> list:
    """Conjuntos cadastrados, de GET /api/conjuntos."""
    corpo = _buscar("/api/conjuntos", base=base, token=token, http=http)

    return corpo if isinstance(corpo, list) else []


def conjuntos_para_varrer(
    conjuntos: list,
    cadencia: Optional[str] = None,
) -> tuple[list, list]:
    """Separa os conjuntos desta cadencia dos que cairam nela sem cadastro.

    cadencia None pega todos os conjuntos elegiveis, qualquer periodicidade:
    e o que a DAG manual usa.
    """
    desta = []
    sem_cadastro = []

    for conjunto in conjuntos:
        if not isinstance(conjunto, dict):
            continue

        # Sem id nao tem como consultar a fila nem montar o run_id.
        if conjunto.get("id") is None:
            log.warning("Conjunto sem id na resposta da API: %r", conjunto)
            continue

        # ativo ja cobre conjunto ARQUIVADO e fonte desativada.
        if not conjunto.get("ativo", True):
            continue

        if conjunto.get("situacao") == "ARQUIVADO":
            continue

        # Conjunto que nao recebe arquivo nao tem fila de upload para varrer.
        if not conjunto.get("upload", True):
            continue

        alvo, reconhecida = normalizar_cadencia(conjunto.get("periodicidade"))

        if cadencia is not None and alvo != cadencia:
            continue

        desta.append(conjunto)

        if not reconhecida:
            sem_cadastro.append(conjunto)

    return desta, sem_cadastro


# Situacoes de arq_status que a varredura automatica pode disparar. O
# resolverAlvos do back-end devolve tudo que nao esta PROCESSADO, de proposito,
# porque o POST /api/pipeline/lote e disparado por gente. Na varredura nao tem
# ninguem olhando, e disparar RECEBENDO processaria um upload pela metade.
SITUACOES_DISPARAVEIS = tuple(
    pedaco.strip().upper()
    for pedaco in os.environ.get(
        "GEORURAL_SITUACOES_DISPARAVEIS", "RECEBIDO,APROVADO").split(",")
    if pedaco.strip()
)

# Por que cada uma das outras fica de fora, para o log dizer o motivo.
MOTIVO_PARA_NAO_DISPARAR = {
    "RECEBENDO": "o upload ainda esta em andamento",
    "VALIDANDO": "ja tem etapa rodando nesse arquivo agora",
    "REJEITADO": "reprovado na validacao; soltar da quarentena e decisao de gente",
    "FALHA": "o envio nao chegou na zona bruta; precisa subir o arquivo de novo",
    "PROCESSADO": "ja foi concluido",
    "NULL": "esta sem situacao gravada no banco",
    "": "esta sem situacao gravada no banco",
}


def arquivos_a_disparar(fila: list) -> tuple[list, list]:
    """Separa a fila entre o que a varredura dispara e o que ela deixa parado.

    Devolve (prontos, adiados), com adiados = [(arquivo, motivo)].
    """
    prontos = []
    adiados = []

    for arquivo in fila:
        situacao = str(arquivo.get("situacao") or "").strip().upper()

        if situacao in SITUACOES_DISPARAVEIS:
            prontos.append(arquivo)
            continue

        motivo = MOTIVO_PARA_NAO_DISPARAR.get(
            situacao, f"a situacao {situacao} nao esta na lista de disparaveis")
        adiados.append((arquivo, motivo))

    return prontos, adiados


def pendentes(
    conjunto_id: Any,
    base: Optional[str] = None,
    token: Optional[str] = None,
    http: Optional[requests.Session] = None,
) -> list:
    """Arquivos que o lote pegaria agora, de GET /api/pipeline/lote/pendentes."""
    corpo = _buscar(
        "/api/pipeline/lote/pendentes",
        parametros={"conjuntoId": int(conjunto_id)},
        base=base,
        token=token,
        http=http,
    )

    arquivos = corpo.get("arquivos") if isinstance(corpo, dict) else None

    return [a for a in (arquivos or []) if isinstance(a, dict) and a.get("id")]


def _buscar(
    caminho: str,
    parametros: Optional[Dict[str, Any]] = None,
    base: Optional[str] = None,
    token: Optional[str] = None,
    http: Optional[requests.Session] = None,
) -> Any:
    """GET numa rota da API, com o mesmo tratamento de status do POST de etapa."""
    base = (base or base_da_api()).rstrip("/")
    token = token if token is not None else token_da_api()
    http = http or sessao()

    url = f"{base}{caminho}"
    cabecalhos = {"Accept": "application/json"}

    if token:
        cabecalhos["Authorization"] = f"Bearer {token}"

    try:
        resposta = http.get(
            url,
            params=parametros,
            headers=cabecalhos,
            timeout=(TIMEOUT_CONEXAO_SEG, TIMEOUT_CONSULTA_SEG),
        )
    except requests.RequestException as erro:
        raise FalhaTemporaria(f"Nao consegui falar com a API em {caminho}: {erro}") from erro

    codigo = resposta.status_code

    if 400 <= codigo < 500:
        raise FalhaDefinitiva(
            f"A API recusou {caminho} com {codigo}: {_texto(resposta)}"
        )

    if codigo >= 500:
        raise FalhaTemporaria(
            f"A API falhou em {caminho} com {codigo}: {_texto(resposta)}"
        )

    try:
        return resposta.json()
    except Exception as erro:
        raise FalhaTemporaria(
            f"A resposta de {caminho} nao e JSON: {erro}"
        ) from erro


def executar_etapa(
    etapa: str,
    corpo: Dict[str, Any],
    base: Optional[str] = None,
    token: Optional[str] = None,
    http: Optional[requests.Session] = None,
    timeout_leitura: Optional[int] = None,
) -> Dict[str, Any]:
    """Roda uma etapa e devolve o EtapaExecutadaResponse.

    Levanta EtapaSemImplementacao em 501, FalhaDefinitiva em 4xx e
    FalhaTemporaria em 5xx ou erro de rede.
    """
    base = (base or base_da_api()).rstrip("/")
    token = token if token is not None else token_da_api()
    http = http or sessao()
    leitura = timeout_leitura or timeout_de_leitura()

    url = f"{base}/api/pipeline/etapas/{etapa}"
    cabecalhos = {"Content-Type": "application/json"}

    if token:
        cabecalhos["Authorization"] = f"Bearer {token}"

    log.info("[%s] POST %s", corpo.get("dagRunId"), url)

    try:
        resposta = http.post(
            url,
            json=corpo,
            headers=cabecalhos,
            timeout=(TIMEOUT_CONEXAO_SEG, leitura),
        )
    except requests.RequestException as erro:
        raise FalhaTemporaria(f"Nao consegui falar com a API: {erro}") from erro

    return _interpretar(etapa, resposta)


def _interpretar(etapa: str, resposta: Any) -> Dict[str, Any]:
    codigo = resposta.status_code

    if codigo == 501:
        raise EtapaSemImplementacao(
            f"A etapa {etapa} nao esta implementada na API: {_texto(resposta)}"
        )

    if codigo == 409:
        raise FalhaDefinitiva(
            f"Conflito na etapa {etapa}: {_texto(resposta)}. "
            "Costuma ser outra rodada viva no mesmo conjunto; "
            "repetir agora nao resolve."
        )

    if 400 <= codigo < 500:
        raise FalhaDefinitiva(
            f"A API recusou a etapa {etapa} com {codigo}: {_texto(resposta)}"
        )

    if codigo >= 500:
        raise FalhaTemporaria(
            f"A API falhou na etapa {etapa} com {codigo}: {_texto(resposta)}"
        )

    corpo = _json(resposta)

    situacao = corpo.get("situacao")
    log.info(
        "[%s] etapa %s -> %s (recebidos=%s, aprovados=%s, rejeitados=%s, execucao=%s)",
        corpo.get("dagRunId"),
        corpo.get("etapa", etapa),
        situacao,
        corpo.get("recebidos"),
        corpo.get("aprovados"),
        corpo.get("rejeitados"),
        corpo.get("execucaoId"),
    )

    if situacao == "COM_RESSALVA":
        log.warning(
            "[%s] etapa %s terminou COM_RESSALVA: %s",
            corpo.get("dagRunId"),
            etapa,
            corpo.get("mensagem"),
        )

    if situacao == "FALHOU":
        raise FalhaDefinitiva(
            f"A etapa {etapa} respondeu 200 com situacao FALHOU: "
            f"{corpo.get('mensagem')}"
        )

    return corpo


def _texto(resposta: Any) -> str:
    corpo = _json(resposta)

    if isinstance(corpo, dict):
        for chave in ("mensagem", "erro", "message"):
            if corpo.get(chave):
                return str(corpo[chave])

    texto = getattr(resposta, "text", "") or ""
    return texto[:300]


def _json(resposta: Any) -> Dict[str, Any]:
    try:
        corpo = resposta.json()
    except Exception:
        return {}

    return corpo if isinstance(corpo, dict) else {}
