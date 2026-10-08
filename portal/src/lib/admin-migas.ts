import { messages } from "@/lib/messages";

/** Acción principal que la cabecera ofrece en una página: el alta asistida de una cuenta (#188), el alta de un plan (#190) y exportar el consumo (#193). */
export type AccionAdmin = "nuevaCuenta" | "nuevoPlan" | "exportarConsumo";

export type MigaAdmin = { seccion: string; pagina: string; accion?: AccionAdmin };

const t = messages.admin;

/**
 * Ubicación de cada página del backoffice y su acción principal. `exacta` evita que «/admin» engulla las páginas que todavía
 * no tienen miga; el resto casa por segmento, así que el detalle de una cuenta (`/admin/cuentas/<id>`) sigue bajo
 * «Clientes / Cuentas» pero `/admin/cuentas-viejas` no. La `accion` es de la página de la lista, no de lo que cuelga de ella:
 * solo se devuelve cuando la ruta coincide exacta. Así la cabecera no necesita conocer rutas.
 */
const MIGAS: Array<{ ruta: string; exacta?: boolean } & MigaAdmin> = [
  { ruta: "/admin", exacta: true, seccion: t.topbar.backoffice, pagina: t.nav.inicio },
  { ruta: "/admin/cuentas", seccion: t.topbar.clientes, pagina: t.nav.cuentas, accion: "nuevaCuenta" },
  { ruta: "/admin/empresas", seccion: t.topbar.clientes, pagina: t.nav.empresas },
  { ruta: "/admin/planes", seccion: t.topbar.comercial, pagina: t.nav.planes, accion: "nuevoPlan" },
  { ruta: "/admin/consumo", seccion: t.topbar.comercial, pagina: t.nav.consumo, accion: "exportarConsumo" },
  { ruta: "/admin/monitor", seccion: t.topbar.operacion, pagina: t.nav.monitor },
  { ruta: "/admin/errores", seccion: t.topbar.operacion, pagina: t.nav.errores },
  { ruta: "/admin/avisos", seccion: t.topbar.operacion, pagina: t.nav.avisos },
  { ruta: "/admin/integridad", seccion: t.topbar.operacion, pagina: t.nav.integridad },
  { ruta: "/admin/configuracion", seccion: t.topbar.plataforma, pagina: t.nav.configuracion },
];

export function migaAdmin(pathname: string): MigaAdmin | null {
  const miga = MIGAS.find((m) => (m.exacta ? pathname === m.ruta : pathname === m.ruta || pathname.startsWith(`${m.ruta}/`)));
  if (!miga) return null;
  return { seccion: miga.seccion, pagina: miga.pagina, ...(miga.accion && pathname === miga.ruta ? { accion: miga.accion } : {}) };
}
