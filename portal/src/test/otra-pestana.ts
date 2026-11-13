import { vi } from "vitest";
import { CANAL_SESION } from "@/lib/sesion/canal-de-sesion";

type Oyente = (evento: { data: unknown }) => void;

/**
 * Un `BroadcastChannel` mínimo con la regla que importa: un mensaje llega a todos los objetos del mismo nombre MENOS al que lo publica. Así una prueba distingue «otra pestaña»
 * (otro objeto) de «esta pestaña» (el canal único de `canal-de-sesion.ts`), y una pestaña que abriera un segundo canal para publicar se recibiría a sí misma, como en el navegador.
 */
class CanalFalso {
  static abiertos: CanalFalso[] = [];
  private readonly oyentes = new Set<Oyente>();

  constructor(readonly nombre: string) {
    CanalFalso.abiertos.push(this);
  }

  addEventListener(_tipo: string, oyente: Oyente) {
    this.oyentes.add(oyente);
  }

  removeEventListener(_tipo: string, oyente: Oyente) {
    this.oyentes.delete(oyente);
  }

  postMessage(dato: unknown) {
    for (const otro of CanalFalso.abiertos) {
      if (otro !== this && otro.nombre === this.nombre) otro.oyentes.forEach((oyente) => oyente({ data: dato }));
    }
  }

  close() {
    CanalFalso.abiertos = CanalFalso.abiertos.filter((canal) => canal !== this);
  }
}

/**
 * Reemplaza `BroadcastChannel` por el falso y devuelve «otra pestaña» del mismo navegador: lo que publica llega a esta pestaña y lo que esta pestaña publica queda en `recibidos`.
 * Va antes de que cualquier código use el canal (se crea la primera vez que se usa); `vitest.setup.ts` lo descarta al terminar cada prueba.
 */
export function simularOtraPestana() {
  CanalFalso.abiertos = [];
  vi.stubGlobal("BroadcastChannel", CanalFalso);
  const otra = new CanalFalso(CANAL_SESION);
  const recibidos: unknown[] = [];
  otra.addEventListener("message", (evento) => recibidos.push(evento.data));
  return { publicar: (dato: unknown) => otra.postMessage(dato), recibidos };
}
