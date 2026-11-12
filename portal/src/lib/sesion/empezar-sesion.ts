import { reiniciarMarcas } from "./inactividad";
import { empezarSesionNueva } from "./sesion-terminada";

/**
 * Una sesión nueva (el login o el registro salieron bien) empieza de cero, y esa regla vive acá, en un solo lugar: no hereda la inactividad de la anterior (#637, sin esto la marca de
 * ayer la cerraría en la primera evaluación) ni su «Tu sesión terminó» (#654, la navegación del cliente no recarga la página y el estado viejo abriría el diálogo sobre la sesión nueva).
 */
export function empezarSesion(): void {
  reiniciarMarcas();
  empezarSesionNueva();
}
