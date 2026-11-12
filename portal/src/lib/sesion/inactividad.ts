/**
 * La inactividad de la persona en el navegador (#637): cuándo avisar, cuándo cerrar y cuándo tocar el servidor para que no cierre antes (#636).
 *
 * Todo se calcula con tiempo de pared a partir de dos marcas en `localStorage`, compartidas por todas las pestañas del portal: `ultimaActividad` (la última interacción de la
 * persona en cualquiera de ellas) y `ultimoKeepAlive` (el último toque al servidor). No se cuentan tics: una pestaña en segundo plano a la que el navegador le espacia los
 * temporizadores, o un equipo que vuelve de la suspensión, decide bien en cuanto evalúa. Este módulo no toca el DOM; el componente le pone los eventos y los temporizadores.
 */

export const MINUTO_MS = 60_000;
/** Cuánto antes del corte aparece el aviso con la cuenta regresiva. */
export const AVISO_ANTES_MS = 2 * MINUTO_MS;
/**
 * El techo del keep-alive: a lo sumo uno cada 5 minutos entre todas las pestañas. Se dispara al inicio de la ventana (la primera interacción) y al cierre (la primera evaluación
 * pasados los 5 minutos si hubo interacción dentro de ella): sin el segundo, el servidor podría cortar antes que el aviso.
 */
export const VENTANA_KEEP_ALIVE_MS = 5 * MINUTO_MS;
/** Tras un keep-alive que falló por red o por un 5xx, se reintenta a los 30 s en vez de esperar la ventana entera. */
export const REINTENTO_TRAS_FALLO_MS = 30_000;

export const CLAVE_ULTIMA_ACTIVIDAD = "khipu.sesion.ultimaActividad";
export const CLAVE_ULTIMO_KEEP_ALIVE = "khipu.sesion.ultimoKeepAlive";

/** Dónde viven las marcas. Una abstracción para que las pruebas simulen varias pestañas con un mismo almacén. */
export type Almacen = {
  leer(clave: string): number | null;
  escribir(clave: string, valor: number): void;
  borrar(clave: string): void;
};

/** `localStorage`, y una memoria local si el navegador lo bloquea (modo privado, datos de sitio bloqueados): ahí el corte sigue funcionando, pero por pestaña. */
export function almacenDelNavegador(): Almacen {
  const respaldo = new Map<string, number>();
  const almacen = (): Storage | null => {
    try {
      return window.localStorage;
    } catch {
      return null;
    }
  };
  return {
    leer(clave) {
      try {
        const crudo = almacen()?.getItem(clave);
        const valor = crudo == null ? NaN : Number(crudo);
        if (Number.isFinite(valor)) return valor;
      } catch {
        // sin almacenamiento: se usa el respaldo
      }
      return respaldo.get(clave) ?? null;
    },
    escribir(clave, valor) {
      respaldo.set(clave, valor);
      try {
        almacen()?.setItem(clave, String(valor));
      } catch {
        // el respaldo ya la tiene
      }
    },
    borrar(clave) {
      respaldo.delete(clave);
      try {
        almacen()?.removeItem(clave);
      } catch {
        // nada que borrar
      }
    },
  };
}

/** Una sesión nueva (login, registro) empieza sin marcas: si no, la actividad de la sesión anterior, de ayer, cerraría la nueva en la primera evaluación. */
export function reiniciarMarcas(almacen: Almacen = almacenDelNavegador()): void {
  almacen.borrar(CLAVE_ULTIMA_ACTIVIDAD);
  almacen.borrar(CLAVE_ULTIMO_KEEP_ALIVE);
}

export type Marcas = { ultimaActividad: number | null; ultimoKeepAlive: number | null };

export type Decision = {
  /** `aviso` desde N − 2 min; `cerrada` al cumplirse N. */
  estado: "activa" | "aviso" | "cerrada";
  /** Cuánto falta para cerrar. */
  restanteMs: number;
  /** Hubo interacción desde el último keep-alive y ya pasó la ventana: toca uno. */
  keepAliveDebido: boolean;
};

/** La decisión para un instante, sin efectos. `minutos` es el N del servidor. */
export function decidir(ahora: number, minutos: number, { ultimaActividad, ultimoKeepAlive }: Marcas): Decision {
  const limite = minutos * MINUTO_MS;
  const inactivo = ultimaActividad === null ? 0 : Math.max(0, ahora - ultimaActividad);
  const estado = inactivo >= limite ? "cerrada" : inactivo >= limite - AVISO_ANTES_MS ? "aviso" : "activa";
  const hubo = ultimaActividad !== null && (ultimoKeepAlive === null || ultimaActividad > ultimoKeepAlive);
  const venceLaVentana = ultimoKeepAlive === null || ahora - ultimoKeepAlive >= VENTANA_KEEP_ALIVE_MS;
  return { estado, restanteMs: Math.max(0, limite - inactivo), keepAliveDebido: estado !== "cerrada" && hubo && venceLaVentana };
}

/** Qué respondió el servidor al keep-alive: `muerta` = la sesión ya no existe (401); `suspendida` = la cuenta lo está; `fallo` = red o 5xx, la sesión sigue. */
export type ResultadoKeepAlive = "vivo" | "muerta" | "suspendida" | "fallo";

export type Dependencias = {
  minutos: number;
  almacen: Almacen;
  ahora: () => number;
  enviarKeepAlive: () => Promise<ResultadoKeepAlive>;
  /** El keep-alive encontró la sesión muerta o la cuenta suspendida: el navegador va al login o a la página de la suspensión. */
  alTerminarLaSesion: (motivo: "muerta" | "suspendida") => void;
};

export type Vigilante = {
  /** Al montarse: sin marca, este instante es el primer movimiento. Una marca ya vencida NO se perdona: es tiempo de pared. */
  iniciar(): Decision;
  /** Una interacción de la persona: la anota y, si toca, manda el keep-alive de inicio de ventana. */
  registrarActividad(): Decision;
  /** Mira las marcas (las de todas las pestañas) y, si toca, manda el keep-alive de cierre de ventana. */
  evaluar(): Decision;
  /** «Seguir conectado»: reinicia el contador y toca el servidor en el acto, aunque la ventana del throttle siga abierta. */
  seguirConectado(): Promise<void>;
};

export function crearVigilante({ minutos, almacen, ahora, enviarKeepAlive, alTerminarLaSesion }: Dependencias): Vigilante {
  const marcas = (): Marcas => ({ ultimaActividad: almacen.leer(CLAVE_ULTIMA_ACTIVIDAD), ultimoKeepAlive: almacen.leer(CLAVE_ULTIMO_KEEP_ALIVE) });

  async function tocarElServidor(): Promise<void> {
    const instante = ahora();
    // Se anota antes de llamar: así una segunda evaluación, aquí o en otra pestaña, ya ve la ventana ocupada.
    almacen.escribir(CLAVE_ULTIMO_KEEP_ALIVE, instante);
    const resultado = await enviarKeepAlive();
    if (resultado === "fallo") {
      // Salvo que otra pestaña ya haya anotado uno más nuevo.
      if (almacen.leer(CLAVE_ULTIMO_KEEP_ALIVE) === instante) almacen.escribir(CLAVE_ULTIMO_KEEP_ALIVE, instante - VENTANA_KEEP_ALIVE_MS + REINTENTO_TRAS_FALLO_MS);
      return;
    }
    if (resultado !== "vivo") alTerminarLaSesion(resultado);
  }

  function evaluar(): Decision {
    const decision = decidir(ahora(), minutos, marcas());
    if (decision.keepAliveDebido) void tocarElServidor();
    return decision;
  }

  return {
    iniciar() {
      if (almacen.leer(CLAVE_ULTIMA_ACTIVIDAD) === null) {
        // Una sesión recién abierta (o sin marcas): el servidor acaba de verla, así que no hace falta un keep-alive que nadie pidió.
        almacen.escribir(CLAVE_ULTIMA_ACTIVIDAD, ahora());
        if (almacen.leer(CLAVE_ULTIMO_KEEP_ALIVE) === null) almacen.escribir(CLAVE_ULTIMO_KEEP_ALIVE, ahora());
      }
      return evaluar();
    },
    registrarActividad() {
      almacen.escribir(CLAVE_ULTIMA_ACTIVIDAD, ahora());
      return evaluar();
    },
    evaluar,
    async seguirConectado() {
      almacen.escribir(CLAVE_ULTIMA_ACTIVIDAD, ahora());
      await tocarElServidor();
    },
  };
}
