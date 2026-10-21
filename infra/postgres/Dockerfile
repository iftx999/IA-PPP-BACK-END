# PostgreSQL 18 + pgvector (imagem base) + PostGIS e ltree.
FROM pgvector/pgvector:0.8.7-pg18-trixie

RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-18-postgis-3 \
 && rm -rf /var/lib/apt/lists/*

COPY init/ /docker-entrypoint-initdb.d/
