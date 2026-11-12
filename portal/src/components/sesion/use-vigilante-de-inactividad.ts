"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { RUTA_CUENTA_SUSPENDIDA } from "@/lib/api/cuenta-suspendida";
import {
  almacenDelNavegador,
  CLAVE_ULTIMA_ACTIVIDAD,
  CLAVE_ULTIMO_KEEP_ALIVE,
  crearVigilante,
  reiniciarMarcas,
  type Decision,
  type Vigilante,
} from "@/lib/sesion/inactividad";
import { pedirKeepAlive } from "@/lib/sesion/keep-alive";

/** Lo que cuenta como actividad de la persona. Las peticiones de fondo (sondeos, `router.refresh()`, reintentos del proxy) no tocan el DOM, así que nunca llegan acá. */
const EVENTOS_DE_ACTIVIDAD = ["keydown", "pointerdown", "touchstart", "wheel", "scroll"] as const;
const REGISTRO_MIN_MS = 1_000;
/** El movimiento del puntero cuenta, pero se anota a lo sumo una vez cada pocos segundos. */
const REGISTRO_PUNTERO_MIN_MS = 5_000;
const TIC_NORMAL_MS = 10_000;
/** Con el aviso abierto la cuenta regresiva se evalúa cada segundo. */
const TIC_CON_AVISO_MS = 1_000;

/** Cuánto se espera al logout antes de navegar al login de todos modos. */
const TOPE_DEL_LOGOUT_MS = 3_000;

const CANAL = "khipu-sesion";
const RUTA_LOGIN = "/login";
export const RUTA_LOGIN_POR_INACTIVIDAD = "/login?motivo=inactividad";

type Aviso = "inactividad" | "salir";

export type OpcionesDelVigilante = {
  minutos: number;
  /** Navegar de verdad (una carga completa, no `router.push`: no debe quedar estado de React con datos de la sesión). Inyectable para las pruebas. */
  irA: (url: string) => void;
  ahora: () => number;
};

/**
 * Cuenta la inactividad de la persona en el navegador (#637): avisa a N − 2 min, cierra la sesión a los N y, mientras haya interacción, toca el servidor a lo sumo cada 5 min para que
 * el corte de #636 no le gane. Devuelve los segundos que faltan cuando el aviso está abierto (`null` si no), y lo que hacen sus dos botones.
 */
export function useVigilanteDeInactividad({ minutos, irA, ahora }: OpcionesDelVigilante) {
  const [segundos, setSegundos] = useState<number | null>(null);
  const controles = useRef<{ seguir: () => void; salir: () => void }>({ seguir: () => undefined, salir: () => undefined });

  useEffect(() => {
    const almacen = almacenDelNavegador();
    const canal = typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel(CANAL);
    let avisando = false;
    let cerrando = false;
    let temporizador: ReturnType<typeof setTimeout> | undefined;

    function navegar(url: string) {
      cerrando = true;
      clearTimeout(temporizador);
      irA(url);
    }

    /** El logout normal (revoca la familia) y, pase lo que pase con él, al login: la sesión del navegador ya no sirve. */
    function cerrarSesion(url: string, aviso: Aviso) {
      if (cerrando) return;
      cerrando = true;
      clearTimeout(temporizador);
      reiniciarMarcas(almacen); // la sesión siguiente empieza de cero (también la ve cualquier otra pestaña)
      canal?.postMessage(aviso);
      // El logout no puede retener la página: si el backend se cuelga, la pestaña seguiría mostrando los datos de una sesión que ya se dio por cerrada. Navega cuando responde o,
      // a más tardar, al vencer el tope; `keepalive` deja que la petición termine aunque la página ya se haya ido.
      let navegado = false;
      const irAlLogin = () => {
        if (navegado) return;
        navegado = true;
        clearTimeout(tope);
        irA(url);
      };
      const tope = setTimeout(irAlLogin, TOPE_DEL_LOGOUT_MS);
      void fetch("/api/auth/logout", { method: "POST", keepalive: true })
        .catch(() => undefined)
        .finally(irAlLogin);
    }

    function aplicar(decision: Decision) {
      if (cerrando) return;
      if (decision.estado === "cerrada") {
        cerrarSesion(RUTA_LOGIN_POR_INACTIVIDAD, "inactividad");
        return;
      }
      const ahoraAvisa = decision.estado === "aviso";
      setSegundos(ahoraAvisa ? Math.ceil(decision.restanteMs / 1_000) : null);
      if (ahoraAvisa !== avisando) {
        avisando = ahoraAvisa;
        programar();
      }
    }

    function programar() {
      clearTimeout(temporizador);
      temporizador = setTimeout(() => {
        aplicar(vigilante.evaluar());
        if (!cerrando) programar();
      }, avisando ? TIC_CON_AVISO_MS : TIC_NORMAL_MS);
    }

    const vigilante: Vigilante = crearVigilante({
      minutos,
      almacen,
      ahora,
      enviarKeepAlive: pedirKeepAlive,
      alTerminarLaSesion: (motivo) => navegar(motivo === "suspendida" ? RUTA_CUENTA_SUSPENDIDA : RUTA_LOGIN),
    });

    let ultimoRegistro = 0;
    function alActividad(evento: Event) {
      // Con el aviso abierto solo cuentan sus botones: un puntero que pasa por encima del diálogo no debe salvarlo. La actividad de OTRA pestaña sí, y le llega por las marcas.
      if (avisando || cerrando) return;
      const minimo = evento.type === "pointermove" ? REGISTRO_PUNTERO_MIN_MS : REGISTRO_MIN_MS;
      const t = ahora();
      if (t - ultimoRegistro < minimo) return;
      ultimoRegistro = t;
      aplicar(vigilante.registrarActividad());
    }
    const evaluarYa = () => aplicar(vigilante.evaluar());
    const alVolverVisible = () => {
      if (document.visibilityState === "visible") evaluarYa();
    };
    const alCambiarElAlmacen = (evento: StorageEvent) => {
      if (evento.key === null || evento.key === CLAVE_ULTIMA_ACTIVIDAD || evento.key === CLAVE_ULTIMO_KEEP_ALIVE) evaluarYa();
    };
    const alRecibirAviso = (evento: MessageEvent<Aviso>) => {
      if (evento.data === "inactividad") navegar(RUTA_LOGIN_POR_INACTIVIDAD);
      else if (evento.data === "salir") navegar(RUTA_LOGIN);
    };

    for (const tipo of EVENTOS_DE_ACTIVIDAD) window.addEventListener(tipo, alActividad, { capture: true, passive: true });
    window.addEventListener("pointermove", alActividad, { passive: true });
    window.addEventListener("focus", evaluarYa);
    window.addEventListener("pageshow", evaluarYa);
    window.addEventListener("storage", alCambiarElAlmacen);
    document.addEventListener("visibilitychange", alVolverVisible);
    canal?.addEventListener("message", alRecibirAviso);

    controles.current = {
      seguir: () => {
        // Las marcas se escriben en el acto (síncrono), así que la evaluación que sigue ya ve la sesión viva y el aviso se cierra sin esperar al servidor.
        void vigilante.seguirConectado();
        aplicar(vigilante.evaluar());
      },
      salir: () => cerrarSesion(RUTA_LOGIN, "salir"),
    };

    aplicar(vigilante.iniciar());
    programar();

    return () => {
      clearTimeout(temporizador);
      for (const tipo of EVENTOS_DE_ACTIVIDAD) window.removeEventListener(tipo, alActividad, { capture: true });
      window.removeEventListener("pointermove", alActividad);
      window.removeEventListener("focus", evaluarYa);
      window.removeEventListener("pageshow", evaluarYa);
      window.removeEventListener("storage", alCambiarElAlmacen);
      document.removeEventListener("visibilitychange", alVolverVisible);
      canal?.close();
    };
  }, [minutos, irA, ahora]);

  const seguirConectado = useCallback(() => controles.current.seguir(), []);
  const cerrarSesion = useCallback(() => controles.current.salir(), []);
  return { segundos, seguirConectado, cerrarSesion };
}
