import type { EventEmitter } from "node:events";

type Worker = { id: number; kill: (senal?: string) => void };
type Cluster = EventEmitter & { fork: () => Worker };

export function procesosConfigurados(valor: string | undefined, avisar: (mensaje: string) => void): number;
export function esperaAntesDeReiniciar(vivioMs: number, caidasSeguidas: number): number;
export function orquestar(opciones: {
  cluster: Cluster;
  procesos: number;
  ahora: () => number;
  avisar: (mensaje: string) => void;
  salir: (codigo: number) => void;
  proceso: EventEmitter;
}): void;
