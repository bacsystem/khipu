import { spawnSync } from "node:child_process";
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

  /** Los mismos seis casos para cada lenguaje: el de la guía acepta el ejemplo y rechaza lo alterado, lo de otro secreto, lo viejo, lo basura y un v1 no ASCII (que no debe lanzar). */
  const casos = () => {
    const tolerante = 10 * 365 * 24 * 3600;
    return [
      { cuerpo, firma, secreto: "whsec_ejemplo", tol: tolerante },
      { cuerpo: cuerpo.replace("125", "126"), firma, secreto: "whsec_ejemplo", tol: tolerante },
      { cuerpo, firma, secreto: "whsec_otro", tol: tolerante },
      { cuerpo, firma, secreto: "whsec_ejemplo", tol: 300 },
      { cuerpo, firma: "basura", secreto: "whsec_ejemplo", tol: tolerante },
      { cuerpo, firma: firma.replace(/v1=.*/, "v1=ñandú"), secreto: "whsec_ejemplo", tol: tolerante },
    ];
  };
  const esperado = [true, false, false, false, false, false];
  const tiene = (cmd: string, args: string[]) => spawnSync(cmd, args, { encoding: "utf8" }).status === 0;
  const python = ["python3", "python"].find((c) => tiene(c, ["--version"]));
  const php = tiene("php", ["--version"]) ? "php" : undefined;

  it.skipIf(!python)("el verificador de Python de la guía, ejecutado de verdad, da lo esperado en los seis casos", () => {
    const programa = `${VERIFICAR_PYTHON}
import json, sys
print(json.dumps([verificar_khipu(c["cuerpo"].encode(), c["firma"], c["secreto"], c["tol"]) for c in json.load(sys.stdin)]))
`;
    const r = spawnSync(python!, ["-I", "-c", programa], { input: JSON.stringify(casos()), encoding: "utf8" });
    expect(r.stderr).toBe("");
    expect(JSON.parse(r.stdout)).toEqual(esperado);
  });

  it.skipIf(!php)("el verificador de PHP de la guía, ejecutado de verdad, da lo esperado en los seis casos", () => {
    const programa = `${VERIFICAR_PHP}
$casos = json_decode(stream_get_contents(STDIN), true);
echo json_encode(array_map(fn($c) => verificarKhipu($c["cuerpo"], $c["firma"], $c["secreto"], $c["tol"]), $casos));
`;
    const r = spawnSync(php!, ["-r", programa.replace(/^<\?php\s*/, "")], { input: JSON.stringify(casos()), encoding: "utf8" });
    expect(r.stderr).toBe("");
    expect(JSON.parse(r.stdout)).toEqual(esperado);
  });

  it("los tres usan el mismo algoritmo y comparan en tiempo constante", () => {
    expect(VERIFICAR_NODE).toContain("crypto.timingSafeEqual");
    expect(VERIFICAR_PYTHON).toContain("hmac.compare_digest");
    expect(VERIFICAR_PHP).toContain("hash_equals");
  });
});
