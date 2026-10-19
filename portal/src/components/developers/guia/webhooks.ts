/**
 * Los ejemplos de la sección «Webhooks» de la guía (#19). El cuerpo de ejemplo tiene la forma real de `PayloadDeWebhook` (backend), en una sola línea como se envía, y su firma está
 * calculada con el secreto `whsec_ejemplo`; los verificadores recalculan la firma
 * como `FirmaWebhook`: HMAC-SHA256 con el secreto sobre «t.cuerpo». `webhooks.test.ts` comprueba el algoritmo contra el mismo vector que el test del backend.
 */
export const EJEMPLO_ENTREGA = `POST /khipu/webhook HTTP/1.1
Content-Type: application/json; charset=utf-8
User-Agent: khipu-webhooks/1
X-Khipu-Event: comprobante.aceptado
X-Khipu-Delivery: 0b9a7c1e-5f43-4f0e-9d63-2f3e6b6f8f10
X-Khipu-Signature: t=1792339200,v1=031e06e16dee2de83f1074a4f61cc270225c618400e33347cc1db206d9bc7108

{"evento":"comprobante.aceptado","creado_en":"2026-10-18T16:00:00.512Z","datos":{"id":"5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f","tipo":"01","serie":"F001","numero":125,"fecha_emision":"2026-10-18","estado":"ACEPTADO","estado_anterior":"ENVIADO","detalle":"0 - La Factura numero F001-125, ha sido aceptada","cdr":{"codigo":"0","descripcion":"La Factura numero F001-125, ha sido aceptada","observaciones":[]},"ultimo_error":null}}`;

export const VERIFICAR_NODE = `import crypto from "node:crypto";

// Use el cuerpo CRUDO de la petición (los bytes tal como llegaron), no el JSON ya parseado y vuelto a escribir.
export function verificarKhipu(cuerpoCrudo, cabeceraFirma, secreto, toleranciaSegundos = 300) {
  const partes = Object.fromEntries(cabeceraFirma.split(",").map((p) => p.split("=")));
  const t = Number(partes.t);
  if (!Number.isFinite(t) || Math.abs(Date.now() / 1000 - t) > toleranciaSegundos) return false; // entrega vieja: posible repetición
  const esperada = crypto.createHmac("sha256", secreto).update(t + "." + cuerpoCrudo).digest("hex");
  const recibida = Buffer.from(partes.v1 ?? "", "hex");
  const calculada = Buffer.from(esperada, "hex");
  return recibida.length === calculada.length && crypto.timingSafeEqual(recibida, calculada);
}`;

export const VERIFICAR_PYTHON = `import hashlib, hmac, time

def verificar_khipu(cuerpo_crudo: bytes, cabecera_firma: str, secreto: str, tolerancia_segundos: int = 300) -> bool:
    # cuerpo_crudo: los bytes tal como llegaron, no el JSON vuelto a escribir.
    try:
        partes = dict(p.split("=", 1) for p in cabecera_firma.split(","))
        t = int(partes["t"])
    except (KeyError, ValueError):
        return False
    if abs(time.time() - t) > tolerancia_segundos:
        return False  # entrega vieja: posible repetición
    esperada = hmac.new(secreto.encode(), f"{t}.".encode() + cuerpo_crudo, hashlib.sha256).hexdigest()
    return hmac.compare_digest(esperada.encode(), partes.get("v1", "").encode())  # en bytes: un v1 no ASCII no debe lanzar`;

export const VERIFICAR_PHP = `<?php
function verificarKhipu(string $cuerpoCrudo, string $cabeceraFirma, string $secreto, int $toleranciaSegundos = 300): bool {
    // $cuerpoCrudo: file_get_contents('php://input'), tal como llegó.
    $partes = [];
    foreach (explode(',', $cabeceraFirma) as $p) {
        [$k, $v] = array_pad(explode('=', $p, 2), 2, '');
        $partes[$k] = $v;
    }
    $t = (int)($partes['t'] ?? 0);
    if ($t === 0 || abs(time() - $t) > $toleranciaSegundos) return false; // entrega vieja: posible repetición
    $esperada = hash_hmac('sha256', $t . '.' . $cuerpoCrudo, $secreto);
    return hash_equals($esperada, $partes['v1'] ?? '');
}`;

export const RESPUESTA_CREAR_WEBHOOK = `{
  "estado": "exito",
  "datos": {
    "webhook": {
      "id": "7c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f",
      "url": "https://mi-sistema.pe/khipu/webhook",
      "descripcion": "ERP producción",
      "eventos": ["comprobante.aceptado", "comprobante.rechazado"],
      "activo": true,
      "fallos_consecutivos": 0,
      "creado_en": "2026-10-18T15:00:00Z"
    },
    "secreto": "whsec_Zm9vYmFyYmF6cXV4..."
  }
}`;
