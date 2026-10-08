import { SECCIONES_ADMIN } from "@/lib/admin-secciones";
import { messages } from "@/lib/messages";

/** Acción principal que la cabecera ofrece en una página: el alta asistida de una cuenta (#188), el alta de un plan (#190) y exportar el consumo (#193). */
export type AccionAdmin = "nuevaCuenta" | "nuevoPlan" | "exportarConsumo";

export type MigaAdmin = { seccion: string; pagina: string; accion?: AccionAdmin };

const t = messages.admin;

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
 * `SECCIONES_ADMIN`, la misma que agrupa el menú lateral.
 */
const MIGAS: Array<{ ruta: string; exacta?: boolean } & MigaAdmin> = [
  { ruta: "/admin", exacta: true, seccion: t.topbar.backoffice, pagina: t.nav.inicio },
  ...SECCIONES_ADMIN.flatMap((s) =>
    s.items.map((i) => ({ ruta: i.href, seccion: s.titulo, pagina: i.etiqueta, ...(ACCIONES[i.href] ? { accion: ACCIONES[i.href] } : {}) })),
  ),
];

export function migaAdmin(pathname: string): MigaAdmin | null {
  const miga = MIGAS.find((m) => (m.exacta ? pathname === m.ruta : pathname === m.ruta || pathname.startsWith(`${m.ruta}/`)));
  if (!miga) return null;
  return { seccion: miga.seccion, pagina: miga.pagina, ...(miga.accion && pathname === miga.ruta ? { accion: miga.accion } : {}) };
}
