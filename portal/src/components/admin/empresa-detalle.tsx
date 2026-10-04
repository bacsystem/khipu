import Link from "next/link";
import type { ReactNode } from "react";
import { CambiarEntorno, ProbarConexion, RevocarApiKey } from "@/components/admin/acciones-de-empresa";
import { CertificadoEtiqueta, Etiqueta } from "@/components/admin/etiquetas";
import { CABECERA_FILA, Seccion, Vacio } from "@/components/admin/seccion";
import { ETIQUETAS_ESTADO, EstadoBadge } from "@/components/comprobantes/estado-badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { hrefDetalleCuenta } from "@/lib/api/admin-cuenta-detalle";
import type { DomicilioEmpresa, EmpresaDetalleAdmin } from "@/lib/api/admin-empresa-detalle";
import { estadoCertificadoDeEmpresa } from "@/lib/api/admin-empresas";
import type { EstadoDocumento } from "@/lib/api/facturas";
import { CABECERA_TABLA, ETIQUETA_DATO } from "@/lib/estilos";
import { formatearFecha, formatearFechaHora, formatearMonto } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.empresaDetalle;

function Dato({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className={ETIQUETA_DATO}>{etiqueta}</dt>
      <dd className="mt-0.5 text-[13px] break-words text-foreground">{children}</dd>
    </div>
  );
}

const SIN_DATO = <span className="text-muted-foreground">{t.fiscales.ninguno}</span>;

function direccion(d: DomicilioEmpresa): string {
  return [d.direccion, d.urbanizacion, [d.distrito, d.provincia, d.departamento].filter(Boolean).join(", ")].filter(Boolean).join(" · ");
}

const numero = (serie: string, n: number) => `${serie}-${String(n).padStart(8, "0")}`;

function etiquetaEstado(estado: string): string {
  return ETIQUETAS_ESTADO[estado as EstadoDocumento] ?? estado;
}

function Fiscales({ e }: { e: EmpresaDetalleAdmin }) {
  const f = t.fiscales;
  return (
    <Seccion titulo={f.titulo} id="empresa-fiscales">
      <dl className="grid gap-x-8 gap-y-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
        <Dato etiqueta={f.razonSocial}>{e.razon_social}</Dato>
        <Dato etiqueta={f.nombreComercial}>{e.nombre_comercial ?? SIN_DATO}</Dato>
        <Dato etiqueta={f.ruc}>
          <span className="font-mono">{e.ruc}</span>
        </Dato>
        <Dato etiqueta={f.domicilio}>
          {e.domicilio ? (
            <>
              <span data-testid="domicilio">{direccion(e.domicilio)}</span>
              <span className="block font-mono text-[11px] text-muted-foreground">
                {f.ubigeo} {e.domicilio.ubigeo}
                {e.domicilio.codigo_establecimiento ? ` · ${e.domicilio.codigo_establecimiento}` : ""}
              </span>
            </>
          ) : (
            <span className="text-muted-foreground">{f.sinDomicilio}</span>
          )}
        </Dato>
        <Dato etiqueta={f.detracciones}>{e.cuenta_detracciones ? <span className="font-mono">{e.cuenta_detracciones}</span> : SIN_DATO}</Dato>
        <Dato etiqueta={f.padron}>{e.padron_tasa_especial_igv ? f.si : f.no}</Dato>
      </dl>
    </Seccion>
  );
}

function Conexion({ e }: { e: EmpresaDetalleAdmin }) {
  const c = t.conexion;
  return (
    <Seccion titulo={c.titulo} id="empresa-conexion">
      <div className="grid gap-3 p-4">
        <dl className="flex flex-wrap gap-x-8 gap-y-3">
          <Dato etiqueta={c.certificado}>
            <CertificadoEtiqueta estado={estadoCertificadoDeEmpresa(e)} />
          </Dato>
          <Dato etiqueta={c.sol}>
            <Etiqueta tono={e.tiene_credenciales_sol ? "ok" : "neutro"}>{e.tiene_credenciales_sol ? c.solCargadas : c.solSinCargar}</Etiqueta>
          </Dato>
        </dl>
        <p className="text-xs text-muted-foreground">{c.nota}</p>
        <ProbarConexion empresaId={e.id} tieneSol={e.tiene_credenciales_sol} />
      </div>
    </Seccion>
  );
}

function Series({ series }: { series: EmpresaDetalleAdmin["series"] }) {
  const s = t.series;
  return (
    <Seccion titulo={s.titulo} id="empresa-series">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{s.columnas.serie}</TableHead>
            <TableHead className={CABECERA_TABLA}>{s.columnas.tipo}</TableHead>
            <TableHead className={`${CABECERA_TABLA} text-right`}>{s.columnas.ultimo}</TableHead>
            <TableHead className={CABECERA_TABLA}>{s.columnas.estado}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{s.columnas.establecimiento}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {series.map((x) => (
            <TableRow key={`${x.tipo}-${x.codigo}`} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px]">{x.codigo}</TableCell>
              <TableCell className="px-3 py-2 font-mono text-[12px]">{x.tipo}</TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{x.ultimo_numero}</TableCell>
              <TableCell className="px-3 py-2">
                <Etiqueta tono={x.activa ? "ok" : "neutro"}>{x.activa ? s.activa : s.inactiva}</Etiqueta>
              </TableCell>
              <TableCell className="py-2 pr-4 pl-3 font-mono text-[12px]">{x.establecimiento}</TableCell>
            </TableRow>
          ))}
          {series.length === 0 ? <Vacio columnas={5} texto={s.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function Establecimientos({ establecimientos }: { establecimientos: EmpresaDetalleAdmin["establecimientos"] }) {
  const s = t.establecimientos;
  return (
    <Seccion titulo={s.titulo} id="empresa-establecimientos">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{s.columnas.codigo}</TableHead>
            <TableHead className={CABECERA_TABLA}>{s.columnas.nombre}</TableHead>
            <TableHead className={CABECERA_TABLA}>{s.columnas.direccion}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{s.columnas.estado}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {establecimientos.map((x) => (
            <TableRow key={x.codigo} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px]">{x.codigo}</TableCell>
              <TableCell className="px-3 py-2">{x.nombre}</TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{direccion(x.domicilio)}</TableCell>
              <TableCell className="py-2 pr-4 pl-3">
                <Etiqueta tono={x.activo ? "ok" : "neutro"}>{x.activo ? s.activo : s.inactivo}</Etiqueta>
              </TableCell>
            </TableRow>
          ))}
          {establecimientos.length === 0 ? <Vacio columnas={4} texto={s.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function ApiKeys({ empresaId, razonSocial, keys }: { empresaId: string; razonSocial: string; keys: EmpresaDetalleAdmin["api_keys"] }) {
  const k = t.apiKeys;
  return (
    <Seccion titulo={k.titulo} id="empresa-api-keys">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{k.columnas.prefijo}</TableHead>
            <TableHead className={CABECERA_TABLA}>{k.columnas.estado}</TableHead>
            <TableHead className={CABECERA_TABLA}>{k.columnas.creada}</TableHead>
            <TableHead className={CABECERA_TABLA}>{k.columnas.revocada}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4 text-right`}>{k.columnas.acciones}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {keys.map((x) => (
            <TableRow key={x.id} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px]">{x.prefijo}…</TableCell>
              <TableCell className="px-3 py-2">
                <Etiqueta tono={x.activa ? "ok" : "neutro"}>{x.activa ? k.activa : k.revocada}</Etiqueta>
              </TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{formatearFechaHora(x.creada_en)}</TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{x.revocada_en ? formatearFechaHora(x.revocada_en) : SIN_DATO}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-right">
                {x.activa ? <RevocarApiKey empresaId={empresaId} apiKeyId={x.id} prefijo={x.prefijo} razonSocial={razonSocial} /> : null}
              </TableCell>
            </TableRow>
          ))}
          {keys.length === 0 ? <Vacio columnas={5} texto={k.vacio} /> : null}
        </TableBody>
      </Table>
      <p className="border-t border-border/60 px-4 py-2 text-xs text-muted-foreground">{k.nota}</p>
    </Seccion>
  );
}

function Pdf({ pdf }: { pdf: EmpresaDetalleAdmin["pdf"] }) {
  const p = t.pdf;
  return (
    <Seccion titulo={p.titulo} id="empresa-pdf">
      <dl className="grid gap-x-8 gap-y-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
        <Dato etiqueta={p.plantilla}>{pdf.plantilla}</Dato>
        <Dato etiqueta={p.color}>
          <span className="inline-flex items-center gap-2 font-mono">
            <span className="inline-block size-3.5 rounded-sm border border-border" style={{ backgroundColor: pdf.color_primario }} aria-hidden="true" />
            {pdf.color_primario}
          </span>
        </Dato>
        <Dato etiqueta={p.logo}>
          <Etiqueta tono={pdf.tiene_logo ? "ok" : "neutro"}>{pdf.tiene_logo ? p.conLogo : p.sinLogo}</Etiqueta>
        </Dato>
        <Dato etiqueta={p.pie}>{pdf.pie_de_pagina ?? SIN_DATO}</Dato>
        <Dato etiqueta={p.observaciones}>{pdf.observaciones_por_defecto ?? SIN_DATO}</Dato>
      </dl>
    </Seccion>
  );
}

function Comprobantes({ comprobantes }: { comprobantes: EmpresaDetalleAdmin["comprobantes"] }) {
  const c = t.comprobantes;
  return (
    <Seccion titulo={c.titulo} id="empresa-comprobantes">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{c.columnas.comprobante}</TableHead>
            <TableHead className={CABECERA_TABLA}>{c.columnas.fecha}</TableHead>
            <TableHead className={CABECERA_TABLA}>{c.columnas.estado}</TableHead>
            <TableHead className={`${CABECERA_TABLA} text-right`}>{c.columnas.total}</TableHead>
            <TableHead className={`${CABECERA_TABLA} text-right`}>{c.columnas.intentos}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{c.columnas.cdr}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {comprobantes.map((x) => (
            <TableRow key={x.id} className="border-b border-border/60 align-top">
              <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px]">{numero(x.serie, x.numero)}</TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{formatearFecha(x.fecha_emision)}</TableCell>
              <TableCell className="px-3 py-2">
                <EstadoBadge estado={x.estado} />
              </TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{formatearMonto(x.moneda, x.total)}</TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{x.intentos}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-[12px]">
                {x.cdr ? (
                  <div data-testid="cdr" className="grid gap-0.5">
                    <span>
                      <span className="font-mono">{x.cdr.codigo}</span> · {x.cdr.descripcion}
                    </span>
                    {x.cdr.observaciones.length > 0 ? (
                      <ul className="list-disc pl-4 text-muted-foreground">
                        {x.cdr.observaciones.map((o) => (
                          <li key={o}>{o}</li>
                        ))}
                      </ul>
                    ) : (
                      <span className="text-muted-foreground">{c.sinObservaciones}</span>
                    )}
                  </div>
                ) : (
                  <span className="text-muted-foreground">{c.sinCdr}</span>
                )}
                {x.ultimo_error ? (
                  <p className="mt-1 text-destructive">
                    {c.ultimoError}: {x.ultimo_error}
                  </p>
                ) : null}
              </TableCell>
            </TableRow>
          ))}
          {comprobantes.length === 0 ? <Vacio columnas={6} texto={c.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function Eventos({ eventos }: { eventos: EmpresaDetalleAdmin["eventos"] }) {
  const v = t.eventos;
  return (
    <Seccion titulo={v.titulo} id="empresa-eventos">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{v.columnas.comprobante}</TableHead>
            <TableHead className={CABECERA_TABLA}>{v.columnas.cambio}</TableHead>
            <TableHead className={CABECERA_TABLA}>{v.columnas.detalle}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{v.columnas.cuando}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {eventos.map((x, i) => (
            <TableRow key={`${x.comprobante}-${x.ocurrido_en}-${i}`} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px]">{x.comprobante}</TableCell>
              <TableCell className="px-3 py-2 text-[12px]">
                {x.estado_anterior ? etiquetaEstado(x.estado_anterior) : v.inicio} → <span className="font-medium">{etiquetaEstado(x.estado_nuevo)}</span>
              </TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{x.detalle ?? SIN_DATO}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-[12px] text-foreground/80">{formatearFechaHora(x.ocurrido_en)}</TableCell>
            </TableRow>
          ))}
          {eventos.length === 0 ? <Vacio columnas={4} texto={v.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function Outbox({ outbox }: { outbox: EmpresaDetalleAdmin["outbox"] }) {
  const o = t.outbox;
  return (
    <Seccion titulo={o.titulo} id="empresa-outbox">
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{o.columnas.tarea}</TableHead>
            <TableHead className={`${CABECERA_TABLA} text-right`}>{o.columnas.intentos}</TableHead>
            <TableHead className={CABECERA_TABLA}>{o.columnas.proximo}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{o.columnas.error}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {outbox.proximas.map((x) => (
            <TableRow key={`${x.agregado_id}-${x.accion}`} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4 text-[12px]">
                <span className="font-medium">{x.accion}</span> <span className="text-muted-foreground">{x.agregado}</span>
              </TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{x.intentos}</TableCell>
              <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{formatearFechaHora(x.siguiente_intento)}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-[12px] text-destructive">{x.ultimo_error ?? SIN_DATO}</TableCell>
            </TableRow>
          ))}
          {outbox.proximas.length === 0 ? <Vacio columnas={4} texto={o.vacio} /> : null}
        </TableBody>
      </Table>
      {outbox.total > 0 ? (
        <p data-testid="outbox-total" className="border-t border-border/60 px-4 py-2 text-xs text-muted-foreground">
          {outbox.total === 1 ? o.unaTarea : o.total.replace("{n}", String(outbox.total))}
        </p>
      ) : null}
    </Seccion>
  );
}

/**
 * Detalle de una empresa del backoffice (#186): lo mismo que ve su dueño. Desde aquí el administrador puede cambiar el entorno, revocar una API key y
 * probar la conexión con SUNAT (#187), cada cosa con su confirmación (salvo la prueba, que no cambia nada) y su registro en la bitácora; las demás
 * acciones llegan en sus issues.
 */
export function EmpresaDetalle({ empresa }: { empresa: EmpresaDetalleAdmin }) {
  return (
    <div className="grid min-w-0 gap-6" data-testid="empresa-detalle">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <dl className="flex flex-wrap gap-x-8 gap-y-2 text-sm">
          <Dato etiqueta={t.fiscales.ruc}>
            <span className="font-mono">{empresa.ruc}</span>
          </Dato>
          <Dato etiqueta={t.cuenta}>
            {empresa.cuenta_id ? (
              <Link href={hrefDetalleCuenta(empresa.cuenta_id)} className="text-primary hover:underline">
                {empresa.cuenta_nombre}
              </Link>
            ) : (
              <span className="text-muted-foreground">{t.sinCuenta}</span>
            )}
          </Dato>
          <Dato etiqueta={t.fiscales.entorno}>
            <Etiqueta tono={empresa.entorno === "PRODUCCION" ? "ok" : "neutro"}>
              {empresa.entorno === "PRODUCCION" ? messages.admin.empresas.produccion : messages.admin.empresas.beta}
            </Etiqueta>
          </Dato>
          <Dato etiqueta={t.alta}>{formatearFechaHora(empresa.creada_en)}</Dato>
        </dl>
        <CambiarEntorno empresaId={empresa.id} razonSocial={empresa.razon_social} entorno={empresa.entorno} />
      </div>
      <p className="text-xs text-muted-foreground">{t.soloLectura}</p>
      <Fiscales e={empresa} />
      <Conexion e={empresa} />
      <Series series={empresa.series} />
      <Establecimientos establecimientos={empresa.establecimientos} />
      <ApiKeys empresaId={empresa.id} razonSocial={empresa.razon_social} keys={empresa.api_keys} />
      <Pdf pdf={empresa.pdf} />
      <Comprobantes comprobantes={empresa.comprobantes} />
      <Eventos eventos={empresa.eventos} />
      <Outbox outbox={empresa.outbox} />
    </div>
  );
}
