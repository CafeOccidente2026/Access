// Parte R (rendimiento): carga con k6 contra el compose de prueba (nunca contra la base del usuario ni producción).
// Mezcla del plan: 60 % compras (vista previa + factura), 25 % informes, 15 % consultas; pausa de 1 a 3 s.
// Variables:
//   BASE_URL   http://web
//   USUARIOS   archivo con "token,agencia" por línea (R4: un usuario por agencia); cada usuario virtual toma uno.
//   CEDULAS    archivo con "cedula,agencia" por línea: cada agencia le compra a sus propios caficultores.
//   ESPECIAL   especial de las compras (ESTANDAR); FONDO (1).
//   VUS y DURACION (carga fija) o ESCALONES: JSON de k6 ([{"duration":"2m","target":25},...]) para R5/R6.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://web';
const rows = (path) => open(path).split('\n').map((l) => l.trim()).filter(Boolean).map((l) => l.split(','));
const USERS = rows(__ENV.USUARIOS || './usuarios.txt');
const SELLERS = {};
for (const [cedula, agency] of rows(__ENV.CEDULAS || './cedulas.txt')) {
  (SELLERS[agency] = SELLERS[agency] || []).push(cedula);
}
const ALL_SELLERS = Object.values(SELLERS).flat();
const SPECIAL = __ENV.ESPECIAL || 'ESTANDAR';
const FUND = Number(__ENV.FONDO || 1);

const NAMES = ['compra-vista-previa', 'compra-factura', 'informe-compras-30d', 'informe-caja', 'informe-beneficiario',
  'informe-formas-pago', 'consulta-ultima', 'consulta-factura', 'acceso-tablas', 'remesa-pendientes'];
const thresholds = {
  'http_req_failed': ['rate<0.01'],
  'http_req_duration{tipo:api}': ['p(95)<800'],
  'http_req_duration{tipo:compra}': ['p(95)<1500'],
};
// Umbral holgado por endpoint: solo para que el resumen traiga su p95.
for (const n of NAMES) {
  thresholds[`http_req_duration{name:${n}}`] = ['p(95)<60000'];
}

export const options = __ENV.ESCALONES
  ? { scenarios: { escalones: { executor: 'ramping-vus', startVUs: 0, stages: JSON.parse(__ENV.ESCALONES) } }, thresholds }
  : { vus: Number(__ENV.VUS || 10), duration: __ENV.DURACION || '3m', thresholds };

const day = (offset) => new Date(Date.now() - offset * 86400000).toISOString().slice(0, 10);
const pick = (list) => list[Math.floor(Math.random() * list.length)];

function session() {
  const [token, agency] = USERS[(__VU - 1) % USERS.length];
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, agency };
}

function get(s, path, name, tipo = 'api') {
  const res = http.get(`${BASE}${path}`, { headers: s.headers, tags: { name, tipo } });
  if (!check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 })) {
    console.warn(`${name} ${res.status}: ${String(res.body).slice(0, 160)}`);
  }
  return res;
}

function purchase(s) {
  const body = {
    fundId: FUND, specialType: SPECIAL, idNumber: pick(SELLERS[s.agency] || ALL_SELLERS), cellphone: '3000000000',
    bagsCount: 5, grossKg: 300 + Math.floor(Math.random() * 200), tareKg: 2, totalStoredWeight: 205,
    defectiveStoredWeight: 1 + Math.floor(Math.random() * 10), penalty: 0, freightDiscount: 0, otherDiscounts: 0,
  };
  const preview = http.post(`${BASE}/api/purchases/dry-coffee/preview`, JSON.stringify(body),
    { headers: s.headers, tags: { name: 'compra-vista-previa', tipo: 'compra' } });
  if (!check(preview, { 'vista previa 200': (r) => r.status === 200 })) {
    console.warn(`vista previa ${preview.status}: ${String(preview.body).slice(0, 160)}`);
    return;
  }
  body.payment = { cashAmount: preview.json('netToPay'), checkAmount: 0, transferAmount: 0, cardAmount: 0 };
  const created = http.post(`${BASE}/api/purchases/dry-coffee`, JSON.stringify(body),
    { headers: s.headers, tags: { name: 'compra-factura', tipo: 'compra' } });
  if (!check(created, { 'factura 201': (r) => r.status === 201 })) {
    console.warn(`factura ${created.status}: ${String(created.body).slice(0, 160)}`);
  }
}

function report(s) {
  const r = Math.random();
  if (r < 0.3) {
    get(s, `/api/purchases/reports/by-date?from=${day(30)}&to=${day(0)}`, 'informe-compras-30d');
  } else if (r < 0.55) {
    get(s, '/api/supplies/reports/cash?fund=LF', 'informe-caja');
  } else if (r < 0.8) {
    get(s, `/api/vendors/reports/beneficiary?idNumber=${pick(SELLERS[s.agency] || ALL_SELLERS).slice(0, 4)}`, 'informe-beneficiario');
  } else {
    get(s, `/api/supplies/reports/payment-methods?from=${day(30)}&to=${day(0)}`, 'informe-formas-pago');
  }
}

function query(s) {
  const r = Math.random();
  if (r < 0.35) {
    get(s, '/api/purchases/query/navigate?direction=LAST', 'consulta-ultima');
  } else if (r < 0.6) {
    get(s, `/api/purchases/query?invoiceNumber=${100000 + Math.floor(Math.random() * 5000)}`, 'consulta-factura');
  } else if (r < 0.8) {
    get(s, '/api/tables/compras_cafe_seco?limit=100', 'acceso-tablas');
  } else {
    get(s, '/api/remittances/pending?fundCode=LF', 'remesa-pendientes');
  }
}

export default function () {
  const s = session();
  const r = Math.random();
  if (r < 0.6) {
    purchase(s);
  } else if (r < 0.85) {
    report(s);
  } else {
    query(s);
  }
  sleep(1 + Math.random() * 2);
}
