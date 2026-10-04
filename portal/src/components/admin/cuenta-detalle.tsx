import { InboxIcon } from "lucide-react";
import type { ReactNode } from "react";
import { CertificadoEtiqueta, Etiqueta } from "@/components/admin/etiquetas";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { estadoCertificado, type CuentaDetalleAdmin, type EmpresaCuenta } from "@/lib/api/admin-cuenta-detalle";
import { CABECERA_TABLA } from "@/lib/estilos";
import { formatearFecha, formatearFechaHora, formatearMonto } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.detalle;

function Seccion({ titulo, id, children }: { titulo: string; id: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="grid min-w-0 gap-2">
      <h2 id={id} className="font-heading text-base">
        {titulo}
      </h2>
      <div className="overflow-x-auto rounded-xl border border-border/90 bg-card shadow-2xs">{children}</div>
    </section>
  );
}

function Vacio({ columnas, texto }: { columnas: number; texto: string }) {
  return (
    <TableRow className="hover:bg-transparent">
      <TableCell colSpan={columnas} className="py-10 text-center">
        <div className="flex flex-col items-center gap-2 text-muted-foreground">
          <InboxIcon className="size-5" />
          <p className="text-sm">{texto}</p>
        </div>
      </TableCell>
    </TableRow>
  );
}

const CABECERA = "border-b border-border/80 bg-muted hover:bg-muted";

function Usuarios({ usuarios }: { usuarios: CuentaDetalleAdmin["usuarios"] }) {
  const u = t.usuarios;
  return (
    <Seccion titulo={u.titulo} id="detalle-usuarios">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{u.columnas.correo}</TableHead>
            <TableHead className={CABECERA_TABLA}>{u.columnas.rol}</TableHead>
            <TableHead className={CABECERA_TABLA}>{u.columnas.estado}</TableHead>
            <TableHead className={CABECERA_TABLA}>{u.columnas.verificado}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{u.columnas.ultimoAcceso}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {usuarios.map((x) => (
            <TableRow key={x.id} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px]">{x.email}</TableCell>
              <TableCell className="px-3 py-2 text-[12px]">{x.rol}</TableCell>
              <TableCell className="px-3 py-2">
                <Etiqueta tono={x.activo ? "ok" : "neutro"}>{x.activo ? u.activo : u.inactivo}</Etiqueta>
              </TableCell>
              <TableCell className="px-3 py-2">
                {x.correo_verificado_en ? (
                  <span title={formatearFechaHora(x.correo_verificado_en)}>
                    <Etiqueta tono="ok">{u.verificado}</Etiqueta>
                  </span>
                ) : (
                  <Etiqueta tono="aviso">{u.sinVerificar}</Etiqueta>
                )}
              </TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-[12px] text-foreground/80">
                {x.ultimo_acceso ? formatearFechaHora(x.ultimo_acceso) : <span className="text-muted-foreground">{u.nunca}</span>}
              </TableCell>
            </TableRow>
          ))}
          {usuarios.length === 0 ? <Vacio columnas={5} texto={u.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function Empresas({ empresas, hoy }: { empresas: EmpresaCuenta[]; hoy: string }) {
  const e = t.empresas;
  return (
    <Seccion titulo={e.titulo} id="detalle-empresas">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{e.columnas.empresa}</TableHead>
            <TableHead className={CABECERA_TABLA}>{e.columnas.entorno}</TableHead>
            <TableHead className={CABECERA_TABLA}>{e.columnas.certificado}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{e.columnas.sol}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {empresas.map((x) => (
            <TableRow key={x.id} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4">
                <div className="flex flex-col">
                  <span className="font-medium text-foreground">{x.razon_social}</span>
                  <span className="font-mono text-[11px] text-muted-foreground">{x.ruc}</span>
                </div>
              </TableCell>
              <TableCell className="px-3 py-2">
                <Etiqueta tono={x.entorno === "PRODUCCION" ? "ok" : "neutro"}>{x.entorno === "PRODUCCION" ? e.produccion : e.beta}</Etiqueta>
              </TableCell>
              <TableCell className="px-3 py-2">
                <CertificadoEtiqueta estado={estadoCertificado(x, hoy)} />
              </TableCell>
              <TableCell className="py-2 pr-4 pl-3">
                <Etiqueta tono={x.tiene_credenciales_sol ? "ok" : "neutro"}>{x.tiene_credenciales_sol ? e.solCargadas : e.solSinCargar}</Etiqueta>
              </TableCell>
            </TableRow>
          ))}
          {empresas.length === 0 ? <Vacio columnas={4} texto={e.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function Comprobantes({ comprobantes }: { comprobantes: CuentaDetalleAdmin["comprobantes"] }) {
  const c = t.comprobantes;
  return (
    <Seccion titulo={c.titulo} id="detalle-comprobantes">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{c.columnas.comprobante}</TableHead>
            <TableHead className={CABECERA_TABLA}>{c.columnas.empresa}</TableHead>
            <TableHead className={CABECERA_TABLA}>{c.columnas.fecha}</TableHead>
            <TableHead className={CABECERA_TABLA}>{c.columnas.estado}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4 text-right`}>{c.columnas.total}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {comprobantes.map((x) => (
            <TableRow key={x.id} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px]">
                {x.serie}-{String(x.numero).padStart(8, "0")}
              </TableCell>
              <TableCell className="px-3 py-2 font-mono text-[12px] text-foreground/80">{x.ruc}</TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{formatearFecha(x.fecha_emision)}</TableCell>
              <TableCell className="px-3 py-2">
                <EstadoBadge estado={x.estado} />
              </TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-right font-mono tabular-nums">{formatearMonto(x.moneda, x.total)}</TableCell>
            </TableRow>
          ))}
          {comprobantes.length === 0 ? <Vacio columnas={5} texto={c.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function Eventos({ eventos }: { eventos: CuentaDetalleAdmin["eventos"] }) {
  const e = t.eventos;
  const acciones = e.acciones as Record<string, string>;
  return (
    <Seccion titulo={e.titulo} id="detalle-eventos">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{e.columnas.accion}</TableHead>
            <TableHead className={CABECERA_TABLA}>{e.columnas.actor}</TableHead>
            <TableHead className={CABECERA_TABLA}>{e.columnas.cuando}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{e.columnas.detalle}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {eventos.map((x, i) => (
            <TableRow key={`${x.ocurrido_en}-${i}`} className="border-b border-border/60">
              {/* Una acción que este portal todavía no conoce se muestra con su código, no se esconde. */}
              <TableCell className="py-2 pr-3 pl-4">{acciones[x.accion] ?? x.accion}</TableCell>
              <TableCell className="px-3 py-2 text-[12px]">{x.actor === "ADMINISTRADOR" ? e.actorAdministrador : e.actorPlataforma}</TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{formatearFechaHora(x.ocurrido_en)}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 font-mono text-[11px] text-muted-foreground">{x.detalle ?? "—"}</TableCell>
            </TableRow>
          ))}
          {eventos.length === 0 ? <Vacio columnas={4} texto={e.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

/**
 * Detalle de una cuenta del backoffice (#181). Solo lectura: no hay botones de acción (suspender, impersonar, planes…); llegan en
 * sus issues, con su propia confirmación y su registro en la bitácora. `hoy` (fecha de Lima) llega de afuera para que el estado del
 * certificado se calcule igual en el servidor y en las pruebas.
 */
export function CuentaDetalle({ cuenta, hoy }: { cuenta: CuentaDetalleAdmin; hoy: string }) {
  return (
    <div className="grid min-w-0 gap-6" data-testid="cuenta-detalle">
      <dl className="flex flex-wrap gap-x-8 gap-y-2 text-sm">
        <div>
          <dt className="text-[11px] tracking-wide text-muted-foreground uppercase">{t.correo}</dt>
          <dd className="font-mono text-[13px]">{cuenta.email}</dd>
        </div>
        <div>
          <dt className="text-[11px] tracking-wide text-muted-foreground uppercase">{t.telefono}</dt>
          <dd className="font-mono text-[13px]">{cuenta.telefono ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-[11px] tracking-wide text-muted-foreground uppercase">{t.alta}</dt>
          <dd className="text-[13px]">{formatearFechaHora(cuenta.creada_en)}</dd>
        </div>
      </dl>
      <p className="text-xs text-muted-foreground">{t.soloLectura}</p>
      <Usuarios usuarios={cuenta.usuarios} />
      <Empresas empresas={cuenta.empresas} hoy={hoy} />
      <Comprobantes comprobantes={cuenta.comprobantes} />
      <Eventos eventos={cuenta.eventos} />
    </div>
  );
}
