"""DAG do pipeline do GeoRural: as 5 etapas automaticas, encadeadas.

Uma task por etapa, cada uma chamando POST /api/pipeline/etapas/{etapa} com o
run_id do Airflow como dag_run_id. A PUBLICACAO fica de fora de proposito: pelo
enum Etapa ela nao e automatica, e publicar versao e ato do gestor.

Disparo (manual ou pela DAG de descoberta da US#10.04):

    {"conjuntoId": 10, "arquivoBrutoId": 7, "competencia": "2026-03",
     "parametros": {"camadasSelecionadas": "AREA_IMOVEL,RESERVA_LEGAL"}}
"""

from __future__ import annotations

import logging
import os
from datetime import timedelta

import pendulum
from airflow.decorators import dag, task
from airflow.exceptions import AirflowFailException, AirflowSkipException

# O Airflow 3 moveu estas duas para airflow.sdk; o 2.x mantem onde estavam.
try:  # pragma: no cover
    from airflow.sdk import chain, get_current_context
except ImportError:  # pragma: no cover
    from airflow.models.baseoperator import chain
    from airflow.operators.python import get_current_context

from georural_api import (
    ETAPAS_ENCADEADAS,
    EtapaSemImplementacao,
    FalhaDefinitiva,
    FalhaTemporaria,
    executar_etapa,
    montar_corpo,
)

log = logging.getLogger(__name__)

# Config de parse: variavel de ambiente, nao Variable, para nao bater no banco
# do Airflow a cada leitura do arquivo.
MAX_RODADAS = int(os.environ.get("GEORURAL_MAX_RODADAS_PARALELAS", "1"))

# Zero de proposito. Repetir uma etapa cria outra linha em EXECUCAO_PIPELINE e
# pode reaplicar trabalho ja gravado; o caminho pensado para repetir e o
# endpoint da US#10.01, que remonta o contexto. Erro de conexao, em que nada
# chegou a rodar, ja e repetido dentro do cliente HTTP.
RETRIES_ETAPA = int(os.environ.get("GEORURAL_RETRIES_ETAPA", "0"))


def _conf() -> dict:
    """O conf do disparo, caindo para os params da DAG."""
    contexto = get_current_context()
    rodada = contexto.get("dag_run")
    conf = dict(getattr(rodada, "conf", None) or {})

    for nome, valor in (contexto.get("params") or {}).items():
        conf.setdefault(nome, valor)

    conf["_run_id"] = contexto["run_id"]
    return conf


@dag(
    dag_id="georural_pipeline",
    description="Recebimento, validacao, tratamento, calculo e qualidade de um conjunto",
    schedule=None,
    start_date=pendulum.datetime(2026, 1, 1, tz="America/Sao_Paulo"),
    catchup=False,
    max_active_runs=MAX_RODADAS,
    tags=["georural", "pipeline"],
    params={
        "conjuntoId": None,
        "arquivoBrutoId": None,
        "competencia": None,
        "parametros": {},
    },
    default_args={
        "retries": RETRIES_ETAPA,
        "retry_delay": timedelta(minutes=5),
        "retry_exponential_backoff": True,
    },
    doc_md=__doc__,
)
def georural_pipeline():

    @task
    def etapa() -> dict:
        """Roda uma etapa. O task_id e sobrescrito na montagem da cadeia."""
        nome = get_current_context()["task"].task_id.upper()
        conf = _conf()

        corpo = montar_corpo(
            conjunto_id=conf.get("conjuntoId"),
            arquivo_bruto_id=conf.get("arquivoBrutoId"),
            competencia=conf.get("competencia"),
            dag_run_id=conf["_run_id"],
            parametros=conf.get("parametros"),
        )

        try:
            return executar_etapa(nome, corpo)
        except EtapaSemImplementacao as erro:
            # Mesmo comportamento do executarAutomatico: o pipeline para aqui,
            # sem marcar falha, e as etapas seguintes ficam skipped.
            raise AirflowSkipException(str(erro)) from erro
        except FalhaDefinitiva as erro:
            # Nao adianta repetir: requisicao errada, conflito ou etapa que falhou.
            raise AirflowFailException(str(erro)) from erro
        except FalhaTemporaria:
            # Deixa subir como erro comum, para o retry do Airflow pegar.
            raise

    tarefas = [etapa.override(task_id=nome.lower())() for nome in ETAPAS_ENCADEADAS]

    @task(task_id="resumo", trigger_rule="all_done")
    def resumo(resultados: list) -> None:
        """Fecha a rodada no log e lembra que publicar e manual."""
        feitas = [r for r in resultados if isinstance(r, dict)]

        for r in feitas:
            log.info(
                "%s: %s (execucao %s, %ss)",
                r.get("etapa"),
                r.get("situacao"),
                r.get("execucaoId"),
                r.get("duracaoSeg"),
            )

        versoes = [r.get("versaoGerada") for r in feitas if r.get("versaoGerada")]

        if versoes:
            log.info(
                "Versao %s criada como RASCUNHO. A PUBLICACAO nao entra nesta DAG: "
                "publique pelo portal quando o resultado estiver conferido.",
                versoes[-1],
            )
        else:
            log.info(
                "Nenhuma versao nova. Se alguma etapa ficou FALHOU, reexecute so ela: "
                "POST /api/pipeline/execucoes/%s/etapas/<ETAPA>/reexecutar",
                get_current_context().get("run_id"),
            )

    chain(*tarefas)
    resumo(tarefas)


dag_do_pipeline = georural_pipeline()
