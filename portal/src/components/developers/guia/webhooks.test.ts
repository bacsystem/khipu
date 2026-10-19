import crypto from "node:crypto";
import { describe, expect, it } from "vitest";
import { EJEMPLO_ENTREGA, VERIFICAR_NODE, VERIFICAR_PHP, VERIFICAR_PYTHON } from "./webhooks";

/** La firma de la guía es HMAC-SHA256 del secreto sobre «t.cuerpo» (#19): el mismo vector que `WebhookDominioTest` del backend, que lo reprodujo con openssl. */
describe("ejemplos de webhooks de la guía", () => {
  it("el algoritmo documentado da el vector que fija el backend", () => {
    const hex = crypto.createHmac("sha256", "whsec_prueba").update('1700000000.{"a":1}').digest("hex");
    expect(hex).toBe("0ca0bb9c916989fb6c1d1eb729a180da30cb078b7cbe551c8f8da0ec452ac9db");
  });

  const [cabeceras, cuerpo] = EJEMPLO_ENTREGA.split("\n\n");
  const firma = /X-Khipu-Signature: (.+)/.exec(cabeceras)![1];

  it("la entrega de ejemplo está firmada con whsec_ejemplo y su cuerpo es JSON de una sola línea", () => {
    const t = /t=(\d+)/.exec(firma)![1];
    const v1 = /v1=([0-9a-f]{64})/.exec(firma)![1];
    expect(crypto.createHmac("sha256", "whsec_ejemplo").update(`${t}.${cuerpo}`).digest("hex")).toBe(v1);
    expect(cuerpo).not.toContain("\n");
    expect(JSON.parse(cuerpo)).toMatchObject({ evento: "comprobante.aceptado", datos: { estado: "ACEPTADO", serie: "F001", numero: 125 } });
  });

  it("el verificador de Node de la guía acepta la entrega de ejemplo y rechaza una alterada, una de otro secreto y una vieja", () => {
    const codigo = VERIFICAR_NODE.replace(/^import crypto from "node:crypto";\n/, "").replace("export function", "function");
    const verificar = new Function("crypto", "Buffer", `${codigo}\nreturn verificarKhipu;`)(crypto, Buffer) as (c: string, f: string, s: string, tol?: number) => boolean;
    const tolerante = 10 * 365 * 24 * 3600;

    expect(verificar(cuerpo, firma, "whsec_ejemplo", tolerante)).toBe(true);
    expect(verificar(cuerpo.replace("125", "126"), firma, "whsec_ejemplo", tolerante)).toBe(false);
    expect(verificar(cuerpo, firma, "whsec_otro", tolerante)).toBe(false);
    expect(verificar(cuerpo, firma, "whsec_ejemplo")).toBe(false);
    expect(verificar(cuerpo, "basura", "whsec_ejemplo", tolerante)).toBe(false);
    expect(verificar(cuerpo, firma.replace(/v1=.*/, "v1=zz"), "whsec_ejemplo", tolerante)).toBe(false);
  });

  it("los verificadores de Python y PHP usan el mismo algoritmo y comparan en tiempo constante", () => {
    expect(VERIFICAR_PYTHON).toContain("hmac.compare_digest");
    expect(VERIFICAR_PYTHON).toContain('f"{t}.".encode() + cuerpo_crudo');
    expect(VERIFICAR_PHP).toContain("hash_equals");
    expect(VERIFICAR_PHP).toContain("$t . '.' . $cuerpoCrudo");
  });
});
