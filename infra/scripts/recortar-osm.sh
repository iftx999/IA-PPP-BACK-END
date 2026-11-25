#!/usr/bin/env bash
# Recorta do extrato do OpenStreetMap (ex.: sudeste-latest.osm.pbf, Geofabrik) a área da concessão, para o Valhalla
# (ADR 0046). Usa o osmium-tool num contêiner Debian descartável; nada é instalado na máquina.
#
#   infra/scripts/recortar-osm.sh <extrato.osm.pbf> <oeste,sul,leste,norte> <saida.osm.pbf>
#   ex.: infra/scripts/recortar-osm.sh ~/osm/sudeste-latest.osm.pbf -47.95,-22.95,-47.20,-22.30 ~/osm/regiao.osm.pbf
#
# Depois: AURORA_OSM_RECORTE=<saida> VALHALLA_FORCE_REBUILD=True docker compose -f infra/compose.yaml up -d valhalla
set -euo pipefail

if [[ $# -ne 3 ]]; then
  sed -n '2,8p' "$0" >&2
  exit 2
fi
entrada=$(realpath "$1")
caixa=$2
saida=$(realpath -m "$3")
if [[ ! $caixa =~ ^-?[0-9.]+,-?[0-9.]+,-?[0-9.]+,-?[0-9.]+$ ]]; then
  echo "Caixa inválida: use oeste,sul,leste,norte em graus decimais." >&2
  exit 2
fi

docker run --rm \
  -v "$(dirname "$entrada")":/entrada:ro \
  -v "$(dirname "$saida")":/saida \
  debian:trixie-slim sh -c "
    apt-get update -qq >/dev/null && apt-get install -y -qq osmium-tool >/dev/null &&
    osmium extract -b '$caixa' --strategy=smart --overwrite \
      -o '/saida/$(basename "$saida")' '/entrada/$(basename "$entrada")' &&
    chown $(id -u):$(id -g) '/saida/$(basename "$saida")'"
echo "Recorte gravado em $saida"
