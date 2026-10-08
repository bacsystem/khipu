import type { ReactNode } from "react";
import { AccionesDeBaja } from "@/components/admin/acciones-de-baja";
import { AccionesDeCuenta } from "@/components/admin/acciones-de-cuenta";
import { AccionesDeUsuario } from "@/components/admin/acciones-de-usuario";
import { CertificadoEtiqueta, Etiqueta, EstadoCuentaEtiqueta } from "@/components/admin/etiquetas";
import { CABECERA_FILA, Seccion, Vacio } from "@/components/admin/seccion";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { Tabs } from "@/components/navegacion/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { estadoCertificado, type CuentaDetalleAdmin, type EmpresaCuenta, type EventoReciente } from "@/lib/api/admin-cuenta-detalle";
import { detalleLegible, motivoDe } from "@/lib/bitacora";
import { CABECERA_TABLA } from "@/lib/estilos";
import { formatearFecha, formatearFechaHora, formatearMonto } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.detalle;

function Usuarios({ cuentaId, usuarios }: { cuentaId: string; usuarios: CuentaDetalleAdmin["usuarios"] }) {
  const u = t.usuarios;
  return (
    <Seccion titulo={u.titulo} id="detalle-usuarios">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{u.columnas.correo}</TableHead>
            <TableHead className={CABECERA_TABLA}>{u.columnas.rol}</TableHead>
            <TableHead className={CABECERA_TABLA}>{u.columnas.estado}</TableHead>
            <TableHead className={CABECERA_TABLA}>{u.columnas.verificado}</TableHead>
            <TableHead className={CABECERA_TABLA}>{u.columnas.ultimoAcceso}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4 text-right`}>{u.columnas.acciones}</TableHead>
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
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">
                {x.ultimo_acceso ? formatearFechaHora(x.ultimo_acceso) : <span className="text-muted-foreground">{u.nunca}</span>}
              </TableCell>
              <TableCell className="py-2 pr-4 pl-3">
                <AccionesDeUsuario cuentaId={cuentaId} usuarioId={x.id} correo={x.email} activo={x.activo} verificado={Boolean(x.correo_verificado_en)} />
              </TableCell>
            </TableRow>
          ))}
          {usuarios.length === 0 ? <Vacio columnas={6} texto={u.vacio} /> : null}
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
          <TableRow className={CABECERA_FILA}>
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
          <TableRow className={CABECERA_FILA}>
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

const acciones = t.eventos.acciones as Record<string, string>;

/** Quién hizo una acción (H11): el correo del administrador; «Administrador» solo si ya no existe, y «Clave de plataforma» si no fue una persona. */
function quien(x: EventoReciente): string {
  if (x.actor !== "ADMINISTRADOR") return t.eventos.actorPlataforma;
  return x.administrador ?? t.eventos.actorAdministrador;
}

function Eventos({ eventos }: { eventos: CuentaDetalleAdmin["eventos"] }) {
  const e = t.eventos;
  return (
    <Seccion titulo={e.titulo} id="detalle-eventos">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
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
              <TableCell className="px-3 py-2 text-[12px]">{quien(x)}</TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{formatearFechaHora(x.ocurrido_en)}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-[12px] text-muted-foreground">{detalleLegible(x.detalle) || "—"}</TableCell>
            </TableRow>
          ))}
          {eventos.length === 0 ? <Vacio columnas={4} texto={e.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

/** Todas las suspensiones, reactivaciones, bajas y reposiciones (H15): cuándo, quién y por qué, aparte de la bitácora donde se mezclaban con lo demás. */
function HistorialEstado({ historial }: { historial: EventoReciente[] }) {
  const h = t.historial;
  return (
    <Seccion titulo={h.titulo} id="detalle-historial-estado">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{h.columnas.cuando}</TableHead>
            <TableHead className={CABECERA_TABLA}>{h.columnas.cambio}</TableHead>
            <TableHead className={CABECERA_TABLA}>{h.columnas.quien}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{h.columnas.motivo}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]" data-testid="historial-estado">
          {historial.map((x, i) => {
            const motivo = motivoDe(x.detalle);
            return (
              <TableRow key={`${x.ocurrido_en}-${i}`} className="border-b border-border/60">
                <TableCell className="py-2 pr-3 pl-4 text-[12px] whitespace-nowrap text-foreground/80">{formatearFechaHora(x.ocurrido_en)}</TableCell>
                <TableCell className="px-3 py-2">{acciones[x.accion] ?? x.accion}</TableCell>
                <TableCell className="px-3 py-2 text-[12px]">{quien(x)}</TableCell>
                <TableCell className="py-2 pr-4 pl-3 text-[12px]">{motivo ?? <span className="text-muted-foreground">{h.sinMotivo}</span>}</TableCell>
              </TableRow>
            );
          })}
          {historial.length === 0 ? <Vacio columnas={4} texto={h.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

/** El motivo del último cambio de ese tipo, para mostrarlo junto al estado (H14): quien atiende al cliente lo necesita ahí, no al fondo de la bitácora. */
function ultimoMotivo(historial: EventoReciente[], accion: string): string | null {
  return motivoDe(historial.find((x) => x.accion === accion)?.detalle);
}

type Pestana = "plan" | "estado" | "usuarios" | "empresas" | "comprobantes" | "bitacora";

/**
 * Detalle de una cuenta del backoffice (#181): arriba el estado, los datos de contacto y las acciones que cambian el estado; abajo, en pestañas (H19), el plan
 * y los pagos (llegan armados de la página: van aparte del detalle y pueden fallar solos), el historial del estado, los usuarios, las empresas, los
 * comprobantes y la bitácora. Antes era una sola columna de siete tablas. `hoy` (fecha de Lima) llega de afuera para que el estado del certificado se
 * calcule igual en el servidor y en las pruebas.
 */
export function CuentaDetalle({ cuenta, hoy, plan }: { cuenta: CuentaDetalleAdmin; hoy: string; plan?: ReactNode }) {
  const p = t.pestanas;
  const motivoSuspension = cuenta.suspendida_en ? ultimoMotivo(cuenta.historial_estado, "SUSPENDER_CUENTA") : null;
  const motivoBaja = cuenta.baja_en ? ultimoMotivo(cuenta.historial_estado, "DAR_DE_BAJA_CUENTA") : null;
  const pestanas: Array<{ id: Pestana; etiqueta: string; contador?: number }> = [
    ...(plan ? [{ id: "plan" as const, etiqueta: p.plan }] : []),
    { id: "estado", etiqueta: p.estado, contador: cuenta.historial_estado.length },
    { id: "usuarios", etiqueta: p.usuarios, contador: cuenta.usuarios.length },
    { id: "empresas", etiqueta: p.empresas, contador: cuenta.empresas.length },
    { id: "comprobantes", etiqueta: p.comprobantes },
    { id: "bitacora", etiqueta: p.bitacora },
  ];
  return (
    <div className="grid min-w-0 gap-6" data-testid="cuenta-detalle">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <dl className="flex flex-wrap gap-x-8 gap-y-2 text-sm">
          <div>
            <dt className="text-[11px] tracking-wide text-muted-foreground uppercase">{t.estado}</dt>
            <dd className="text-[13px]">
              <EstadoCuentaEtiqueta estado={cuenta.estado} />
              {cuenta.suspendida_en ? (
                <span data-testid="suspendida-desde" className="mt-1 block text-[11px] text-muted-foreground">
                  {t.suspendidaDesde.replace("{fecha}", formatearFechaHora(cuenta.suspendida_en))}
                </span>
              ) : null}
              {motivoSuspension ? (
                <span data-testid="motivo-suspension" className="block max-w-sm text-[11px] text-foreground/80">
                  {t.motivo.replace("{motivo}", motivoSuspension)}
                </span>
              ) : null}
              {cuenta.baja_en ? (
                <span data-testid="baja-desde" className="mt-1 block text-[11px] text-muted-foreground">
                  {t.bajaDesde.replace("{fecha}", formatearFechaHora(cuenta.baja_en))}
                </span>
              ) : null}
              {motivoBaja ? (
                <span data-testid="motivo-baja" className="block max-w-sm text-[11px] text-foreground/80">
                  {t.motivo.replace("{motivo}", motivoBaja)}
                </span>
              ) : null}
            </dd>
          </div>
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
        <div className="flex flex-wrap items-center gap-2">
          {/* Suspender también vale con la cuenta de baja (H16): la baja no corta el acceso, y suspenderla es lo que corta el servicio. */}
          <AccionesDeCuenta id={cuenta.id} nombre={cuenta.nombre} suspendida={Boolean(cuenta.suspendida_en)} empresas={cuenta.empresas.length} />
          <AccionesDeBaja id={cuenta.id} nombre={cuenta.nombre} estado={cuenta.estado} />
        </div>
      </div>
      <Tabs<Pestana>
        items={pestanas}
        paneles={{
          plan: <div className="grid gap-4">{plan}</div>,
          estado: <HistorialEstado historial={cuenta.historial_estado} />,
          usuarios: <Usuarios cuentaId={cuenta.id} usuarios={cuenta.usuarios} />,
          empresas: <Empresas empresas={cuenta.empresas} hoy={hoy} />,
          comprobantes: <Comprobantes comprobantes={cuenta.comprobantes} />,
          bitacora: <Eventos eventos={cuenta.eventos} />,
        }}
      />
    </div>
  );
}
