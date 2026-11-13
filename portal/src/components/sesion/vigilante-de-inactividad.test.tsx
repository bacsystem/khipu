import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { avisarALasDemasPestanas } from "@/lib/sesion/canal-de-sesion";
import { avisarSesionTerminada } from "@/lib/sesion/sesion-terminada";
import { DialogoSesionTerminada } from "./dialogo-sesion-terminada";
import { AVISO_ANTES_MS, CLAVE_ULTIMA_ACTIVIDAD, CLAVE_ULTIMO_KEEP_ALIVE, MINUTO_MS, reiniciarMarcas, VENTANA_KEEP_ALIVE_MS } from "@/lib/sesion/inactividad";
import { simularOtraPestana } from "@/test/otra-pestana";
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
/** Como en `(privado)` y en `/onboarding` (#654): el diálogo «Tu sesión terminó» es hermano del vigilante. */
function montarConDialogo() {
  return render(
    <>
      <VigilanteDeInactividad minutos={N} irA={irA} />
      <DialogoSesionTerminada />
    </>,
  );
}
/** Monta el vigilante y lo deja con el aviso de inactividad abierto (a N − 2 min). */
async function conAviso() {
  montar();
  await avanzar((N - 2) * MINUTO_MS);
}
const aviso = () => screen.queryByTestId("aviso-de-inactividad");
const seguirConectado = () => screen.getByTestId("inactividad-seguir");
const franja = () => aviso()!.querySelector('[data-slot="franja-cuenta-regresiva"]')!;
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
    /** #656: la cuenta regresiva va en su propia franja, en mm:ss, y no escondida dentro de la frase. */
    it("el título y el texto no llevan el número; la franja cuenta en mm:ss y baja cada segundo", async () => {
      await conAviso();
      expect(screen.getByRole("heading", { name: "¿Sigues ahí? Tu sesión está por finalizar" })).toBeInTheDocument();
      expect(screen.getByText("Confirma que sigues usando el portal para mantener tu sesión activa.")).toBeInTheDocument();
      expect(franja()).toHaveTextContent("Tu sesión se cerrará en:");
      expect(franja()).toHaveTextContent("02:00");

      await avanzar(5 * SEGUNDO);

      expect(franja()).toHaveTextContent("01:55");
    });

    it("con 65 s faltando la franja dice 01:05 y con 4 s dice 00:04", async () => {
      montar();

      await avanzar(N * MINUTO_MS - 65 * SEGUNDO);
      expect(franja()).toHaveTextContent("01:05");

      await avanzar(61 * SEGUNDO);
      expect(franja()).toHaveTextContent("00:04");
    });

    it("sale en inglés con el locale en inglés", async () => {
      fijarLocaleDePrueba("en");
      await conAviso();

      expect(screen.getByRole("heading", { name: "Are you still there? Your session is about to end" })).toBeInTheDocument();
      expect(screen.getByText("Confirm you are still using the portal to keep your session active.")).toBeInTheDocument();
      expect(franja()).toHaveTextContent("Your session will end in:");
      expect(screen.getByRole("button", { name: "Keep using the portal" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Sign out" })).toBeInTheDocument();
    });

    it("«Seguir usando el portal» es el botón principal y «Cerrar sesión» el secundario, en ese orden del DOM para que el principal quede arriba en pantalla angosta", async () => {
      await conAviso();

      const botones = within(aviso()!).getAllByRole("button");
      expect(botones.map((b) => b.textContent)).toEqual(["Cerrar sesión", "Seguir usando el portal"]);
    });

    it("el foco empieza en «Seguir usando el portal»", async () => {
      await conAviso();

      await vi.waitFor(() => expect(seguirConectado()).toHaveFocus());
    });

    it("la cuenta regresiva no está en una región viva: no se anuncia en cada segundo", async () => {
      await conAviso();

      expect(aviso()!.querySelector("[aria-live]")).toBeNull();
      expect(screen.queryByRole("timer")).toBeNull();
      expect(screen.queryByRole("status")).toBeNull();
    });

    it("«Seguir usando el portal» cierra el aviso, reinicia el contador y renueva la sesión en el acto", async () => {
      await conAviso();
      const antes = keepAlives();

      await act(async () => void fireEvent.click(seguirConectado()));

      expect(aviso()).toBeNull();
      expect(keepAlives()).toBe(antes + 1);
      await avanzar(AVISO_ANTES_MS + MINUTO_MS);
      expect(aviso()).toBeNull();
      expect(irA).not.toHaveBeenCalled();
    });

    it("un clic afuera no lo cierra", async () => {
      await conAviso();
      const fondo = document.querySelector('[data-slot="dialog-overlay"]')!;

      await act(async () => {
        fireEvent.pointerDown(fondo);
        fireEvent.mouseDown(fondo);
        fireEvent.click(fondo);
      });

      expect(aviso()).not.toBeNull();
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
      const otra = simularOtraPestana();
      montar();

      await act(async () => otra.publicar("inactividad"));

      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
      expect(llamadas).not.toContain("/api/auth/logout");
    });

    /** #653: el cierre voluntario de otra pestaña (el menú, los términos, la cuenta suspendida o el «Cerrar sesión» del aviso) lleva al login con su motivo. */
    it("un cierre voluntario en otra pestaña la lleva a /login?motivo=otra-pestana sin llamar otra vez al logout", async () => {
      const otra = simularOtraPestana();
      montar();

      await act(async () => otra.publicar("salir"));

      expect(irA).toHaveBeenCalledExactlyOnceWith("/login?motivo=otra-pestana");
      expect(llamadas).not.toContain("/api/auth/logout");
    });

    /** El corte por inactividad puede estar apagado (`SESION_INACTIVIDAD_MINUTOS=0`) o no aplicar (sesión de soporte): cerrar la sesión en una pestaña igual saca a las demás. */
    it.each([[0], [undefined]])("con minutos=%s (sin contador) también escucha el cierre voluntario de otra pestaña", async (minutos) => {
      const otra = simularOtraPestana();
      render(<VigilanteDeInactividad minutos={minutos} irA={irA} />);
      expect(aviso()).toBeNull();

      await act(async () => otra.publicar("salir"));

      expect(irA).toHaveBeenCalledExactlyOnceWith("/login?motivo=otra-pestana");
    });

    it("y sin contador también escucha el cierre por inactividad de otra pestaña", async () => {
      const otra = simularOtraPestana();
      render(<VigilanteDeInactividad minutos={0} irA={irA} />);

      await act(async () => otra.publicar("inactividad"));

      expect(irA).toHaveBeenCalledExactlyOnceWith("/login?motivo=inactividad");
    });

    it("con contador, el aviso de otra pestaña navega una sola vez (no hay dos oyentes)", async () => {
      const otra = simularOtraPestana();
      montar();

      await act(async () => otra.publicar("salir"));

      expect(irA).toHaveBeenCalledTimes(1);
    });

    it("al desmontarse deja de escuchar", async () => {
      const otra = simularOtraPestana();
      const { unmount } = render(<VigilanteDeInactividad minutos={0} irA={irA} />);

      unmount();
      await act(async () => otra.publicar("salir"));

      expect(irA).not.toHaveBeenCalled();
    });

    it("un aviso que no conoce se ignora", async () => {
      const otra = simularOtraPestana();
      montar();

      await act(async () => otra.publicar("salir-para-siempre"));

      expect(irA).not.toHaveBeenCalled();
    });

    it("el «Cerrar sesión» del aviso avisa a las demás pestañas y la propia va al login sin motivo, una sola vez", async () => {
      const otra = simularOtraPestana();
      await conAviso();

      await act(async () => void fireEvent.click(screen.getByTestId("inactividad-salir")));
      await avanzar(3_000);

      expect(otra.recibidos).toEqual(["salir"]);
      expect(irA).toHaveBeenCalledExactlyOnceWith("/login");
    });

    it("el cierre por inactividad avisa a las demás pestañas con su propio aviso", async () => {
      const otra = simularOtraPestana();
      await conAviso();

      await avanzar(AVISO_ANTES_MS);

      expect(otra.recibidos).toEqual(["inactividad"]);
    });

    /** La pestaña que cierra la sesión desde el menú no se navega a sí misma: publica por el mismo canal por el que escucha el vigilante. */
    it("la pestaña que publica un cierre voluntario no se recibe a sí misma", async () => {
      simularOtraPestana();
      montar();

      await act(async () => avisarALasDemasPestanas("salir"));

      expect(irA).not.toHaveBeenCalled();
    });

    it("sin BroadcastChannel el vigilante funciona igual", async () => {
      vi.stubGlobal("BroadcastChannel", undefined);
      await conAviso();

      await avanzar(AVISO_ANTES_MS);

      expect(irA).toHaveBeenCalledWith("/login?motivo=inactividad");
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

    /** #654: la sesión muerta se explica con «Tu sesión terminó» (con `next`), no con un salto al login sin explicación. */
    it("si responde que la sesión murió (401) abre «Tu sesión terminó» en vez de navegar", async () => {
      estadoKeepAlive = 401;
      montarConDialogo();
      await avanzar(6 * MINUTO_MS);

      fireEvent.keyDown(window, { key: "a" });
      await avanzar(SEGUNDO);

      expect(screen.getByTestId("dialogo-sesion-terminada")).toBeInTheDocument();
      expect(irA).not.toHaveBeenCalled();
    });

    it("con el diálogo abierto el vigilante se desmonta: ni el aviso de inactividad ni el cierre por inactividad se ven encima", async () => {
      estadoKeepAlive = 401;
      montarConDialogo();
      await avanzar((N - 2) * MINUTO_MS);
      expect(aviso()).not.toBeNull();

      await act(async () => void avisarSesionTerminada());

      expect(aviso()).toBeNull();
      await avanzar(AVISO_ANTES_MS + MINUTO_MS);
      expect(irA).not.toHaveBeenCalled();
      expect(llamadas).not.toContain("/api/auth/logout");
    });

    it("un keep-alive que estaba en vuelo cuando se abrió el diálogo no navega al volver", async () => {
      let responder!: (r: Response) => void;
      vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
          llamadas.push(url);
          return new Promise<Response>((resolve) => (responder = resolve));
        }),
      );
      montarConDialogo();
      await avanzar(6 * MINUTO_MS);
      fireEvent.keyDown(window, { key: "a" }); // sale el keep-alive y queda en vuelo
      expect(keepAlives()).toBe(1);

      await act(async () => void avisarSesionTerminada()); // otra acción encontró la sesión muerta antes
      await act(async () => responder(new Response(null, { status: 403 })));

      expect(irA).not.toHaveBeenCalled();
      expect(screen.getByTestId("dialogo-sesion-terminada")).toBeInTheDocument();
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
