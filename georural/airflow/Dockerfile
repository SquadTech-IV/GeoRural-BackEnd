FROM apache/airflow:3.1.2

USER root

COPY --chown=50000:0 dags/ /opt/airflow/dags/
COPY --chown=50000:0 config/ /opt/airflow/config/
COPY --chown=50000:0 verificar_integracao.py /opt/airflow/verificar.py
COPY --chown=50000:0 start-web.sh /opt/airflow/start-web.sh
RUN chmod +x /opt/airflow/start-web.sh

USER 50000
WORKDIR /opt/airflow