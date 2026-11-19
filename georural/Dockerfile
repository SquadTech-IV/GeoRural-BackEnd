# --- Estágio 1: Build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# --- Estágio 2: Runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh

# Garante LF e permissão de execução caso o arquivo tenha sido criado no Windows
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

# A Wallet do Oracle é montada em tempo de execução via ORACLE_WALLET_BASE64,
# nunca copiada via COPY dentro da imagem.
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]