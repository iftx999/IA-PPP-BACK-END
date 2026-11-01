// Teste de carga das leituras (doc 09, 9.9; ADR 0034). Mede o p95 de cada fluxo contra a meta e reprova se passar.
//
//   ./preparar-cliente-de-carga.sh            # uma vez, no Keycloak local
//   docker run --rm -i --network host -v "$PWD:/carga" grafana/k6:1.3.0 run /carga/leitura.js
//
// Variáveis: API (padrão http://localhost:8080), KEYCLOAK (http://localhost:8180), REALM (aurora),
// USUARIO/SENHA (admin@exemplo.dev / aurora-dev), TAXA (requisições por segundo, padrão 20), DURACAO (2m).
import http from 'k6/http';
import { check } from 'k6';

const API = __ENV.API || 'http://localhost:8080';
const KEYCLOAK = __ENV.KEYCLOAK || 'http://localhost:8180';
const REALM = __ENV.REALM || 'aurora';
const TAXA = Number(__ENV.TAXA || 20);
const DURACAO = __ENV.DURACAO || '2m';

const cenario = (exec, fracao) => ({
  executor: 'constant-arrival-rate',
  exec,
  rate: Math.max(1, Math.round(TAXA * fracao)),
  timeUnit: '1s',
  duration: DURACAO,
  preAllocatedVUs: 20,
  maxVUs: 100,
});

export const options = {
  // Mistura de uso: painel e troca de competência dominam; mapa e lista em seguida.
  scenarios: {
    painel: cenario('painel', 0.35),
    trocaDeCompetencia: cenario('trocaDeCompetencia', 0.15),
    semLigacao: cenario('semLigacao', 0.2),
    tiles: cenario('tiles', 0.3),
  },
  // Metas do doc 09 (9.9). Tiles medidos sem cache do navegador (a meta de 500 ms é a "sem cache").
  thresholds: {
    'http_req_duration{fluxo:painel}': ['p(95)<300'],
    'http_req_duration{fluxo:troca-de-competencia}': ['p(95)<400'],
    'http_req_duration{fluxo:sem-ligacao}': ['p(95)<250'],
    'http_req_duration{fluxo:tile}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const token = http.post(`${KEYCLOAK}/realms/${REALM}/protocol/openid-connect/token`, {
    client_id: 'aurora-carga',
    grant_type: 'password',
    username: __ENV.USUARIO || 'admin@exemplo.dev',
    password: __ENV.SENHA || 'aurora-dev',
  }).json('access_token');
  const cabecalhos = { headers: { Authorization: `Bearer ${token}` } };
  // Só unidades com painel (ETE/EEE não têm adesão; unidade sem medição responde 404 "sem dados"), cada uma com as
  // competências em que tem medição.
  const unidades = [];
  for (const u of http.get(`${API}/api/v1/unidades`, cabecalhos).json()) {
    const r = http.get(`${API}/api/v1/painel?unidade=${u.id}`,
      { ...cabecalhos, responseCallback: http.expectedStatuses(200, 404) });
    if (r.status === 200) {
      unidades.push({ id: u.id, competencias: r.json('competenciasDisponiveis') });
    }
  }
  const painel = http.get(`${API}/api/v1/painel`, cabecalhos).json();
  return { token, unidades, atual: painel.competencia };
}

const sortear = (lista) => lista[Math.floor(Math.random() * lista.length)];
const com = (dados, fluxo) => ({ headers: { Authorization: `Bearer ${dados.token}` }, tags: { fluxo } });

export function painel(dados) {
  const r = http.get(`${API}/api/v1/painel?unidade=${sortear(dados.unidades).id}`, com(dados, 'painel'));
  check(r, { 'painel 200': (x) => x.status === 200 });
}

export function trocaDeCompetencia(dados) {
  const unidade = sortear(dados.unidades);
  const r = http.get(`${API}/api/v1/painel?unidade=${unidade.id}&competencia=${sortear(unidade.competencias)}`,
    com(dados, 'troca-de-competencia'));
  check(r, { 'competência 200': (x) => x.status === 200 });
}

export function semLigacao(dados) {
  const situacao = sortear(['', '&situacao=FACTIVEL', '&situacao=CORTADO']);
  const r = http.get(`${API}/api/v1/imoveis/sem-ligacao?unidade=${sortear(dados.unidades).id}&limite=50${situacao}`,
    com(dados, 'sem-ligacao'));
  check(r, { 'sem ligação 200': (x) => x.status === 200 });
}

// Área dos imóveis de demonstração (Limeira/Piracicaba): tiles dos zooms 12 a 16 sobre ela.
const AREA = { oeste: -47.66, sul: -22.74, leste: -47.39, norte: -22.55 };
function tile(z) {
  const n = 2 ** z;
  const lon = AREA.oeste + Math.random() * (AREA.leste - AREA.oeste);
  const lat = (AREA.sul + Math.random() * (AREA.norte - AREA.sul)) * Math.PI / 180;
  const x = Math.floor(((lon + 180) / 360) * n);
  const y = Math.floor(((1 - Math.log(Math.tan(lat) + 1 / Math.cos(lat)) / Math.PI) / 2) * n);
  return `${z}/${x}/${y}`;
}

export function tiles(dados) {
  const r = http.get(`${API}/api/v1/mapa/tiles/${tile(sortear([12, 13, 14, 15, 16]))}.mvt?competencia=${dados.atual}`,
    com(dados, 'tile'));
  check(r, { 'tile 200/204': (x) => x.status === 200 || x.status === 204 });
}
