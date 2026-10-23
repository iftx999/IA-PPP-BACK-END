# syntax=docker/dockerfile:1.7
# Imagem da API da Aurora (ADR 0022). Multi-stage: compila com o JDK, gera um JRE mínimo (jlink) e roda sobre o
# Ubuntu com o Tesseract (OCR, ADR 0021). Construir a partir de backend/:  docker build -t aurora-api .

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# Testes rodam no CI (precisam de Docker/Testcontainers); aqui só o empacotamento.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests -Dspring-javaformat.skip=true -Djacoco.skip=true package \
 && java -Djarmode=tools -jar target/aurora-api-*.jar extract --layers --launcher --destination /app \
 && java -Djarmode=tools -jar target/aurora-api-*.jar extract --destination /analise

# JRE só com os módulos usados (jdeps) + os carregados dinamicamente: jdk.localedata (datas e números em pt-BR),
# jdk.crypto.ec (TLS), java.naming (Tomcat/Hikari), jdk.zipfs e jdk.charsets.
RUN modulos="$(jdeps --ignore-missing-deps -q --recursive --multi-release 21 --print-module-deps \
                 --class-path '/analise/lib/*' /analise/aurora-api-*.jar)" \
 && jlink --add-modules "${modulos},jdk.localedata,jdk.crypto.ec,java.naming,jdk.zipfs,jdk.charsets" \
          --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output /jre

FROM ubuntu:noble
# Tesseract com português para o OCR; curl para o health check; sem cache do apt na imagem.
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-por curl ca-certificates tzdata \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 aurora \
 && useradd --system --uid 10001 --gid aurora --home-dir /app --shell /usr/sbin/nologin aurora
COPY --from=build /jre /opt/java
WORKDIR /app
# Camadas do Spring Boot: dependências mudam pouco e ficam em cache entre versões.
COPY --from=build --chown=aurora:aurora /app/dependencies/ ./
COPY --from=build --chown=aurora:aurora /app/spring-boot-loader/ ./
COPY --from=build --chown=aurora:aurora /app/snapshot-dependencies/ ./
COPY --from=build --chown=aurora:aurora /app/application/ ./
USER 10001:10001
ENV PATH=/opt/java/bin:$PATH \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp" \
    TZ=America/Sao_Paulo \
    LANG=C.UTF-8
EXPOSE 8080 8081
# Porta 8081 = gerenciamento (saúde e métricas), fora do proxy público.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=4 \
  CMD curl -fsS http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
