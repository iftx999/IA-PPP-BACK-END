# PostgreSQL 18 + pgvector (imagem base) + PostGIS e ltree.
FROM pgvector/pgvector:0.8.7-pg18-trixie

# Correções de segurança do Debian e PostGIS; sem cache do apt na imagem.
# gosu → setpriv (ADR 0022): o binário Go da base sai (com CVEs críticos) e o shim fica em /usr/local/sbin, que vem
# antes no PATH; o entrypoint oficial continua igual.
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && apt-get install -y --no-install-recommends postgresql-18-postgis-3 \
 && rm -rf /var/lib/apt/lists/* /usr/local/bin/gosu
COPY --chmod=755 gosu-setpriv.sh /usr/local/sbin/gosu

COPY init/ /docker-entrypoint-initdb.d/
