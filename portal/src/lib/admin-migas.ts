import { messages } from "@/lib/messages";

export type MigaAdmin = { seccion: string; pagina: string };

const t = messages.admin;

/**
 * Ubicación de cada página del backoffice. `exacta` evita que «/admin» engulla las páginas que todavía no tienen miga; el
 * resto casa por segmento, así que el detalle de una cuenta (`/admin/cuentas/<id>`) sigue bajo «Clientes / Cuentas» pero
 * `/admin/cuentas-viejas` no.
 */
const MIGAS: Array<{ ruta: string; exacta?: boolean } & MigaAdmin> = [
  { ruta: "/admin", exacta: true, seccion: t.topbar.backoffice, pagina: t.nav.inicio },
  { ruta: "/admin/cuentas", seccion: t.topbar.clientes, pagina: t.nav.cuentas },
];

export function migaAdmin(pathname: string): MigaAdmin | null {
  const miga = MIGAS.find((m) => (m.exacta ? pathname === m.ruta : pathname === m.ruta || pathname.startsWith(`${m.ruta}/`)));
  return miga ? { seccion: miga.seccion, pagina: miga.pagina } : null;
}
