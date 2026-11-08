// @vitest-environment node
import { EventEmitter } from "node:events";
import { createRequire } from "node:module";
import { describe, expect, it, vi } from "vitest";

// procesos.cjs vive en la raíz del portal porque es el punto de entrada de la imagen (CMD), fuera del build de Next.
const require = createRequire(import.meta.url);
const { procesosConfigurados, esperaAntesDeReiniciar, orquestar } = require("../../procesos.cjs") as typeof import("../../procesos.cjs");

describe("procesosConfigurados", () => {
  it("sin PORTAL_PROCESOS es 1: el portal arranca como siempre", () => {
    expect(procesosConfigurados(undefined, vi.fn())).toBe(1);
    expect(procesosConfigurados("", vi.fn())).toBe(1);
    expect(procesosConfigurados("  ", vi.fn())).toBe(1);
  });

  it("acepta un entero de 1 a 32, con espacios alrededor", () => {
    expect(procesosConfigurados("1", vi.fn())).toBe(1);
    expect(procesosConfigurados(" 4 ", vi.fn())).toBe(4);
    expect(procesosConfigurados("32", vi.fn())).toBe(32);
  });

  it("un valor inválido no tumba el arranque: avisa nombrando la variable y usa 1", () => {
    for (const malo of ["0", "-2", "33", "2.5", "tres", "4x", "1e1"]) {
      const avisar = vi.fn();
      expect(procesosConfigurados(malo, avisar)).toBe(1);
      expect(avisar).toHaveBeenCalledWith(expect.stringContaining("PORTAL_PROCESOS"));
    }
  });
});

describe("esperaAntesDeReiniciar", () => {
  it("un proceso que vivió al menos 10 s se reemplaza enseguida", () => {
    expect(esperaAntesDeReiniciar(10_000, 0)).toBe(0);
    expect(esperaAntesDeReiniciar(3_600_000, 5)).toBe(0);
  });

  it("uno que se cae enseguida espera más cada vez, hasta 30 s, para no entrar en un bucle de reinicios", () => {
    expect(esperaAntesDeReiniciar(500, 0)).toBe(1_000);
    expect(esperaAntesDeReiniciar(500, 1)).toBe(2_000);
    expect(esperaAntesDeReiniciar(500, 3)).toBe(8_000);
    expect(esperaAntesDeReiniciar(500, 10)).toBe(30_000);
  });
});

/** Un `cluster` de mentira: fork() crea un worker que se puede «caer» a mano. */
function clusterFalso() {
  const cluster = new EventEmitter() as EventEmitter & { fork: () => EventEmitter & { id: number; kill: (s?: string) => void } };
  let id = 0;
  const vivos = new Map<number, EventEmitter & { id: number; kill: (s?: string) => void }>();
  cluster.fork = () => {
    const w = Object.assign(new EventEmitter(), { id: ++id, kill: vi.fn(() => { vivos.delete(w.id); cluster.emit("exit", w, 0, "SIGTERM"); }) });
    vivos.set(w.id, w);
    return w;
  };
  const caer = (wid: number, codigo = 1) => { const w = vivos.get(wid)!; vivos.delete(wid); cluster.emit("exit", w, codigo, null); };
  return { cluster, vivos, caer };
}

describe("orquestar", () => {
  it("levanta N procesos y, si uno se cae, levanta otro para volver a N", () => {
    vi.useFakeTimers();
    const { cluster, vivos, caer } = clusterFalso();
    let ahora = 0;
    orquestar({ cluster, procesos: 3, ahora: () => ahora, avisar: vi.fn(), salir: vi.fn(), proceso: new EventEmitter() });
    expect(vivos.size).toBe(3);

    ahora = 60_000;
    caer(2);
    vi.runAllTimers();

    expect(vivos.size).toBe(3);
    vi.useRealTimers();
  });

  it("un proceso que se cae enseguida se reemplaza recién tras la espera", () => {
    vi.useFakeTimers();
    const { cluster, vivos, caer } = clusterFalso();
    orquestar({ cluster, procesos: 2, ahora: () => 0, avisar: vi.fn(), salir: vi.fn(), proceso: new EventEmitter() });

    caer(1);
    expect(vivos.size).toBe(1);
    vi.advanceTimersByTime(999);
    expect(vivos.size).toBe(1);
    vi.advanceTimersByTime(1);
    expect(vivos.size).toBe(2);
    vi.useRealTimers();
  });

  it("SIGTERM detiene todos los procesos, no los reemplaza y sale cuando terminaron", () => {
    vi.useFakeTimers();
    const { cluster, vivos } = clusterFalso();
    const proceso = new EventEmitter(), salir = vi.fn(), avisar = vi.fn();
    orquestar({ cluster, procesos: 3, ahora: () => 0, avisar, salir, proceso });

    proceso.emit("SIGTERM");

    // Sin avanzar el reloj: sale en cuanto terminó el último, no por el plazo de seguridad de 10 s (un `docker stop` lento).
    expect(vivos.size).toBe(0);
    expect(salir).toHaveBeenCalledOnce();
    expect(salir).toHaveBeenCalledWith(0);
    vi.runAllTimers();
    expect(vivos.size).toBe(0);
    expect(avisar).not.toHaveBeenCalled();
    vi.useRealTimers();
  });

  it("si un proceso no termina con SIGTERM, el principal sale igual al cumplirse el plazo de 10 s y lo avisa", () => {
    vi.useFakeTimers();
    const { cluster } = clusterFalso();
    const terco = { id: 99, kill: vi.fn() };
    const fork = cluster.fork;
    let primero = true;
    cluster.fork = () => (primero ? ((primero = false), terco as unknown as ReturnType<typeof fork>) : fork());
    const proceso = new EventEmitter(), salir = vi.fn(), avisar = vi.fn();
    orquestar({ cluster, procesos: 2, ahora: () => 0, avisar, salir, proceso });

    proceso.emit("SIGTERM");
    expect(salir).not.toHaveBeenCalled();
    vi.advanceTimersByTime(10_000);

    expect(terco.kill).toHaveBeenCalledWith("SIGTERM");
    expect(salir).toHaveBeenCalledWith(0);
    expect(avisar).toHaveBeenCalledWith(expect.stringContaining("no todos los procesos terminaron"));
    vi.useRealTimers();
  });
});
