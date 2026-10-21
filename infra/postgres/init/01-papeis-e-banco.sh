#!/bin/bash
# Cria os papéis e o banco da Aurora na primeira inicialização do volume.
#   aurora_owner: dono do schema, roda as migrações (Flyway).
#   aurora_app:   usado pela API; sem BYPASSRLS, só recebe GRANTs das migrações.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE aurora_owner LOGIN PASSWORD '${AURORA_OWNER_PASSWORD}' NOSUPERUSER NOBYPASSRLS;
CREATE ROLE aurora_app   LOGIN PASSWORD '${AURORA_APP_PASSWORD}'   NOSUPERUSER NOBYPASSRLS;
CREATE DATABASE aurora OWNER aurora_owner;
SQL

# Extensões exigem superusuário; ficam no schema public do banco aurora.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname aurora <<SQL
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS ltree;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER SCHEMA public OWNER TO aurora_owner;
SQL
