# PostgreSQL 18 + pgvector (imagem base) + PostGIS e ltree + pgBackRest (backup contínuo, ADR 0039).
FROM pgvector/pgvector:0.8.7-pg18-trixie

# Correções de segurança do Debian e PostGIS; sem cache do apt na imagem.
# gosu → setpriv (ADR 0022): o binário Go da base sai (com CVEs críticos) e o shim fica em /usr/local/sbin, que vem
# antes no PATH; o entrypoint oficial continua igual.
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && apt-get install -y --no-install-recommends postgresql-18-postgis-3 pgbackrest \
 && rm -rf /var/lib/apt/lists/* /usr/local/bin/gosu \
 && install -d -o postgres -g postgres -m 750 /var/lib/pgbackrest /var/log/pgbackrest
# Sem COPY --chmod: o Testcontainers constrói com o builder legado, que não aceita a opção.
COPY gosu-setpriv.sh /usr/local/sbin/gosu
RUN chmod 755 /usr/local/sbin/gosu

COPY init/ /docker-entrypoint-initdb.d/
