"use client";

import { Alerta } from "khipu-design-system/feedback/alerta";
import { FunnelIcon, TrayIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { DescartarComprobante } from "@/components/admin/descartar-comprobante";
import { Etiqueta, type Tono } from "@/components/admin/etiquetas";
import { ReintentarEnvio } from "@/components/admin/reintentar-envio";
import { BusquedaDelListado } from "@/components/compartidos/busqueda-del-listado";
import { LimpiarFiltros } from "@/components/compartidos/limpiar-filtros";
import { CONTENEDOR_TABLA, FilaVacia, PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { hrefDetalleComprobante } from "@/lib/api/admin-comprobante";
import { hrefDetalleEmpresa } from "@/lib/api/admin-empresa-detalle";
import { CLASES_DE_ERROR, hrefErrores, MAX_BUSQUEDA, type ClaseDeError, type ErrorDeEmision, type ParamsErrores } from "@/lib/api/admin-errores";
import { CABECERA_TABLA, CONTROL_FILTRO, SEGMENTADO, SEGMENTO } from "@/lib/estilos";
import { faultEnPalabras } from "@/lib/errores-formato";
import { formatearFecha, formatearFechaHora } from "@/lib/formato";
import { cn } from "@/lib/utils";

/** Un error de envío todavía se puede arreglar (aviso); un rechazo de formato y un fuera de plazo ya son terminales (error). */
const TONO_DE_CLASE: Record<ClaseDeError, Tono> = { ERROR_DE_ENVIO: "aviso", ERROR_DE_FORMATO: "error", FUERA_DE_PLAZO: "error", BOLETA_SIN_ENVIAR: "aviso" };

/**
 * La cola global de errores (#196): los comprobantes con problema de todos los clientes, paginados y filtrados en el servidor. Cambiar de tipo, buscar o cambiar de página
 * solo cambia la URL y el Server Component de la página vuelve a renderizar. Solo un error de envío tiene acciones (reintentar y descartar); los otros dos ya son terminales y la
 * fila lo dice. Lo que pasó con la última acción se muestra arriba y sobrevive a la recarga que sigue.
 */
export function ColaDeErroresTabla({ errores, total, params }: { errores: ErrorDeEmision[]; total: number; params: ParamsErrores }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.admin.errores;
  const tipos = messages.documentos.tipos as Record<string, string>;
  const estados = messages.documentos.estados as Record<string, string>;
  const router = useRouter();
  const [resultado, setResultado] = useState<string | null>(null);

  const ultimaPagina = Math.max(1, Math.ceil(total / params.porPagina));
  const primero = total === 0 ? 0 : (params.pagina - 1) * params.porPagina + 1;
  const ultimo = (params.pagina - 1) * params.porPagina + errores.length;
  const conFiltros = Boolean(params.clase || params.empresa || params.q);

  /** Buscar vuelve a la primera página y conserva la clase y la empresa; en blanco quita la búsqueda. */
  function buscar(valor: string) {
    const texto = valor.trim().slice(0, MAX_BUSQUEDA);
    router.push(hrefErrores({ ...params, q: texto || undefined, pagina: 1 }));
  }

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex flex-wrap items-center gap-2.5">
        <nav aria-label={t.vistas} className={SEGMENTADO}>
          {[undefined, ...CLASES_DE_ERROR].map((clase) => (
            <Link
              key={clase ?? "TODAS"}
              href={hrefErrores({ ...params, clase, pagina: 1 })}
              aria-current={params.clase === clase ? "page" : undefined}
              className={SEGMENTO}
            >
              {t.filtros[clase ?? "TODAS"]}
            </Link>
          ))}
        </nav>

        {/* La barra de filtros del design system (§12), la misma del portal del cliente: la búsqueda se aplica tras una pausa, sin botón. */}
        <div role="search" aria-label={t.barraDeFiltros} className="ml-auto flex flex-wrap items-center justify-end gap-2.5">
          {params.empresa ? (
            // El filtro por empresa llega desde una fila («Solo una empresa»): una píldora más de la barra, que se quita con «Ver todas».
            <span data-testid="errores-empresa-filtrada" className={cn(CONTROL_FILTRO, "inline-flex items-center gap-2 px-3")}>
              {t.soloUnaEmpresa}
              <Link href={hrefErrores({ ...params, empresa: undefined, pagina: 1 })} className="text-primary underline-offset-2 hover:underline">
                {t.verTodas}
              </Link>
            </span>
          ) : null}
          <BusquedaDelListado
            id="errores-q"
            etiqueta={t.buscar}
            ayuda={t.buscar}
            placeholder={t.buscarPlaceholder}
            valor={params.q ?? ""}
            onCambio={buscar}
            ancho="w-full sm:w-64"
            maxLength={MAX_BUSQUEDA}
          />
          {conFiltros ? <LimpiarFiltros href={hrefErrores({ pagina: 1, porPagina: params.porPagina })}>{t.quitarFiltros}</LimpiarFiltros> : null}
        </div>
      </div>

      {params.clase ? <p className="text-control text-muted-foreground">{t.ayudaClase[params.clase]}</p> : null}

      {resultado ? (
        <div data-testid="errores-resultado">
          <Alerta tono="info">{resultado}</Alerta>
        </div>
      ) : null}

      <div className={CONTENEDOR_TABLA}>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.cliente}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.comprobante}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.problema}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.fault}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.intentos}</TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {errores.map((e) => (
              <TableRow key={e.comprobante_id} data-testid="errores-fila" data-clase={e.clase} data-comprobante={e.nombre_archivo} className="align-top">
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <Link href={hrefDetalleEmpresa(e.empresa_id)} className="font-medium text-foreground hover:text-primary hover:underline">
                      {e.razon_social}
                    </Link>
                    <span className="font-mono text-etiqueta text-muted-foreground">{e.ruc}</span>
                    {e.cuenta_nombre ? <span className="text-etiqueta text-muted-foreground">{e.cuenta_nombre}</span> : null}
                    {params.empresa === e.empresa_id ? null : (
                      <Link
                        href={hrefErrores({ ...params, empresa: e.empresa_id, pagina: 1 })}
                        data-testid="errores-filtrar-empresa"
                        className="mt-0.5 inline-flex items-center gap-1 text-etiqueta text-primary hover:underline"
                      >
                        <FunnelIcon className="size-3" />
                        {t.soloUnaEmpresa}
                      </Link>
                    )}
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">
                  <div className="flex flex-col">
                    {/* #251: cada fila lleva a la ficha del comprobante. */}
                    <Link href={hrefDetalleComprobante(e.comprobante_id)} className="font-mono text-control whitespace-nowrap text-foreground hover:text-primary hover:underline">
                      {e.nombre_archivo}
                    </Link>
                    <span className="text-etiqueta text-muted-foreground">
                      {tipos[e.tipo] ?? e.tipo} · {formatearFecha(e.fecha_emision, locale)}
                    </span>
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">
                  <div className="grid justify-items-start gap-1">
                    <Etiqueta tono={TONO_DE_CLASE[e.clase]}>{t.filtros[e.clase]}</Etiqueta>
                    <span className="text-etiqueta text-muted-foreground">{estados[e.estado] ?? e.estado}</span>
                  </div>
                </TableCell>
                <TableCell className="max-w-xs px-3 py-2 font-mono text-etiqueta break-words text-muted-foreground" data-testid="errores-fault">
                  {faultEnPalabras(e.fault) || t.sinFault}
                </TableCell>
                <TableCell className="px-3 py-2">
                  <div className="flex flex-col">
                    <span className="font-mono tabular-nums" data-testid="errores-intentos">
                      {e.intentos}
                    </span>
                    {e.proximo_intento ? (
                      <span className="text-etiqueta whitespace-nowrap text-muted-foreground">{t.proximoIntento.replace("{fecha}", formatearFechaHora(e.proximo_intento, locale))}</span>
                    ) : e.accionable ? (
                      <span className="text-etiqueta text-muted-foreground">{t.sinReintento}</span>
                    ) : null}
                  </div>
                </TableCell>
                <TableCell className="py-2 pr-4 pl-3">
                  {e.accionable ? (
                    <div className="flex flex-wrap gap-1.5">
                      <ReintentarEnvio comprobante={e} alResultado={setResultado} />
                      <DescartarComprobante comprobante={e} alResultado={setResultado} />
                    </div>
                  ) : (
                    <span className="text-etiqueta text-muted-foreground">{e.clase === "BOLETA_SIN_ENVIAR" ? t.decisionDelCliente : t.terminal}</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {errores.length === 0 ? (
              <FilaVacia columnas={6} icon={<TrayIcon className="size-6" />}>
                <span data-testid="errores-vacio">{params.q || params.empresa ? t.vacioConBusqueda : t.vacio[params.clase ?? "TODAS"]}</span>
              </FilaVacia>
            ) : null}
          </TableBody>
        </Table>

        <PieTabla
          desde={primero}
          hasta={ultimo}
          total={total}
          unidad="comprobantes"
          porPagina={params.porPagina}
          onPorPagina={(n) => router.push(hrefErrores({ ...params, pagina: 1, porPagina: n }))}
          pagina={params.pagina}
          ultimaPagina={ultimaPagina}
          onPagina={(p) => router.push(hrefErrores({ ...params, pagina: p }))}
          hrefPagina={(p) => hrefErrores({ ...params, pagina: p })}
        />
      </div>
    </div>
  );
}
