// Escenario "emisión" de docs/spec/spec.md #15: p95 < 500 ms sin SUNAT, 50 doc/s.
// enviar_automatico=false para no depender de la latencia de SUNAT (mide solo
// validar + firmar + guardar, que es lo que controla este servicio).
//
// Uso:
//   API_BASE_URL=http://localhost:8001 API_KEY=fk_... k6 run k6/emision.js
//
// La serie no necesita correlativo: el servidor lo asigna de forma atómica
// (FOR UPDATE), así que todas las VUs pueden emitir contra la misma serie sin
// coordinarse.
import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.API_BASE_URL || "http://localhost:8001";
const API_KEY = __ENV.API_KEY;
const SERIE = __ENV.SERIE || "F001";

if (!API_KEY) {
  throw new Error("Define la variable de entorno API_KEY (X-Api-Key del tenant de carga)");
}

export const options = {
  scenarios: {
    emision: {
      executor: "ramping-arrival-rate",
      startRate: 5,
      timeUnit: "1s",
      preAllocatedVUs: 100,
      maxVUs: 300,
      stages: [
        { target: 50, duration: "30s" }, // rampa hasta 50 doc/s
        { target: 50, duration: "60s" }, // sostenido
        { target: 0, duration: "10s" }, // enfriamiento
      ],
    },
  },
  thresholds: {
    "http_req_duration{expected_response:true}": ["p(95)<500"],
    http_req_failed: ["rate<0.01"],
  },
};

function comprobante() {
  return JSON.stringify({
    serie: SERIE,
    fecha_emision: new Date().toISOString().slice(0, 10),
    tipo_operacion: "0101",
    moneda: "PEN",
    cliente: {
      tipo_doc: "6",
      num_doc: "20100066603",
      razon_social: "Cliente de carga k6",
      direccion: "Av. Prueba 123",
    },
    items: [
      {
        descripcion: "Servicio de prueba k6",
        unidad: "NIU",
        cantidad: 1,
        precio_unitario: 100,
        tipo_afectacion_igv: "10",
      },
    ],
    enviar_automatico: false,
  });
}

export default function () {
  const res = http.post(`${BASE_URL}/v1/facturas`, comprobante(), {
    headers: { "Content-Type": "application/json", "X-Api-Key": API_KEY },
  });
  check(res, {
    "201 creado": (r) => r.status === 201,
    "queda FIRMADO": (r) => {
      try {
        return JSON.parse(r.body).datos.estado_documento === "FIRMADO";
      } catch {
        return false;
      }
    },
  });
}
