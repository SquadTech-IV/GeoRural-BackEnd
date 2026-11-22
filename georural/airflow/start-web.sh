#!/usr/bin/env bash
set -e
airflow db migrate
exec airflow api-server --port "${PORT:-8080}"