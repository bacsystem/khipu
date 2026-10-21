import type { Mensajes } from "@/i18n/mensajes";
import { seccionesAdmin } from "@/lib/admin-secciones";

/** Acción principal que la cabecera ofrece en una página: el alta asistida de una cuenta (#188), el alta de un plan (#190) y exportar el consumo (#193). */
export type AccionAdmin = "nuevaCuenta" | "nuevoPlan" | "exportarConsumo";

export type MigaAdmin = { seccion: string; pagina: string; accion?: AccionAdmin };

const ACCIONES: Record<string, AccionAdmin> = {
  "/admin/cuentas": "nuevaCuenta",
  "/admin/planes": "nuevoPlan",
  "/admin/consumo": "exportarConsumo",
};

/**
 * Ubicación de cada página del backoffice y su acción principal. `exacta` evita que «/admin» engulla las páginas que todavía
 * no tienen miga; el resto casa por segmento, así que el detalle de una cuenta (`/admin/cuentas/<id>`) sigue bajo
 * «Clientes / Cuentas» pero `/admin/cuentas-viejas` no. La `accion` es de la página de la lista, no de lo que cuelga de ella:
 * solo se devuelve cuando la ruta coincide exacta. Así la cabecera no necesita conocer rutas. La sección de cada página sale de
 * `seccionesAdmin(messages)`, la misma que agrupa el menú lateral.
 */
function migas(messages: Mensajes): Array<{ ruta: string; exacta?: boolean } & MigaAdmin> {
  const t = messages.admin;
  const secciones = seccionesAdmin(messages);
  // La ficha de un comprobante (#251) no está en el menú: se llega desde la cola de errores, la integridad y la ficha de la empresa, y cuelga de «Operación».
  const seccionOperacion = secciones.find((s) => s.items.some((i) => i.href === "/admin/errores"))?.titulo ?? t.topbar.operacion;
  return [
    { ruta: "/admin", exacta: true, seccion: t.topbar.backoffice, pagina: t.nav.inicio },
    ...secciones.flatMap((s) =>
      s.items.map((i) => ({ ruta: i.href, seccion: s.titulo, pagina: i.etiqueta, ...(ACCIONES[i.href] ? { accion: ACCIONES[i.href] } : {}) })),
    ),
    { ruta: "/admin/comprobantes", seccion: seccionOperacion, pagina: t.comprobanteDetalle.titulo },
  ];
}

export function migaAdmin(pathname: string, messages: Mensajes): MigaAdmin | null {
  const miga = migas(messages).find((m) => (m.exacta ? pathname === m.ruta : pathname === m.ruta || pathname.startsWith(`${m.ruta}/`)));
  if (!miga) return null;
  return { seccion: miga.seccion, pagina: miga.pagina, ...(miga.accion && pathname === miga.ruta ? { accion: miga.accion } : {}) };
}
