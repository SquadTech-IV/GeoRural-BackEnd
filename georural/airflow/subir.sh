#!/usr/bin/env bash
# Sobe o Airflow do GeoRural. Pode rodar quantas vezes quiser.
#
#   ./subir.sh
#
# Na primeira vez gera o .env com as chaves. Depois so sobe.

set -euo pipefail
cd "$(dirname "$0")"

VERDE='\033[32m'; AMARELO='\033[33m'; VERMELHO='\033[31m'; FIM='\033[0m'
ok()    { echo -e "  ${VERDE}ok${FIM}    $1"; }
aviso() { echo -e "  ${AMARELO}aviso${FIM} $1"; }
erro()  { echo -e "  ${VERMELHO}erro${FIM}  $1"; }

echo
echo "Airflow do GeoRural"
echo "==================="
echo

# ---------- 1. docker ----------
if ! command -v docker >/dev/null 2>&1; then
  erro "Docker nao encontrado. Instale o Docker Desktop e rode de novo."
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  erro "Este Docker nao tem 'docker compose'. Atualize o Docker Desktop."
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  erro "O Docker esta instalado mas nao esta rodando. Abra o Docker Desktop."
  exit 1
fi
ok "Docker respondendo"

# ---------- 2. .env ----------
if [ ! -f .env ]; then
  echo
  echo "Primeira vez: gerando o .env"
  cp .env.exemplo .env

  FERNET=$(docker run --rm "apache/airflow:${AIRFLOW_VERSAO:-3.1.2}" \
      python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())" \
      2>/dev/null | tr -d '\r\n')
  if [ -z "$FERNET" ]; then
    erro "nao consegui gerar a chave Fernet (a imagem do Airflow baixou?)"
    exit 1
  fi

  JWT=$(openssl rand -base64 32 2>/dev/null | tr -d '\r\n')
  if [ -z "$JWT" ]; then
    JWT=$(head -c 32 /dev/urandom | base64 | tr -d '\r\n')
  fi

  # O sed do Mac (BSD) precisa do argumento vazio no -i.
  if sed --version >/dev/null 2>&1; then SEDI=(-i); else SEDI=(-i ''); fi
  sed "${SEDI[@]}" "s|^AIRFLOW_FERNET_KEY=.*|AIRFLOW_FERNET_KEY=${FERNET}|" .env
  sed "${SEDI[@]}" "s|^AIRFLOW_JWT_SECRET=.*|AIRFLOW_JWT_SECRET=${JWT}|" .env

  if [ "$(uname -s)" = "Linux" ]; then
    sed "${SEDI[@]}" "s|^AIRFLOW_UID=.*|AIRFLOW_UID=$(id -u)|" .env
    ok "AIRFLOW_UID=$(id -u) (Linux: evita log com dono root)"
  fi

  ok ".env criado com as chaves"
  aviso "o .env nao vai para o git; guarde-o se quiser manter as Connections"
else
  ok ".env ja existe"
fi

# ---------- 3. porta livre ----------
PORTA=$(grep -E '^AIRFLOW_PORTA=' .env | cut -d= -f2)
PORTA=${PORTA:-8081}
if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":${PORTA} "; then
  aviso "a porta ${PORTA} ja esta ocupada; mude AIRFLOW_PORTA no .env se der erro"
fi

# ---------- 4. sobe ----------
echo
echo "Subindo (a primeira vez baixa as imagens, pode levar alguns minutos)..."
docker compose up -d

echo
echo "Esperando a interface responder..."
for _ in $(seq 1 60); do
  if curl -fsS "http://localhost:${PORTA}/api/v2/monitor/health" >/dev/null 2>&1; then
    ok "interface no ar"
    break
  fi
  sleep 3
done

SENHA=$(python3 -c "import json;print(json.load(open('config/simple_auth_manager_passwords.json'))['admin'])" 2>/dev/null || echo georural)

echo
echo "==================================================================="
echo "  Interface:  http://localhost:${PORTA}"
echo "  Usuario:    admin"
echo "  Senha:      ${SENHA}"
echo
echo "  As DAGs nascem PAUSADAS. Ligue na interface quando quiser que"
echo "  comecem a varrer."
echo
echo "  Antes disso, confira se o Airflow alcanca a API do Spring:"
echo "      docker compose exec scheduler python /opt/airflow/verificar.py"
echo
echo "  Logs:       docker compose logs -f"
echo "  Derrubar:   docker compose down"
echo "==================================================================="
echo
