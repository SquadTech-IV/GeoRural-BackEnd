""" Cliente da API do GeoRural usada pelas DAGs """

from __future__ import annotations

import logging
import os
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

ETAPAS_ENCADEADAS = ("INGESTAO", "VALIDACAO", "TRATAMENTO", "CALCULO", "QUALIDADE")


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


def base_da_api() -> str:
    base, _ = _por_conexao()
    base = base or _por_variavel("georural_api_base") or "http://localhost:8080"
    return base.rstrip("/")


def token_da_api() -> Optional[str]:
    _, token = _por_conexao()
    return token or _por_variavel("georural_api_token")


def timeout_de_leitura() -> int:
    bruto = _por_variavel("georural_timeout_etapa_seg")
    try:
        return int(bruto) if bruto else TIMEOUT_LEITURA_PADRAO_SEG
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
