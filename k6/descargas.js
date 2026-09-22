// Escenario "descargas" de docs/spec/spec.md #15: p95 < 300 ms.
// setup() crea N comprobantes una sola vez y cada iteración descarga el XML
// de uno al azar (simula el patrón real: pocas emisiones, muchas descargas).
//
// Uso:
//   API_BASE_URL=http://localhost:8001 API_KEY=fk_... k6 run k6/descargas.js
import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.API_BASE_URL || "http://localhost:8001";
const API_KEY = __ENV.API_KEY;
const SERIE = __ENV.SERIE || "F001";
const SEMILLA = Number(__ENV.SEMILLA || 50);

if (!API_KEY) {
  throw new Error("Define la variable de entorno API_KEY (X-Api-Key del tenant de carga)");
}

export const options = {
  scenarios: {
    descargas: {
      executor: "constant-arrival-rate",
      rate: 20,
      timeUnit: "1s",
      duration: "60s",
      preAllocatedVUs: 50,
      maxVUs: 150,
    },
  },
  thresholds: {
    "http_req_duration{expected_response:true}": ["p(95)<300"],
    http_req_failed: ["rate<0.01"],
  },
};

const headers = { "Content-Type": "application/json", "X-Api-Key": API_KEY };

export function setup() {
  const ids = [];
  for (let i = 0; i < SEMILLA; i++) {
    const res = http.post(
      `${BASE_URL}/v1/facturas`,
      JSON.stringify({
        serie: SERIE,
        fecha_emision: new Date().toISOString().slice(0, 10),
        tipo_operacion: "0101",
        moneda: "PEN",
        cliente: { tipo_doc: "6", num_doc: "20100066603", razon_social: "Cliente de carga k6", direccion: "Av. Prueba 123" },
        items: [{ descripcion: "Servicio de prueba k6", unidad: "NIU", cantidad: 1, precio_unitario: 100, tipo_afectacion_igv: "10" }],
        enviar_automatico: false,
      }),
      { headers },
    );
    if (res.status !== 201) throw new Error(`No se pudo sembrar comprobante: ${res.status} ${res.body}`);
    ids.push(JSON.parse(res.body).datos.id);
  }
  return { ids };
}

export default function (data) {
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const res = http.get(`${BASE_URL}/v1/facturas/${id}/xml`, { headers: { "X-Api-Key": API_KEY } });
  check(res, { "200 con XML": (r) => r.status === 200 && r.body.includes("<") });
}
