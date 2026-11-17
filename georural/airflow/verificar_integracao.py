#!/usr/bin/env python3
"""Confere, sem Airflow, se a API responde o que as DAGs precisam.

Rode antes de plugar o Airflow:

    python3 verificar_integracao.py http://localhost:8080 [token]

Nao dispara rodada nenhuma: so consulta e mostra o que a varredura faria.
Sai com codigo 1 se alguma coisa que as DAGs dependem nao respondeu.
"""

import os
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "dags"))

import georural_api as api

VERDE = "\033[32m"
VERMELHO = "\033[31m"
AMARELO = "\033[33m"
FIM = "\033[0m"

problemas = []


def ok(mensagem):
    print(f"  {VERDE}ok{FIM}    {mensagem}")


def aviso(mensagem):
    print(f"  {AMARELO}aviso{FIM} {mensagem}")


def erro(mensagem):
    print(f"  {VERMELHO}erro{FIM}  {mensagem}")
    problemas.append(mensagem)


def main():
    # Sem argumento, usa a mesma resolucao da DAG: Connection, Variable,
    # variavel de ambiente. Rodando dentro do container, isso confere
    # exatamente o que a DAG vai usar.
    if len(sys.argv) > 1:
        # URL na mao: nao toca no Airflow, para rodar em qualquer maquina.
        base = sys.argv[1].rstrip("/")
        # "" em vez de None: o cliente so procura Connection quando vem None.
        token = (sys.argv[2] if len(sys.argv) > 2
                 else os.environ.get("GEORURAL_API_TOKEN") or "")
    else:
        base = api.base_da_api()
        token = api.token_da_api()
    http = api.sessao()

    print(f"\nAPI: {base}")
    print(f"Token: {'sim' if token else 'nao'}")
    print(f"Origem: {'argumento da linha de comando' if len(sys.argv) > 1 else 'Connection/Variable/ambiente, igual a DAG'}\n")

    # 1. a API esta no ar e diz quais etapas existem
    print("1. GET /api/pipeline/etapas")
    try:
        etapas = api._buscar("/api/pipeline/etapas", base=base, token=token, http=http)
    except Exception as e:
        erro(f"nao deu para listar as etapas: {e}")
        etapas = []
    else:
        implementadas = [e["etapa"] for e in etapas if e.get("implementada")]
        faltando = [e for e in api.ETAPAS_ENCADEADAS if e not in implementadas]
        ok(f"{len(etapas)} etapa(s) no fluxo, {len(implementadas)} implementada(s)")
        if faltando:
            aviso(f"a DAG do pipeline vai pular: {', '.join(faltando)}")

    # 2. os conjuntos, e em que cadencia cada um cai
    print("\n2. GET /api/conjuntos")
    try:
        conjuntos = api.listar_conjuntos(base=base, token=token, http=http)
    except Exception as e:
        erro(f"nao deu para listar os conjuntos: {e}")
        print(resumo())
        return

    ok(f"{len(conjuntos)} conjunto(s) cadastrado(s)")

    if not conjuntos:
        aviso("sem conjunto cadastrado, a varredura nao tem o que fazer")

    campos = {"id", "nome", "periodicidade", "ativo", "situacao", "upload"}
    for conjunto in conjuntos[:1]:
        ausentes = campos - set(conjunto)
        if ausentes:
            erro(f"o conjunto nao traz {', '.join(sorted(ausentes))}; "
                 "o filtro da varredura depende desses campos")

    print("\n3. Em que DAG cada conjunto cai")
    por_cadencia = {}
    for cadencia in list(api.CADENCIAS) + [api.CADENCIA_MANUAL]:
        por_cadencia[cadencia] = api.conjuntos_para_varrer(conjuntos, cadencia)[0]

    fora = [c for c in conjuntos
            if isinstance(c, dict)
            and not any(c in lista for lista in por_cadencia.values())]

    for cadencia, lista in por_cadencia.items():
        agenda = api.CADENCIAS.get(cadencia, "sem agenda (DAG manual)")
        nomes = ", ".join(f"{c['id']}:{c['nome']}" for c in lista) or "-"
        print(f"  {cadencia:<12} {agenda:<22} {nomes}")

    if fora:
        nomes = ", ".join(f"{c.get('id')}:{c.get('nome')}" for c in fora)
        aviso(f"fora de qualquer varredura (inativo ou sem upload): {nomes}")

    sem_cadastro = api.conjuntos_para_varrer(conjuntos, None)[1]
    if sem_cadastro:
        nomes = ", ".join(f"{c.get('id')}:{c.get('nome')}"
                          f"={c.get('periodicidade')!r}" for c in sem_cadastro)
        aviso(f"periodicidade em branco ou nao reconhecida, cai na diaria: {nomes}")

    # 4. a fila de cada conjunto e o que seria disparado
    print("\n4. GET /api/pipeline/lote/pendentes de cada conjunto")
    alvos = []
    parados = []
    for conjunto in api.conjuntos_para_varrer(conjuntos, None)[0]:
        conjunto_id = conjunto["id"]
        try:
            fila = api.pendentes(conjunto_id, base=base, token=token, http=http)
        except Exception as e:
            erro(f"conjunto {conjunto_id}: {e}")
            continue

        prontos, adiados = api.arquivos_a_disparar(fila)

        ok(f"conjunto {conjunto_id} ({conjunto['nome']}): "
           f"{len(fila)} pendente(s), {len(prontos)} para disparar")

        for arquivo in prontos:
            alvos.append((
                api.montar_run_id(conjunto_id, arquivo.get("id")),
                conjunto["nome"],
                arquivo.get("nome"),
                arquivo.get("situacao"),
            ))

        for arquivo, motivo in adiados:
            parados.append((conjunto["nome"], arquivo.get("nome"),
                            arquivo.get("situacao"), motivo))

    print(f"\n5. O que a varredura disparia agora, em "
          f"{', '.join(api.SITUACOES_DISPARAVEIS)} (nada foi disparado)")
    if not alvos:
        print("  nada pendente")
    for run_id, conjunto, arquivo, situacao in alvos:
        print(f"  {run_id:<32} {conjunto} / {arquivo} [{situacao}]")

    if parados:
        print("\n6. O que fica parado, e por que")
        for conjunto, arquivo, situacao, motivo in parados:
            print(f"  [{situacao}] {conjunto} / {arquivo}: {motivo}")

    print(resumo())


def resumo():
    if problemas:
        return (f"\n{VERMELHO}{len(problemas)} problema(s){FIM} — as DAGs nao vao "
                "funcionar ate resolver isso.\n")
    return f"\n{VERDE}Tudo que as DAGs precisam respondeu.{FIM}\n"


if __name__ == "__main__":
    main()
    sys.exit(1 if problemas else 0)
