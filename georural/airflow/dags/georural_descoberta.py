

from __future__ import annotations

import inspect
import logging
import os
import sys
from datetime import timedelta
from typing import Optional

import pendulum
# O Airflow 3 move os decoradores para airflow.sdk e avisa de deprecacao em
# airflow.decorators; o 2.x so tem airflow.decorators.
try:  # pragma: no cover
    from airflow.sdk import dag, task
except ImportError:  # pragma: no cover
    from airflow.decorators import dag, task
from airflow.exceptions import AirflowFailException

# O Airflow 3 moveu get_current_context para airflow.sdk; o 2.x mantem onde estava.
try:  # pragma: no cover
    from airflow.sdk import get_current_context
except ImportError:  # pragma: no cover
    from airflow.operators.python import get_current_context

# O Airflow 3 levou os operadores para o provider standard e avisa de deprecacao
# em airflow.operators; o 2.x antigo so tem airflow.operators.
try:  # pragma: no cover
    from airflow.providers.standard.operators.trigger_dagrun import (
        TriggerDagRunOperator,
    )
except ImportError:  # pragma: no cover
    from airflow.operators.trigger_dagrun import TriggerDagRunOperator

# A pasta do proprio arquivo entra no sys.path antes de importar o cliente.
# O dag-processor do Airflow 3 poe a raiz do bundle no sys.path, mas o Airflow 2,
# o DagBag chamado na mao e um subdiretorio da pasta de DAGs nao poem — e sem
# isso o import abaixo quebra com ModuleNotFoundError.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from georural_api import (
    CADENCIA_MANUAL,
    CADENCIAS,
    SITUACOES_DISPARAVEIS,
    FalhaDefinitiva,
    arquivos_a_disparar,
    conjuntos_para_varrer,
    listar_conjuntos,
    montar_run_id,
    pendentes,
)

log = logging.getLogger(__name__)

DAG_DO_PIPELINE = "georural_pipeline"

# Teto de disparos por varredura, para um acervo atrasado nao enfileirar
# centenas de rodadas de uma vez. O resto entra na varredura seguinte.
MAX_DISPAROS = int(os.environ.get("GEORURAL_MAX_DISPAROS_POR_VARREDURA", "50"))

# skip_when_already_exists existe no Airflow 2.8+ e no 3.x. E ele que transforma
# run_id repetido em skipped em vez de falha. Sem ele, o disparo repetido falha.
_ACEITOS = inspect.signature(TriggerDagRunOperator.__init__).parameters
EXTRAS_DO_TRIGGER = (
    {"skip_when_already_exists": True}
    if "skip_when_already_exists" in _ACEITOS
    else {}
)

if not EXTRAS_DO_TRIGGER:
    log.warning(
        "Este Airflow nao tem skip_when_already_exists (precisa do 2.8 ou mais "
        "novo): arquivo que ja tem rodada vai deixar o disparo vermelho em vez "
        "de skipped."
    )


def _variavel(nome: str, padrao: str) -> str:
    """Le uma Variable do Airflow, caindo para variavel de ambiente."""
    try:
        from airflow.models import Variable

        return Variable.get(nome, default_var=padrao) or padrao
    except Exception:
        return os.environ.get(nome.upper(), padrao)


def _conf() -> dict:
    """O conf do disparo, caindo para os params da DAG."""
    contexto = get_current_context()
    rodada = contexto.get("dag_run")
    conf = dict(getattr(rodada, "conf", None) or {})

    for nome, valor in (contexto.get("params") or {}).items():
        conf.setdefault(nome, valor)

    return conf


def _sufixo(conf: dict) -> Optional[str]:
    """Sufixo do run_id, quando se quer permitir repetir o mesmo arquivo.

    Vem do conf (`sufixo`) ou da Variable `georural_sweep_run_id_por_dia`, que
    por padrao esta desligada: ligada, cada dia pode disparar de novo um arquivo
    que ja falhou.
    """
    if conf.get("sufixo"):
        return str(conf["sufixo"])[:32]

    if _variavel("georural_sweep_run_id_por_dia", "false").strip().lower() != "true":
        return None

    contexto = get_current_context()
    data = contexto.get("logical_date") or contexto.get("data_interval_start")

    return (data or pendulum.now("UTC")).format("YYYYMMDD")


def montar_dag(dag_id: str, cadencia: Optional[str], agenda: Optional[str]):
    """Monta uma DAG de descoberta. cadencia None varre todos os conjuntos."""
    rotulo = cadencia or "manual"

    @dag(
        dag_id=dag_id,
        description=(
            f"Varre os conjuntos de periodicidade {cadencia} e dispara o pipeline"
            if cadencia
            else "Varredura manual: dispara o pipeline para todo arquivo pendente"
        ),
        schedule=agenda,
        start_date=pendulum.datetime(2026, 1, 1, tz="America/Sao_Paulo"),
        catchup=False,
        max_active_runs=1,
        tags=["georural", "descoberta", rotulo],
        default_args={"retries": 1, "retry_delay": timedelta(minutes=10)},
        doc_md=__doc__,
    )
    def descoberta():

        @task
        def procurar() -> list:
            """Lista o que disparar: um dict de argumentos do trigger por arquivo."""
            conf = _conf()
            sufixo = _sufixo(conf)
            so_este = conf.get("conjuntoId")

            try:
                conjuntos = listar_conjuntos()
            except FalhaDefinitiva as erro:
                raise AirflowFailException(str(erro)) from erro

            desta, sem_cadastro = conjuntos_para_varrer(conjuntos, cadencia)

            if so_este is not None:
                desta = [c for c in desta if str(c.get("id")) == str(so_este)]
                log.info("conf.conjuntoId=%s: varrendo so esse conjunto.", so_este)

            log.info(
                "Cadencia %s: %s conjunto(s) a varrer, de %s cadastrado(s). "
                "Dispara arquivo em %s.",
                rotulo,
                len(desta),
                len(conjuntos),
                ", ".join(SITUACOES_DISPARAVEIS),
            )

            for conjunto in sem_cadastro:
                log.warning(
                    "Conjunto %s ('%s') esta sem periodicidade reconhecida (%r) e "
                    "caiu na cadencia %s. Vale arrumar o cadastro.",
                    conjunto.get("id"),
                    conjunto.get("nome"),
                    conjunto.get("periodicidade"),
                    rotulo,
                )

            alvos = []
            indisponiveis = 0

            for conjunto in desta:
                conjunto_id = conjunto.get("id")

                try:
                    fila = pendentes(conjunto_id)
                except FalhaDefinitiva as erro:
                    # Um conjunto com problema nao derruba a varredura dos outros.
                    indisponiveis += 1
                    log.error("Conjunto %s: %s", conjunto_id, erro)
                    continue

                prontos, adiados = arquivos_a_disparar(fila)

                log.info(
                    "Conjunto %s ('%s', periodicidade %s): %s pendente(s), "
                    "%s para disparar agora.",
                    conjunto_id,
                    conjunto.get("nome"),
                    conjunto.get("periodicidade"),
                    len(fila),
                    len(prontos),
                )

                for arquivo, motivo in adiados:
                    log.info(
                        "Conjunto %s: '%s' fica parado porque %s (situacao %s).",
                        conjunto_id,
                        arquivo.get("nome"),
                        motivo,
                        arquivo.get("situacao"),
                    )

                for arquivo in prontos:
                    alvos.append({
                        "trigger_run_id": montar_run_id(
                            conjunto_id, arquivo.get("id"), sufixo),
                        "conf": {
                            "conjuntoId": conjunto_id,
                            "arquivoBrutoId": arquivo.get("id"),
                        },
                        # Tirados do dicionario no log do fim: o que sobra aqui
                        # vai como argumento do operador, que recusa chave extra.
                        "_arquivo": arquivo.get("nome"),
                        "_situacao": arquivo.get("situacao"),
                    })

            if indisponiveis:
                log.warning(
                    "%s conjunto(s) ficaram de fora porque a API recusou a consulta.",
                    indisponiveis,
                )

            if len(alvos) > MAX_DISPAROS:
                log.warning(
                    "A varredura achou %s arquivo(s) e o teto por rodada e %s. "
                    "O resto entra na proxima varredura.",
                    len(alvos),
                    MAX_DISPAROS,
                )
                alvos = alvos[:MAX_DISPAROS]

            if not alvos:
                log.info("Nada pendente na cadencia %s.", rotulo)

            # O indice e o numero da instancia de 'disparar' na interface.
            for indice, alvo in enumerate(alvos):
                log.info(
                    "[%s] %s -> '%s' (situacao %s)",
                    indice,
                    alvo["trigger_run_id"],
                    alvo.pop("_arquivo"),
                    alvo.pop("_situacao"),
                )

            return alvos

        @task(trigger_rule="all_done")
        def resumo(alvos: list) -> None:
            """Fecha a varredura; o resultado de cada arquivo esta no disparo dele."""
            log.info(
                "Varredura %s terminou com %s arquivo(s) na lista. Disparo skipped "
                "e arquivo que ja tinha rodada.",
                rotulo,
                len(alvos or []),
            )

        achados = procurar()

        disparos = TriggerDagRunOperator.partial(
            task_id="disparar",
            trigger_dag_id=DAG_DO_PIPELINE,
            reset_dag_run=False,
            wait_for_completion=False,
            **EXTRAS_DO_TRIGGER,
        ).expand_kwargs(achados)

        disparos >> resumo(achados)

    return descoberta()


# Uma DAG por cadencia agendada. O dicionario e constante: o parse nao chama API.
for _cadencia, _agenda in CADENCIAS.items():
    globals()[f"georural_descoberta_{_cadencia}"] = montar_dag(
        f"georural_descoberta_{_cadencia}", _cadencia, _agenda)

# Varredura manual: pega todos os conjuntos, inclusive os SOB_DEMANDA e UNICA,
# que nao entram em agenda nenhuma. Aceita conf {"conjuntoId": 10} para varrer
# um so, e {"sufixo": "..."} para disparar de novo um arquivo que ja tem rodada.
georural_descoberta_manual = montar_dag("georural_descoberta_manual", None, None)

assert CADENCIA_MANUAL not in CADENCIAS, (
    "SOB_DEMANDA nao deve ganhar agenda: quem varre esses conjuntos e a DAG manual."
)
