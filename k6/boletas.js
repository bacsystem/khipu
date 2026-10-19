// Escenario «boletas» (#287): la emisión de boletas por API, con los mismos umbrales que `emision.js` (p95 < 500 ms sin SUNAT, 50 doc/s).
// Cada boleta va sola por sendBill (opción A: el resumen diario solo se usa para bajas y boletas atrasadas), así que lo que se mide es lo mismo que en la factura —validar,
// numerar, firmar, validar contra el XSD y guardar—, pero con las reglas propias de la boleta: la mitad lleva comprador con DNI y la otra mitad «sin documento» (hasta S/ 700).
// `enviar_automatico=false` para no depender de la latencia de SUNAT.
//
// Uso:
//   API_BASE_URL=http://localhost:8001 API_KEY=fk_... k6 run k6/boletas.js
// La empresa necesita la serie B001 de tipo 03: `SERIES="01:F001 03:B001" PLATFORM_ADMIN_KEY=... ./k6/preparar-tenant.sh`.
import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";

// La fecha de emisión se compara con la de Lima (UTC-5, sin horario de verano): con la fecha UTC, entre las 19:00 y las 24:00 de Lima la API responde 422 FECHA_INVALIDA (fecha futura).
const hoyEnLima = () => new Date(Date.now() - 5 * 3600 * 1000).toISOString().slice(0, 10);

const BASE_URL = __ENV.API_BASE_URL || "http://localhost:8001";
const API_KEY = __ENV.API_KEY;
// 500 ms es el objetivo; CI lo sube (UMBRAL_P95_MS) porque el runner compartido es mucho más lento que una laptop y ahí solo debe saltar una regresión grande.
const P95_MAXIMO_MS = __ENV.UMBRAL_P95_MS || "500";
const SERIE = __ENV.SERIE || "B001";

if (!API_KEY) {
  throw new Error("Define la variable de entorno API_KEY (X-Api-Key del tenant de carga)");
}

export const options = {
  scenarios: {
    boletas: {
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
    "http_req_duration{expected_response:true}": [`p(95)<${P95_MAXIMO_MS}`],
    http_req_failed: ["rate<0.01"],
    // Una respuesta 201 que no es lo que se pidió (otro tipo, otro estado) no puede pasar como éxito, y un servidor lento que no alcanza los 50 doc/s descarta iteraciones
    // mientras el p95 de las que sí corren sigue bien: sin estos dos umbrales «50 doc/s» no se exigiría.
    checks: ["rate>0.99"],
    dropped_iterations: ["count<20"],
  },
};

const DNI = { tipo_doc: "1", num_doc: "12345678", razon_social: "Cliente DNI de carga k6" };
const SIN_DOCUMENTO = { tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS" };

function boleta(i) {
  return JSON.stringify({
    serie: SERIE,
    fecha_emision: hoyEnLima(),
    tipo_operacion: "0101",
    moneda: "PEN",
    cliente: i % 2 === 0 ? DNI : SIN_DOCUMENTO,
    items: [
      { descripcion: "Menú ejecutivo", unidad: "NIU", cantidad: 2, precio_unitario: 18.0, tipo_afectacion_igv: "10" },
      { descripcion: "Chicha morada 1 L", unidad: "NIU", cantidad: 1, precio_unitario: 9.5, tipo_afectacion_igv: "10" },
    ],
    enviar_automatico: false,
  });
}

export default function () {
  const res = http.post(`${BASE_URL}/v1/facturas`, boleta(exec.scenario.iterationInTest), {
    headers: { "Content-Type": "application/json", "X-Api-Key": API_KEY },
  });
  check(res, {
    "201 creado": (r) => r.status === 201,
    "es una boleta FIRMADA": (r) => {
      try {
        const d = JSON.parse(r.body).datos;
        return d.tipo === "03" && d.estado_documento === "FIRMADO";
      } catch {
        return false;
      }
    },
  });
}
