// Parte R (rendimiento): carga con k6 contra el compose de prueba (nunca contra la base del usuario ni producción).
// Mezcla del plan: 60 % compras (vista previa + factura), 25 % informes, 15 % consultas; pausa de 1 a 3 s.
// Variables: BASE_URL (http://web), TOKEN (de un usuario de prueba), CEDULAS (archivo con cédulas NESS),
// VUS y DURACION (por defecto 10 y 3m).
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://web';
const CEDULAS = open(__ENV.CEDULAS || './cedulas.txt').split('\n').map((c) => c.trim()).filter(Boolean);
const HEADERS = { Authorization: `Bearer ${__ENV.TOKEN}`, 'Content-Type': 'application/json' };

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURACION || '3m',
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{tipo:api}': ['p(95)<800'],
    'http_req_duration{tipo:compra}': ['p(95)<1500'],
  },
};

const day = (offset) => new Date(Date.now() - offset * 86400000).toISOString().slice(0, 10);
const pick = (list) => list[Math.floor(Math.random() * list.length)];

function get(path, name, tipo = 'api') {
  const res = http.get(`${BASE}${path}`, { headers: HEADERS, tags: { name, tipo } });
  check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return res;
}

function purchase() {
  const body = {
    fundId: 1, specialType: 'NESPRESSO CON TAZA - FTUSA', idNumber: pick(CEDULAS), cellphone: '3000000000',
    bagsCount: 5, grossKg: 300 + Math.floor(Math.random() * 200), tareKg: 2, totalStoredWeight: 205,
    defectiveStoredWeight: 1 + Math.floor(Math.random() * 10), penalty: 0, freightDiscount: 0, otherDiscounts: 0,
  };
  const preview = http.post(`${BASE}/api/purchases/dry-coffee/preview`, JSON.stringify(body),
    { headers: HEADERS, tags: { name: 'compra-vista-previa', tipo: 'compra' } });
  if (!check(preview, { 'vista previa 200': (r) => r.status === 200 })) {
    return;
  }
  body.payment = { cashAmount: preview.json('netToPay'), checkAmount: 0, transferAmount: 0, cardAmount: 0 };
  const created = http.post(`${BASE}/api/purchases/dry-coffee`, JSON.stringify(body),
    { headers: HEADERS, tags: { name: 'compra-factura', tipo: 'compra' } });
  if (!check(created, { 'factura 201': (r) => r.status === 201 })) {
    console.warn(`factura ${created.status}: ${String(created.body).slice(0, 160)}`);
  }
}

function report() {
  const r = Math.random();
  if (r < 0.3) {
    get(`/api/purchases/reports/by-date?from=${day(30)}&to=${day(0)}`, 'informe-compras-30d');
  } else if (r < 0.55) {
    get('/api/supplies/reports/cash?fund=LF', 'informe-caja');
  } else if (r < 0.8) {
    get(`/api/vendors/reports/beneficiary?idNumber=${pick(CEDULAS).slice(0, 4)}`, 'informe-beneficiario');
  } else {
    get(`/api/supplies/reports/payment-methods?from=${day(30)}&to=${day(0)}`, 'informe-formas-pago');
  }
}

function query() {
  const r = Math.random();
  if (r < 0.35) {
    get('/api/purchases/query/navigate?direction=LAST', 'consulta-ultima');
  } else if (r < 0.6) {
    get(`/api/purchases/query?invoiceNumber=${100000 + Math.floor(Math.random() * 5000)}`, 'consulta-factura');
  } else if (r < 0.8) {
    get('/api/tables/compras_cafe_seco?limit=100', 'acceso-tablas');
  } else {
    get('/api/remittances/pending?fundCode=LF', 'remesa-pendientes');
  }
}

export default function () {
  const r = Math.random();
  if (r < 0.6) {
    purchase();
  } else if (r < 0.85) {
    report();
  } else {
    query();
  }
  sleep(1 + Math.random() * 2);
}
