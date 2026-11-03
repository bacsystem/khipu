import {
  ClockCounterClockwiseIcon,
  FilePdfIcon,
  HashIcon,
  IdentificationCardIcon,
  KeyIcon,
  PlugsConnectedIcon,
  ReceiptIcon,
  StorefrontIcon,
  TrayArrowUpIcon,
} from "@phosphor-icons/react/dist/ssr";
import { useLocale, useMessages } from "next-intl";
import Link from "next/link";
import { CambiarEntorno, ProbarConexion, RevocarApiKey } from "@/components/admin/acciones-de-empresa";
import { CertificadoEtiqueta, Etiqueta } from "@/components/admin/etiquetas";
import { CABECERA_FILA, Seccion, Vacio } from "@/components/admin/seccion";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { ListaDatos } from "khipu-design-system/datos/lista-datos";
import { Tabs } from "khipu-design-system/navegacion/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { hrefDetalleComprobante } from "@/lib/api/admin-comprobante";
import { hrefDetalleCuenta } from "@/lib/api/admin-cuenta-detalle";
import type { DomicilioEmpresa, EmpresaDetalleAdmin } from "@/lib/api/admin-empresa-detalle";
import { estadoCertificadoDeEmpresa } from "@/lib/api/admin-empresas";
import { plantillasPdf } from "@/lib/api/empresas";
import { CABECERA_TABLA, TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { formatearFecha, formatearFechaHora, formatearMonto } from "@/lib/formato";
import type { Mensajes } from "@/i18n/mensajes";

/** «—» o lo que diga el catálogo cuando un dato no está. */
function SinDato() {
  return <span className="text-muted-foreground">{useMessages().admin.empresaDetalle.fiscales.ninguno}</span>;
}

function direccion(d: DomicilioEmpresa): string {
  return [d.direccion, d.urbanizacion, [d.distrito, d.provincia, d.departamento].filter(Boolean).join(", ")].filter(Boolean).join(" · ");
}

const numero = (serie: string, n: number) => `${serie}-${String(n).padStart(8, "0")}`;

function etiquetaEstado(estado: string, messages: Mensajes): string {
  return (messages.documentos.estados as Record<string, string>)[estado] ?? estado;
}

function Fiscales({ e }: { e: EmpresaDetalleAdmin }) {
  const t = useMessages().admin.empresaDetalle;
  const f = t.fiscales;
  return (
    <Seccion titulo={f.titulo} id="empresa-fiscales" icon={IdentificationCardIcon}>
      <ListaDatos
        columnas={2}
        className="px-4 pt-1 pb-4"
        datos={[
          { etiqueta: f.razonSocial, valor: e.razon_social },
          { etiqueta: f.nombreComercial, valor: e.nombre_comercial ?? <SinDato /> },
          { etiqueta: f.ruc, valor: e.ruc, mono: true },
          {
            etiqueta: f.domicilio,
            valor: e.domicilio ? (
              <>
                <span data-testid="domicilio">{direccion(e.domicilio)}</span>
                <span className="block font-mono text-etiqueta text-muted-foreground">
                  {f.ubigeo} {e.domicilio.ubigeo}
                  {e.domicilio.codigo_establecimiento ? ` · ${e.domicilio.codigo_establecimiento}` : ""}
                </span>
              </>
            ) : (
              <span className="text-muted-foreground">{f.sinDomicilio}</span>
            ),
          },
          { etiqueta: f.detracciones, valor: e.cuenta_detracciones ? <span className="font-mono">{e.cuenta_detracciones}</span> : <SinDato /> },
          { etiqueta: f.padron, valor: e.padron_tasa_especial_igv ? f.si : f.no },
        ]}
      />
    </Seccion>
  );
}

function Conexion({ e }: { e: EmpresaDetalleAdmin }) {
  const t = useMessages().admin.empresaDetalle;
  const c = t.conexion;
  return (
    <Seccion titulo={c.titulo} id="empresa-conexion" icon={PlugsConnectedIcon}>
      <div className="grid gap-3 px-4 pt-1 pb-4">
        <ListaDatos
          columnas={2}
          datos={[
            { etiqueta: c.certificado, valor: <CertificadoEtiqueta estado={estadoCertificadoDeEmpresa(e)} /> },
            {
              etiqueta: c.sol,
              valor: <Etiqueta tono={e.tiene_credenciales_sol ? "ok" : "neutro"}>{e.tiene_credenciales_sol ? c.solCargadas : c.solSinCargar}</Etiqueta>,
            },
          ]}
        />
        <p className="text-etiqueta text-muted-foreground">{c.nota}</p>
        <ProbarConexion empresaId={e.id} tieneSol={e.tiene_credenciales_sol} />
      </div>
    </Seccion>
  );
}

function Series({ series }: { series: EmpresaDetalleAdmin["series"] }) {
  const messages = useMessages();
  const t = messages.admin.empresaDetalle;
  const s = t.series;
  return (
    <Seccion titulo={s.titulo} id="empresa-series" icon={HashIcon} tabla>
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
        <TableBody className="text-control">
          {series.map((x) => (
            <TableRow key={`${x.tipo}-${x.codigo}`}>
              <TableCell className="py-2 pr-3 pl-4 font-mono text-control">{x.codigo}</TableCell>
              <TableCell className="px-3 py-2 text-control" title={x.tipo}>
                {(messages.documentos.tipos as Record<string, string>)[x.tipo] ?? x.tipo}
              </TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{x.ultimo_numero}</TableCell>
              <TableCell className="px-3 py-2">
                <Etiqueta tono={x.activa ? "ok" : "neutro"}>{x.activa ? s.activa : s.inactiva}</Etiqueta>
              </TableCell>
              <TableCell className="py-2 pr-4 pl-3 font-mono text-control">{x.establecimiento}</TableCell>
            </TableRow>
          ))}
          {series.length === 0 ? <Vacio columnas={5} texto={s.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function Establecimientos({ establecimientos }: { establecimientos: EmpresaDetalleAdmin["establecimientos"] }) {
  const t = useMessages().admin.empresaDetalle;
  const s = t.establecimientos;
  return (
    <Seccion titulo={s.titulo} id="empresa-establecimientos" icon={StorefrontIcon} tabla>
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{s.columnas.codigo}</TableHead>
            <TableHead className={CABECERA_TABLA}>{s.columnas.nombre}</TableHead>
            <TableHead className={CABECERA_TABLA}>{s.columnas.direccion}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{s.columnas.estado}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-control">
          {establecimientos.map((x) => (
            <TableRow key={x.codigo}>
              <TableCell className="py-2 pr-3 pl-4 font-mono text-control">{x.codigo}</TableCell>
              <TableCell className="px-3 py-2">{x.nombre}</TableCell>
              <TableCell className="px-3 py-2 text-control text-foreground/80">{direccion(x.domicilio)}</TableCell>
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
  const locale = useLocale();
  const t = useMessages().admin.empresaDetalle;
  const k = t.apiKeys;
  return (
    <Seccion titulo={k.titulo} id="empresa-api-keys" icon={KeyIcon} tabla>
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
        <TableBody className="text-control">
          {keys.map((x) => (
            <TableRow key={x.id}>
              <TableCell className="py-2 pr-3 pl-4 font-mono text-control">{x.prefijo}…</TableCell>
              <TableCell className="px-3 py-2">
                <Etiqueta tono={x.activa ? "ok" : "neutro"}>{x.activa ? k.activa : k.revocada}</Etiqueta>
              </TableCell>
              <TableCell className="px-3 py-2 text-control text-foreground/80">{formatearFechaHora(x.creada_en, locale)}</TableCell>
              <TableCell className="px-3 py-2 text-control text-foreground/80">{x.revocada_en ? formatearFechaHora(x.revocada_en, locale) : <SinDato />}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-right">
                {x.activa ? <RevocarApiKey empresaId={empresaId} apiKeyId={x.id} prefijo={x.prefijo} razonSocial={razonSocial} /> : null}
              </TableCell>
            </TableRow>
          ))}
          {keys.length === 0 ? <Vacio columnas={5} texto={k.vacio} /> : null}
        </TableBody>
      </Table>
      <p className="px-4 py-2 text-etiqueta text-muted-foreground">{k.nota}</p>
    </Seccion>
  );
}

/** El backend manda la plantilla en mayúsculas (`CLASICO`) y el catálogo del portal la nombra en minúsculas: se busca sin importar eso. */
function nombreDePlantilla(plantilla: string, messages: Mensajes): string {
  return plantillasPdf(messages).find((x) => x.id === plantilla.toLowerCase())?.nombre ?? plantilla;
}

function Pdf({ pdf }: { pdf: EmpresaDetalleAdmin["pdf"] }) {
  const messages = useMessages();
  const t = messages.admin.empresaDetalle;
  const p = t.pdf;
  return (
    <Seccion titulo={p.titulo} id="empresa-pdf" icon={FilePdfIcon}>
      <ListaDatos
        columnas={2}
        className="px-4 pt-1 pb-4"
        datos={[
          { etiqueta: p.plantilla, valor: nombreDePlantilla(pdf.plantilla, messages) },
          {
            etiqueta: p.color,
            valor: (
              <span className="inline-flex items-center gap-2 font-mono">
                <span className="inline-block size-3.5 rounded-sm border border-border" style={{ backgroundColor: pdf.color_primario }} aria-hidden="true" />
                {pdf.color_primario}
              </span>
            ),
          },
          { etiqueta: p.logo, valor: <Etiqueta tono={pdf.tiene_logo ? "ok" : "neutro"}>{pdf.tiene_logo ? p.conLogo : p.sinLogo}</Etiqueta> },
          { etiqueta: p.pie, valor: pdf.pie_de_pagina ?? <SinDato /> },
          { etiqueta: p.observaciones, valor: pdf.observaciones_por_defecto ?? <SinDato /> },
        ]}
      />
    </Seccion>
  );
}

function Comprobantes({ comprobantes }: { comprobantes: EmpresaDetalleAdmin["comprobantes"] }) {
  const locale = useLocale();
  const t = useMessages().admin.empresaDetalle;
  const c = t.comprobantes;
  return (
    <Seccion titulo={c.titulo} id="empresa-comprobantes" icon={ReceiptIcon} tabla>
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
        <TableBody className="text-control">
          {comprobantes.map((x) => (
            <TableRow key={x.id} className="align-top">
              <TableCell className="py-2 pr-3 pl-4 font-mono text-control">
                <Link href={hrefDetalleComprobante(x.id)} className="text-primary hover:underline" data-testid="comprobante-enlace">
                  {numero(x.serie, x.numero)}
                </Link>
              </TableCell>
              <TableCell className="px-3 py-2 text-control text-foreground/80">{formatearFecha(x.fecha_emision, locale)}</TableCell>
              <TableCell className="px-3 py-2">
                <EstadoBadge estado={x.estado} />
              </TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{formatearMonto(x.moneda, x.total, locale)}</TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{x.intentos}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-control">
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
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.admin.empresaDetalle;
  const v = t.eventos;
  return (
    <Seccion titulo={v.titulo} id="empresa-eventos" icon={ClockCounterClockwiseIcon} tabla>
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{v.columnas.comprobante}</TableHead>
            <TableHead className={CABECERA_TABLA}>{v.columnas.cambio}</TableHead>
            <TableHead className={CABECERA_TABLA}>{v.columnas.detalle}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{v.columnas.cuando}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-control">
          {eventos.map((x, i) => (
            <TableRow key={`${x.comprobante}-${x.ocurrido_en}-${i}`}>
              <TableCell className="py-2 pr-3 pl-4 font-mono text-control">{x.comprobante}</TableCell>
              <TableCell className="px-3 py-2 text-control">
                {x.estado_anterior ? etiquetaEstado(x.estado_anterior, messages) : v.inicio} → <span className="font-medium">{etiquetaEstado(x.estado_nuevo, messages)}</span>
              </TableCell>
              <TableCell className="px-3 py-2 text-control text-foreground/80">{x.detalle ?? <SinDato />}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-control text-foreground/80">{formatearFechaHora(x.ocurrido_en, locale)}</TableCell>
            </TableRow>
          ))}
          {eventos.length === 0 ? <Vacio columnas={4} texto={v.vacio} /> : null}
        </TableBody>
      </Table>
    </Seccion>
  );
}

function Outbox({ outbox }: { outbox: EmpresaDetalleAdmin["outbox"] }) {
  const locale = useLocale();
  const t = useMessages().admin.empresaDetalle;
  const o = t.outbox;
  return (
    <Seccion titulo={o.titulo} id="empresa-outbox" icon={TrayArrowUpIcon} tabla>
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{o.columnas.tarea}</TableHead>
            <TableHead className={`${CABECERA_TABLA} text-right`}>{o.columnas.intentos}</TableHead>
            <TableHead className={CABECERA_TABLA}>{o.columnas.proximo}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{o.columnas.error}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-control">
          {outbox.proximas.map((x) => (
            <TableRow key={`${x.agregado_id}-${x.accion}`}>
              <TableCell className="py-2 pr-3 pl-4 text-control">
                <span className="font-medium">{x.accion}</span> <span className="text-muted-foreground">{x.agregado}</span>
              </TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{x.intentos}</TableCell>
              <TableCell className="px-3 py-2 text-control text-foreground/80">{formatearFechaHora(x.siguiente_intento, locale)}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-control text-destructive">{x.ultimo_error ?? <SinDato />}</TableCell>
            </TableRow>
          ))}
          {outbox.proximas.length === 0 ? <Vacio columnas={4} texto={o.vacio} /> : null}
        </TableBody>
      </Table>
      {outbox.total > 0 ? (
        <p data-testid="outbox-total" className="px-4 py-2 text-etiqueta text-muted-foreground">
          {outbox.total === 1 ? o.unaTarea : o.total.replace("{n}", String(outbox.total))}
        </p>
      ) : null}
    </Seccion>
  );
}

type Pestana = "datos" | "emision" | "apiKeys" | "comprobantes" | "envios";

/**
 * Detalle de una empresa del backoffice (#186): lo mismo que ve su dueño. Arriba el RUC, la cuenta, el entorno (que se puede cambiar, #187) y el alta;
 * abajo, en pestañas (H19), lo que antes eran nueve tablas apiladas: datos fiscales y conexión con SUNAT, lo que define cómo emite (series,
 * establecimientos y diseño del PDF), las API keys, los comprobantes con sus cambios de estado y la cola de envíos pendientes.
 */
export function EmpresaDetalle({ empresa }: { empresa: EmpresaDetalleAdmin }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.admin.empresaDetalle;
  const p = t.pestanas;
  const pestanas: Array<{ id: Pestana; etiqueta: string; contador?: number }> = [
    { id: "datos", etiqueta: p.datos },
    { id: "emision", etiqueta: p.emision },
    { id: "apiKeys", etiqueta: p.apiKeys, contador: empresa.api_keys.filter((k) => k.activa).length },
    { id: "comprobantes", etiqueta: p.comprobantes },
    { id: "envios", etiqueta: p.envios, contador: empresa.outbox.total },
  ];
  return (
    <div className="grid min-w-0 gap-3" data-testid="empresa-detalle">
      <section className={cn(TARJETA, "flex flex-wrap items-start justify-between gap-4 p-4")} aria-label={empresa.razon_social}>
        <ListaDatos
          columnas={2}
          className="min-w-0 flex-1 basis-96"
          datos={[
            { etiqueta: t.fiscales.ruc, valor: empresa.ruc, mono: true },
            {
              etiqueta: t.cuenta,
              valor: empresa.cuenta_id ? (
                <Link href={hrefDetalleCuenta(empresa.cuenta_id)} className="text-primary hover:underline">
                  {empresa.cuenta_nombre}
                </Link>
              ) : (
                <span className="text-muted-foreground">{t.sinCuenta}</span>
              ),
            },
            {
              etiqueta: t.fiscales.entorno,
              valor: (
                <Etiqueta tono={empresa.entorno === "PRODUCCION" ? "ok" : "neutro"}>
                  {empresa.entorno === "PRODUCCION" ? messages.admin.empresas.produccion : messages.admin.empresas.beta}
                </Etiqueta>
              ),
            },
            { etiqueta: t.alta, valor: formatearFechaHora(empresa.creada_en, locale) },
          ]}
        />
        <CambiarEntorno empresaId={empresa.id} razonSocial={empresa.razon_social} entorno={empresa.entorno} />
      </section>
      <Tabs<Pestana>
        items={pestanas}
        paneles={{
          datos: (
            <div className="grid gap-3">
              <Fiscales e={empresa} />
              <Conexion e={empresa} />
            </div>
          ),
          emision: (
            <div className="grid gap-3">
              <Series series={empresa.series} />
              <Establecimientos establecimientos={empresa.establecimientos} />
              <Pdf pdf={empresa.pdf} />
            </div>
          ),
          apiKeys: <ApiKeys empresaId={empresa.id} razonSocial={empresa.razon_social} keys={empresa.api_keys} />,
          comprobantes: (
            <div className="grid gap-3">
              <Comprobantes comprobantes={empresa.comprobantes} />
              <Eventos eventos={empresa.eventos} />
            </div>
          ),
          envios: <Outbox outbox={empresa.outbox} />,
        }}
      />
    </div>
  );
}
