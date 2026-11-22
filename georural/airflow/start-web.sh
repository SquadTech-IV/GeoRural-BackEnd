#!/usr/bin/env bash
set -e
airflow db migrate
airflow scheduler &
exec airflow api-server --port "${PORT:-8080}"