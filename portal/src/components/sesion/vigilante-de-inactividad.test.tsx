import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { AVISO_ANTES_MS, CLAVE_ULTIMA_ACTIVIDAD, CLAVE_ULTIMO_KEEP_ALIVE, MINUTO_MS, reiniciarMarcas, VENTANA_KEEP_ALIVE_MS } from "@/lib/sesion/inactividad";
import { VigilanteDeInactividad } from "./vigilante-de-inactividad";

const N = 30;
const SEGUNDO = 1_000;
const T0 = new Date("2026-11-12T10:00:00Z").getTime();

let irA: Mock<(url: string) => void>;
let llamadas: string[];
let estadoKeepAlive: number;

function montar(minutos: number | undefined = N) {
  return render(<VigilanteDeInactividad minutos={minutos} irA={irA} />);
}

const avanzar = (ms: number) => act(async () => void (await vi.advanceTimersByTimeAsync(ms)));
const aviso = () => screen.queryByTestId("aviso-de-inactividad");
const seguirConectado = () => screen.getByTestId("inactividad-seguir");
const keepAlives = () => llamadas.filter((u) => u === "/api/auth/sesion").length;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
  vi.setSystemTime(T0);
  window.localStorage.clear();
  irA = vi.fn();
  llamadas = [];
  estadoKeepAlive = 204;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      llamadas.push(url);
      return new Response(null, { status: url === "/api/auth/sesion" ? estadoKeepAlive : 200 });
    }),
  );
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

/** #637: la inactividad de la persona, medida en el navegador, avisa 2 min antes y cierra a los N. */
describe("VigilanteDeInactividad", () => {
  it("sin interacción, el aviso aparece a N − 2 min y el logout ocurre a N", async () => {
    montar();

    await avanzar((N - 2) * MINUTO_MS - SEGUNDO);
    expect(aviso()).toBeNull();

    await avanzar(SEGUNDO);
    expect(aviso()).not.toBeNull();
    expect(irA).not.toHaveBeenCalled();

    await avanzar(AVISO_ANTES_MS - SEGUNDO);
    expect(irA).not.toHaveBeenCalled();

    await avanzar(SEGUNDO);
    expect(llamadas).toContain("/api/auth/logout");
    expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
    // La sesión siguiente empieza de cero.
    expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBeNull();
    expect(window.localStorage.getItem(CLAVE_ULTIMO_KEEP_ALIVE)).toBeNull();
  });

  it("una tecla o un clic reinicia el contador", async () => {
    montar();
    await avanzar(25 * MINUTO_MS);

    fireEvent.keyDown(window, { key: "a" });
    await avanzar(25 * MINUTO_MS);
    expect(aviso()).toBeNull();

    fireEvent.pointerDown(window);
    await avanzar(25 * MINUTO_MS);
    expect(aviso()).toBeNull();
    expect(irA).not.toHaveBeenCalled();
  });

  it.each([
    ["el scroll", () => fireEvent.scroll(window)],
    ["la rueda", () => fireEvent.wheel(window)],
    ["el toque", () => fireEvent.touchStart(window)],
    ["el movimiento del puntero", () => fireEvent.pointerMove(window)],
  ])("%s también cuenta como actividad", async (_titulo, evento) => {
    montar();
    await avanzar(20 * MINUTO_MS);

    evento();
    await avanzar(20 * MINUTO_MS); // sin el evento ya habría cerrado: 40 min seguidos

    expect(aviso()).toBeNull();
    expect(irA).not.toHaveBeenCalled();
  });

  it("el movimiento del puntero se anota a lo sumo una vez cada pocos segundos", async () => {
    montar();
    await avanzar(10 * MINUTO_MS);
    fireEvent.pointerMove(window);
    const anotada = window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD);

    await avanzar(2 * SEGUNDO);
    fireEvent.pointerMove(window);

    expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBe(anotada);
  });

  it("un tic del sondeo (peticiones de fondo sin ningún evento del DOM) no lo reinicia", async () => {
    montar();
    const sondeo = setInterval(() => void fetch("/api/proxy/facturas"), 10 * SEGUNDO);

    await avanzar((N - 2) * MINUTO_MS);
    expect(aviso()).not.toBeNull();

    await avanzar(AVISO_ANTES_MS);
    expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
    clearInterval(sondeo);
  });

  describe("el aviso", () => {
    async function conAviso() {
      montar();
      await avanzar((N - 2) * MINUTO_MS);
    }

    it("tiene una cuenta regresiva en segundos, que baja cada segundo", async () => {
      await conAviso();
      expect(screen.getByText("Por seguridad, cerraremos tu sesión en 120 s por inactividad.")).toBeInTheDocument();

      await avanzar(5 * SEGUNDO);

      expect(screen.getByText("Por seguridad, cerraremos tu sesión en 115 s por inactividad.")).toBeInTheDocument();
    });

    it("sale en inglés con el locale en inglés", async () => {
      fijarLocaleDePrueba("en");
      await conAviso();

      expect(screen.getByText("Your session is about to end")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Stay signed in" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Sign out" })).toBeInTheDocument();
    });

    it("el foco empieza en «Seguir conectado»", async () => {
      await conAviso();

      await vi.waitFor(() => expect(seguirConectado()).toHaveFocus());
    });

    it("la cuenta regresiva no está en una región viva: no se anuncia en cada segundo", async () => {
      await conAviso();

      expect(aviso()!.querySelector("[aria-live]")).toBeNull();
      expect(screen.queryByRole("timer")).toBeNull();
      expect(screen.queryByRole("status")).toBeNull();
    });

    it("«Seguir conectado» cierra el aviso, reinicia el contador y renueva la sesión en el acto", async () => {
      await conAviso();
      const antes = keepAlives();

      await act(async () => void fireEvent.click(seguirConectado()));

      expect(aviso()).toBeNull();
      expect(keepAlives()).toBe(antes + 1);
      await avanzar(AVISO_ANTES_MS + MINUTO_MS);
      expect(aviso()).toBeNull();
      expect(irA).not.toHaveBeenCalled();
    });

    it("Escape cuenta como seguir conectado", async () => {
      await conAviso();

      await act(async () => void fireEvent.keyDown(document.activeElement ?? document.body, { key: "Escape" }));

      expect(aviso()).toBeNull();
      expect(irA).not.toHaveBeenCalled();
    });

    /** Un backend colgado no puede dejar la pestaña en la página privada con los datos a la vista: pasado el tope, navega aunque el logout no haya respondido. */
    it("un logout que nunca responde no retiene la página: al vencer el tope va al login por inactividad", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
          llamadas.push(url);
          return new Promise<Response>(() => undefined);
        }),
      );
      await conAviso();

      await avanzar(AVISO_ANTES_MS);
      expect(llamadas).toContain("/api/auth/logout");
      expect(irA).not.toHaveBeenCalled();

      await avanzar(2_999);
      expect(irA).not.toHaveBeenCalled();

      await avanzar(1);
      expect(irA).toHaveBeenCalledTimes(1);
      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
    });

    it("lo mismo con «Cerrar sesión»: un logout colgado no impide salir", async () => {
      vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>(() => undefined)));
      await conAviso();

      await act(async () => void fireEvent.click(screen.getByTestId("inactividad-salir")));
      expect(irA).not.toHaveBeenCalled();
      await avanzar(3_000);

      expect(irA).toHaveBeenCalledTimes(1);
      expect(irA).toHaveBeenCalledWith("/login");
    });

    it("si el logout responde después del tope, no navega una segunda vez", async () => {
      let responder!: (r: Response) => void;
      vi.stubGlobal(
        "fetch",
        vi.fn(() => new Promise<Response>((resolve) => (responder = resolve))),
      );
      await conAviso();
      await avanzar(AVISO_ANTES_MS + 3_000);
      expect(irA).toHaveBeenCalledTimes(1);

      await act(async () => responder(new Response(null, { status: 200 })));

      expect(irA).toHaveBeenCalledTimes(1);
    });

    it("si el logout responde antes del tope navega al responder, una sola vez", async () => {
      await conAviso();

      await avanzar(AVISO_ANTES_MS);
      await avanzar(5_000);

      expect(irA).toHaveBeenCalledTimes(1);
    });

    it("el logout se manda con keepalive: termina aunque la página ya se haya ido", async () => {
      await conAviso();

      await avanzar(AVISO_ANTES_MS);

      expect(vi.mocked(fetch)).toHaveBeenCalledWith("/api/auth/logout", expect.objectContaining({ method: "POST", keepalive: true }));
    });

    it("«Cerrar sesión» hace el logout normal y va al login sin motivo", async () => {
      await conAviso();

      await act(async () => void fireEvent.click(screen.getByTestId("inactividad-salir")));

      expect(llamadas).toContain("/api/auth/logout");
      expect(irA).toHaveBeenCalledWith("/login");
      expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBeNull();
      expect(window.localStorage.getItem(CLAVE_ULTIMO_KEEP_ALIVE)).toBeNull();
    });

    it("con el aviso abierto, un clic en la página no lo salva: solo sus botones", async () => {
      await conAviso();

      fireEvent.pointerDown(window);
      fireEvent.keyDown(window, { key: "a" });
      await avanzar(AVISO_ANTES_MS);

      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
    });

    it("el logout que falla no deja a la persona en el panel: va al login igual", async () => {
      vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new TypeError("sin red"))));
      await conAviso();

      await avanzar(AVISO_ANTES_MS);

      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
    });
  });

  describe("entre pestañas", () => {
    it("la actividad de otra pestaña reinicia el contador y cierra el aviso", async () => {
      montar();
      await avanzar((N - 2) * MINUTO_MS);
      expect(aviso()).not.toBeNull();

      window.localStorage.setItem(CLAVE_ULTIMA_ACTIVIDAD, String(Date.now()));
      await act(async () => void window.dispatchEvent(new StorageEvent("storage", { key: CLAVE_ULTIMA_ACTIVIDAD })));

      expect(aviso()).toBeNull();
      await avanzar(AVISO_ANTES_MS + MINUTO_MS);
      expect(irA).not.toHaveBeenCalled();
    });

    it("un cierre por inactividad en otra pestaña la lleva al login sin llamar otra vez al logout", async () => {
      const canales: Array<{ onmessage?: (e: { data: string }) => void; postMessage: () => void }> = [];
      vi.stubGlobal(
        "BroadcastChannel",
        class {
          oyentes: Array<(e: { data: string }) => void> = [];
          constructor() {
            canales.push(this as never);
          }
          addEventListener(_t: string, f: (e: { data: string }) => void) {
            this.oyentes.push(f);
          }
          postMessage() {}
          close() {}
        },
      );
      montar();

      await act(async () => void (canales[0] as unknown as { oyentes: Array<(e: { data: string }) => void> }).oyentes.forEach((f) => f({ data: "inactividad" })));

      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
      expect(llamadas).not.toContain("/api/auth/logout");
    });
  });

  describe("el keep-alive", () => {
    it("con interacción constante no pasa de 1 cada 5 min", async () => {
      montar();
      const instantes: number[] = [];
      vi.mocked(fetch).mockImplementation(async (url) => {
        if (url === "/api/auth/sesion") instantes.push(Date.now());
        return new Response(null, { status: 204 });
      });

      // 40 minutos con un evento cada 15 s.
      for (let i = 0; i < 160; i++) {
        await avanzar(15 * SEGUNDO);
        fireEvent.keyDown(window, { key: "a" });
      }

      expect(instantes.length).toBeGreaterThanOrEqual(7);
      for (let i = 1; i < instantes.length; i++) expect(instantes[i] - instantes[i - 1]).toBeGreaterThanOrEqual(VENTANA_KEEP_ALIVE_MS);
      expect(irA).not.toHaveBeenCalled();
    });

    it("sin interacción no hay keep-alive", async () => {
      montar();

      await avanzar((N - 2) * MINUTO_MS - SEGUNDO);

      expect(keepAlives()).toBe(0);
    });

    it("si responde que la sesión murió (401) va al login sin motivo de inactividad", async () => {
      estadoKeepAlive = 401;
      montar();
      await avanzar(6 * MINUTO_MS);

      fireEvent.keyDown(window, { key: "a" });
      await avanzar(SEGUNDO);

      expect(irA).toHaveBeenCalledWith("/login");
    });

    it("si responde que la cuenta está suspendida (403) va a la página que se lo explica", async () => {
      estadoKeepAlive = 403;
      montar();
      await avanzar(6 * MINUTO_MS);

      fireEvent.keyDown(window, { key: "a" });
      await avanzar(SEGUNDO);

      expect(irA).toHaveBeenCalledWith("/cuenta-suspendida");
    });

    it("si falla la red no navega", async () => {
      estadoKeepAlive = 502;
      montar();
      await avanzar(6 * MINUTO_MS);

      fireEvent.keyDown(window, { key: "a" });
      await avanzar(SEGUNDO);

      expect(irA).not.toHaveBeenCalled();
    });
  });

  describe("tiempo de pared", () => {
    it("al volver de una suspensión de N + 1 min, la pestaña visible cierra en el acto, sin esperar los tics", async () => {
      montar();
      vi.setSystemTime(T0 + (N + 1) * MINUTO_MS);

      await act(async () => void document.dispatchEvent(new Event("visibilitychange")));

      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
    });

    it("también al recuperar el foco o mostrarse de nuevo (pageshow)", async () => {
      montar();
      vi.setSystemTime(T0 + (N + 1) * MINUTO_MS);

      await act(async () => void window.dispatchEvent(new Event("pageshow")));

      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
    });

    it("marca de ayer, login y luego montar: no expulsa", async () => {
      const AYER = T0 - 24 * 60 * MINUTO_MS;
      window.localStorage.setItem(CLAVE_ULTIMA_ACTIVIDAD, String(AYER));
      window.localStorage.setItem(CLAVE_ULTIMO_KEEP_ALIVE, String(AYER));

      reiniciarMarcas(); // lo que hace el login (y el registro) al responder bien, antes de navegar al panel
      montar();
      await avanzar(SEGUNDO);

      expect(irA).not.toHaveBeenCalled();
      expect(llamadas).toEqual([]);
      expect(aviso()).toBeNull();
    });

    it("al montarse con una marca vencida cierra: es tiempo de pared, no se perdona", async () => {
      window.localStorage.setItem(CLAVE_ULTIMA_ACTIVIDAD, String(T0 - (N + 1) * MINUTO_MS));
      window.localStorage.setItem(CLAVE_ULTIMO_KEEP_ALIVE, String(T0 - (N + 1) * MINUTO_MS));

      montar();
      await avanzar(0);

      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
    });
  });

  describe("cuándo no hay contador", () => {
    it.each([[0], [undefined], [-5], [1.5], [Number.NaN]])("con N = %s no hay contador, ni aviso, ni keep-alive", async (minutos) => {
      render(<VigilanteDeInactividad minutos={minutos} irA={irA} />);

      await avanzar(10 * 60 * MINUTO_MS);
      fireEvent.keyDown(window, { key: "a" });
      await avanzar(10 * MINUTO_MS);

      expect(aviso()).toBeNull();
      expect(llamadas).toEqual([]);
      expect(irA).not.toHaveBeenCalled();
      expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBeNull();
    });
  });

  it("al desmontarse suelta los temporizadores y los eventos", async () => {
    const { unmount } = montar();
    unmount();

    await avanzar(N * MINUTO_MS);
    fireEvent.keyDown(window, { key: "a" });

    expect(irA).not.toHaveBeenCalled();
    expect(llamadas).toEqual([]);
  });
});
