import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  almacenDelNavegador,
  AVISO_ANTES_MS,
  CLAVE_ULTIMA_ACTIVIDAD,
  CLAVE_ULTIMO_KEEP_ALIVE,
  crearVigilante,
  decidir,
  MINUTO_MS,
  minutosDeInactividad,
  REINTENTO_TRAS_FALLO_MS,
  reiniciarMarcas,
  VENTANA_KEEP_ALIVE_MS,
  type Almacen,
  type ResultadoKeepAlive,
} from "./inactividad";

const N = 30;
const LIMITE = N * MINUTO_MS;

function almacenEnMemoria(): Almacen {
  const datos = new Map<string, number>();
  return {
    leer: (clave) => datos.get(clave) ?? null,
    escribir: (clave, valor) => void datos.set(clave, valor),
    borrar: (clave) => void datos.delete(clave),
  };
}

/** Una «pestaña»: su vigilante, con el reloj y el almacén que comparte con las demás, y su propio contador de keep-alives. */
function pestana(compartido: { almacen: Almacen; reloj: { t: number } }, resultado: ResultadoKeepAlive = "vivo") {
  const enviarKeepAlive = vi.fn(async () => resultado);
  const alTerminarLaSesion = vi.fn();
  const vigilante = crearVigilante({ minutos: N, almacen: compartido.almacen, ahora: () => compartido.reloj.t, enviarKeepAlive, alTerminarLaSesion });
  return { vigilante, enviarKeepAlive, alTerminarLaSesion };
}

const mundo = () => ({ almacen: almacenEnMemoria(), reloj: { t: 1_000_000_000_000 } });
const avanzar = (m: ReturnType<typeof mundo>, ms: number) => void (m.reloj.t += ms);

describe("minutosDeInactividad", () => {
  it("es el N que informa /me", () => {
    expect(minutosDeInactividad({ sesion_inactividad_minutos: 30 })).toBe(30);
  });

  it("sin el campo o con 0 no hay N que pasar (el 0 lo descarta el vigilante)", () => {
    expect(minutosDeInactividad({})).toBeUndefined();
    expect(minutosDeInactividad({ sesion_inactividad_minutos: 0 })).toBe(0);
  });

  it("una sesión de soporte no lleva contador aunque el campo venga", () => {
    expect(minutosDeInactividad({ soporte_hasta: "2026-11-12T10:30:00Z", sesion_inactividad_minutos: 30 })).toBeUndefined();
  });
});

describe("decidir", () => {
  const A = 5_000_000;

  it.each([
    ["recién hubo actividad", 0, "activa"],
    ["a N − 2 min en punto", LIMITE - AVISO_ANTES_MS, "aviso"],
    ["un milisegundo antes de N − 2", LIMITE - AVISO_ANTES_MS - 1, "activa"],
    ["a N − 1 ms", LIMITE - 1, "aviso"],
    ["a N en punto", LIMITE, "cerrada"],
    ["mucho después de N", LIMITE * 3, "cerrada"],
  ] as const)("%s → %s", (_titulo, inactivo, estado) => {
    expect(decidir(A + inactivo, N, { ultimaActividad: A, ultimoKeepAlive: A }).estado).toBe(estado);
  });

  it("dice cuánto falta para cerrar", () => {
    expect(decidir(A + 10 * MINUTO_MS, N, { ultimaActividad: A, ultimoKeepAlive: A }).restanteMs).toBe(20 * MINUTO_MS);
    expect(decidir(A + LIMITE + 5, N, { ultimaActividad: A, ultimoKeepAlive: A }).restanteMs).toBe(0);
  });

  it("un reloj que retrocede no cuenta como inactividad negativa", () => {
    expect(decidir(A - 10, N, { ultimaActividad: A, ultimoKeepAlive: A }).estado).toBe("activa");
  });

  it("no hay keep-alive sin interacción desde el último", () => {
    expect(decidir(A + 10 * MINUTO_MS, N, { ultimaActividad: A, ultimoKeepAlive: A + 1 }).keepAliveDebido).toBe(false);
  });

  it("con interacción pero dentro de la ventana de 5 min, tampoco", () => {
    expect(decidir(A + VENTANA_KEEP_ALIVE_MS - 1, N, { ultimaActividad: A + 1, ultimoKeepAlive: A }).keepAliveDebido).toBe(false);
  });

  it("con interacción y pasada la ventana, sí", () => {
    expect(decidir(A + VENTANA_KEEP_ALIVE_MS, N, { ultimaActividad: A + 1, ultimoKeepAlive: A }).keepAliveDebido).toBe(true);
  });

  it("una vez cerrada no se toca el servidor", () => {
    expect(decidir(A + LIMITE, N, { ultimaActividad: A, ultimoKeepAlive: null }).keepAliveDebido).toBe(false);
  });
});

describe("el vigilante", () => {
  it("la primera interacción manda el keep-alive de inicio de ventana", async () => {
    const m = mundo();
    const p = pestana(m);
    p.vigilante.iniciar();
    avanzar(m, 6 * MINUTO_MS);

    p.vigilante.registrarActividad();

    expect(p.enviarKeepAlive).toHaveBeenCalledTimes(1);
  });

  it("al montarse sin marcas no manda nada: el servidor acaba de ver la sesión", () => {
    const m = mundo();
    const p = pestana(m);

    p.vigilante.iniciar();

    expect(p.enviarKeepAlive).not.toHaveBeenCalled();
    expect(m.almacen.leer(CLAVE_ULTIMA_ACTIVIDAD)).toBe(m.reloj.t);
  });

  it("sin interacción no hay keep-alive, pase el tiempo que pase hasta el aviso", () => {
    const m = mundo();
    const p = pestana(m);
    p.vigilante.iniciar();

    for (let i = 0; i < N - 2; i++) {
      avanzar(m, MINUTO_MS);
      p.vigilante.evaluar();
    }

    expect(p.enviarKeepAlive).not.toHaveBeenCalled();
  });

  /** El caso del architect: una interacción a 1 min de un keep-alive no sale hasta cerrar la ventana, y ahí sale solo, sin otra interacción. */
  it("el throttle dispara también al cierre de la ventana, y nunca dos dentro de 5 min", () => {
    const m = mundo();
    const p = pestana(m);
    p.vigilante.iniciar();
    avanzar(m, 6 * MINUTO_MS);
    p.vigilante.registrarActividad(); // inicio de ventana: keep-alive #1
    const primero = m.reloj.t;
    avanzar(m, MINUTO_MS);
    p.vigilante.registrarActividad(); // dentro de la ventana
    avanzar(m, 3 * MINUTO_MS);
    p.vigilante.evaluar(); // a 4 min del primero
    expect(p.enviarKeepAlive).toHaveBeenCalledTimes(1);

    avanzar(m, primero + VENTANA_KEEP_ALIVE_MS - m.reloj.t - 1);
    p.vigilante.evaluar(); // a 1 ms de cumplirse la ventana
    expect(p.enviarKeepAlive).toHaveBeenCalledTimes(1);

    avanzar(m, 1);
    p.vigilante.evaluar(); // cierra la ventana: sale el segundo, sin que nadie haya tocado nada
    expect(p.enviarKeepAlive).toHaveBeenCalledTimes(2);
    expect(m.almacen.leer(CLAVE_ULTIMO_KEEP_ALIVE)).toBe(m.reloj.t);

    avanzar(m, VENTANA_KEEP_ALIVE_MS);
    p.vigilante.evaluar(); // sin interacción nueva, no hay un tercero
    expect(p.enviarKeepAlive).toHaveBeenCalledTimes(2);
  });

  it("«Seguir conectado» reinicia el contador y manda el keep-alive en el acto, con la ventana abierta", async () => {
    const m = mundo();
    const p = pestana(m);
    p.vigilante.iniciar();
    avanzar(m, 6 * MINUTO_MS);
    p.vigilante.registrarActividad(); // keep-alive #1: abre la ventana
    avanzar(m, MINUTO_MS);
    p.vigilante.registrarActividad(); // dentro de la ventana: no sale otro
    expect(p.enviarKeepAlive).toHaveBeenCalledTimes(1);

    await p.vigilante.seguirConectado();

    expect(p.enviarKeepAlive).toHaveBeenCalledTimes(2);
    expect(p.vigilante.evaluar()).toMatchObject({ estado: "activa", restanteMs: LIMITE });
  });

  it("desde el aviso, «Seguir conectado» vuelve a empezar la cuenta", async () => {
    const m = mundo();
    const p = pestana(m);
    p.vigilante.iniciar();
    avanzar(m, LIMITE - AVISO_ANTES_MS);
    expect(p.vigilante.evaluar().estado).toBe("aviso");

    await p.vigilante.seguirConectado();

    expect(p.vigilante.evaluar()).toMatchObject({ estado: "activa", restanteMs: LIMITE });
  });

  it("el tiempo es de pared: tras una suspensión de N + 1 min la primera evaluación cierra, sin tics", () => {
    const m = mundo();
    const p = pestana(m);
    p.vigilante.iniciar();

    avanzar(m, (N + 1) * MINUTO_MS);

    expect(p.vigilante.evaluar().estado).toBe("cerrada");
    expect(p.enviarKeepAlive).not.toHaveBeenCalled();
  });

  it("una marca vencida al montar no se perdona", () => {
    const m = mundo();
    m.almacen.escribir(CLAVE_ULTIMA_ACTIVIDAD, m.reloj.t - (N + 1) * MINUTO_MS);

    expect(pestana(m).vigilante.iniciar().estado).toBe("cerrada");
  });

  it("pero una sesión nueva (marcas reiniciadas al abrirla) no hereda la inactividad de ayer", () => {
    const m = mundo();
    m.almacen.escribir(CLAVE_ULTIMA_ACTIVIDAD, m.reloj.t - 24 * 60 * MINUTO_MS);
    m.almacen.escribir(CLAVE_ULTIMO_KEEP_ALIVE, m.reloj.t - 24 * 60 * MINUTO_MS);

    reiniciarMarcas(m.almacen);

    expect(pestana(m).vigilante.iniciar().estado).toBe("activa");
  });

  describe("entre pestañas", () => {
    it("la actividad en una reinicia el contador de la otra", () => {
      const m = mundo();
      const a = pestana(m);
      const b = pestana(m);
      a.vigilante.iniciar();
      b.vigilante.iniciar();
      avanzar(m, LIMITE - AVISO_ANTES_MS + MINUTO_MS);
      expect(b.vigilante.evaluar().estado).toBe("aviso");

      a.vigilante.registrarActividad();

      expect(b.vigilante.evaluar()).toMatchObject({ estado: "activa", restanteMs: LIMITE });
    });

    it("con actividad intercalada, el servidor ve a lo sumo un keep-alive cada 5 min en total", () => {
      const m = mundo();
      const a = pestana(m);
      const b = pestana(m);
      a.vigilante.iniciar();
      b.vigilante.iniciar();
      const instantes: number[] = [];
      const medir = (p: ReturnType<typeof pestana>) => p.enviarKeepAlive.mockImplementation(async () => (instantes.push(m.reloj.t), "vivo"));
      medir(a);
      medir(b);

      // 40 minutos, un evento cada 20 s, alternando de pestaña, y las dos evaluando en cada tic.
      for (let i = 0; i < 120; i++) {
        avanzar(m, 20_000);
        (i % 2 === 0 ? a : b).vigilante.registrarActividad();
        (i % 2 === 0 ? b : a).vigilante.evaluar();
      }

      expect(instantes.length).toBeGreaterThanOrEqual(7);
      for (let i = 1; i < instantes.length; i++) expect(instantes[i] - instantes[i - 1]).toBeGreaterThanOrEqual(VENTANA_KEEP_ALIVE_MS);
    });
  });

  describe("cuando el servidor contesta distinto", () => {
    it("la sesión muerta avisa al navegador, sin motivo de inactividad", async () => {
      const m = mundo();
      const p = pestana(m, "muerta");
      p.vigilante.iniciar();
      avanzar(m, 6 * MINUTO_MS);

      p.vigilante.registrarActividad();
      await vi.waitFor(() => expect(p.alTerminarLaSesion).toHaveBeenCalledWith("muerta"));
    });

    it("la cuenta suspendida también", async () => {
      const m = mundo();
      const p = pestana(m, "suspendida");
      p.vigilante.iniciar();
      avanzar(m, 6 * MINUTO_MS);

      p.vigilante.registrarActividad();
      await vi.waitFor(() => expect(p.alTerminarLaSesion).toHaveBeenCalledWith("suspendida"));
    });

    it("un fallo de red no navega y reintenta a los 30 s, no en cada tic", async () => {
      const m = mundo();
      const p = pestana(m, "fallo");
      p.vigilante.iniciar();
      avanzar(m, 6 * MINUTO_MS);
      p.vigilante.registrarActividad();
      await vi.waitFor(() => expect(m.almacen.leer(CLAVE_ULTIMO_KEEP_ALIVE)).not.toBe(m.reloj.t));

      expect(p.alTerminarLaSesion).not.toHaveBeenCalled();
      avanzar(m, REINTENTO_TRAS_FALLO_MS - 1);
      p.vigilante.evaluar();
      expect(p.enviarKeepAlive).toHaveBeenCalledTimes(1);

      avanzar(m, 1);
      p.vigilante.evaluar();
      expect(p.enviarKeepAlive).toHaveBeenCalledTimes(2);
    });
  });
});

describe("almacenDelNavegador", () => {
  beforeEach(() => window.localStorage.clear());

  it("guarda y lee en localStorage, compartido con cualquier otro almacén sobre el mismo", () => {
    const a = almacenDelNavegador();
    const b = almacenDelNavegador();

    a.escribir("k", 42);

    expect(b.leer("k")).toBe(42);
    expect(window.localStorage.getItem("k")).toBe("42");
    a.borrar("k");
    expect(b.leer("k")).toBeNull();
  });

  it("un valor que no es un número se lee como ausente", () => {
    window.localStorage.setItem("k", "no-es-un-número");

    expect(almacenDelNavegador().leer("k")).toBeNull();
  });

  it("si localStorage lanza, sigue funcionando con su memoria", () => {
    const original = Object.getOwnPropertyDescriptor(window, "localStorage")!;
    Object.defineProperty(window, "localStorage", { configurable: true, get: () => { throw new DOMException("bloqueado", "SecurityError"); } });
    try {
      const almacen = almacenDelNavegador();
      almacen.escribir("k", 7);
      expect(almacen.leer("k")).toBe(7);
      almacen.borrar("k");
      expect(almacen.leer("k")).toBeNull();
    } finally {
      Object.defineProperty(window, "localStorage", original);
    }
  });
});
