"use client";

import { ArrowClockwiseIcon, StorefrontIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { useTransition } from "react";
import { CONTENEDOR_TABLA, FilaVacia } from "khipu-design-system/patrones/pie-tabla";
import { ChipCodigo, PillEstado } from "khipu-design-system/patrones/pill-estado";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { apiRequest } from "@/lib/api/browser";
import type { Establecimiento } from "@/lib/api/establecimientos";
import type { Serie } from "@/lib/api/series";
import { enlaceASeccion } from "@/lib/empresa/secciones";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { EstablecimientoDialog } from "./establecimiento-dialog";
import { ACCION_SECUNDARIA, CABECERA_TABLA, CONTROL_FILTRO } from "@/lib/estilos";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { ConfirmacionEnLinea } from "khipu-design-system/patrones/confirmacion-en-linea";
import { Spinner } from "khipu-design-system/feedback/spinner";

function Estado({ activo, principal }: { activo: boolean; principal: boolean }) {
  const t = useMessages().establecimientos.tabla.estado;
  // El domicilio fiscal no es un estado sino la identidad del 0000: va como el chip de identificador del DS.
  if (principal) return <ChipCodigo>{t.domicilioFiscal}</ChipCodigo>;
  return activo ? <PillEstado tono="ok">{t.activo}</PillEstado> : <PillEstado tono="neutro">{t.dadoDeBaja}</PillEstado>;
}

/** Editar (diálogo) y dar de baja (confirmación en línea). El 0000 se edita en Empresa y no se da de baja. */
function Acciones({ establecimiento, seriesActivas }: { establecimiento: Establecimiento; seriesActivas: string[] }) {
  const messages = useMessages();
  const t = messages.establecimientos.tabla;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const sinPermiso = useSinPermisoDeConfigurar();

  if (establecimiento.principal)
    return (
      <Link href={enlaceASeccion("datos")} className={ACCION_SECUNDARIA}>
        {t.editarEnEmpresa}
      </Link>
    );
  if (!establecimiento.activo) return <span className="text-etiqueta text-muted-foreground/60">—</span>;

  async function darDeBaja() {
    const res = await apiRequest<null>(`/api/proxy/empresa/establecimientos/${establecimiento.codigo}`, { method: "DELETE" });
    if (res.estado !== "exito") return res.mensaje ?? mensajeError(res.codigo);
    router.refresh();
  }

  return (
    <div className="flex flex-wrap items-start justify-end gap-1">
      <EstablecimientoDialog existente={establecimiento} className={ACCION_SECUNDARIA} />
      {/* H4 (#21): dar de baja es configuración; sin ese permiso (emisor, solo lectura, modo soporte) o con series activas se ve
          deshabilitado. La confirmación del DS no recibe `disabled`: lo apaga el `fieldset`, que también lleva el título y el testid. */}
      <fieldset
        disabled={sinPermiso !== null || seriesActivas.length > 0}
        title={sinPermiso ?? (seriesActivas.length > 0 ? interpolar(t.tieneSeriesActivas, { series: seriesActivas.join(", ") }) : undefined)}
        className="m-0 min-w-0 border-0 p-0"
        data-testid={`baja-establecimiento-${establecimiento.codigo}`}
      >
        <ConfirmacionEnLinea
          etiqueta={t.darDeBaja}
          pregunta={interpolar(t.confirmarBaja, { codigo: establecimiento.codigo })}
          confirmar={t.siDarDeBaja}
          onConfirmar={darDeBaja}
        />
      </fieldset>
    </div>
  );
}

export function EstablecimientosTable({ establecimientos, series }: { establecimientos: Establecimiento[]; series: Serie[] }) {
  const messages = useMessages();
  const t = messages.establecimientos.tabla;
  const router = useRouter();
  const [refrescando, startTransition] = useTransition();
  const seriesDe = (codigo: string) => series.filter((s) => s.activa && (s.establecimiento ?? "0000") === codigo).map((s) => s.serie);

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <CabeceraSeccion
        icon={StorefrontIcon}
        titulo={t.titulo}
        subtitulo={t.subtitulo}
        conBorde={false}
        className="p-0"
        derecha={
          <>
            <div className="flex flex-wrap items-center gap-2.5">
              <button
                type="button"
                onClick={() => startTransition(() => router.refresh())}
                title={t.refrescar}
                className={cn(CONTROL_FILTRO, "inline-flex size-control items-center justify-center text-muted-foreground transition-colors hover:text-foreground")}
              >
                {refrescando ? <Spinner aria-label={messages.comun.cargando} /> : <ArrowClockwiseIcon weight="bold" className="size-4" />}
              </button>
              {/* «Nuevo establecimiento» vive en la barra superior, como la acción principal de las demás páginas (#277). */}
            </div>
          </>
        }
      />

      <div className={cn(CONTENEDOR_TABLA, refrescando && "opacity-60")}>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.codigo}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.nombre}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.direccion}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.ubigeo}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.series}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "px-4")}>{t.columnas.estado}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "pr-4 pl-2 text-right")}>{t.columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {establecimientos.map((e) => {
              const activas = seriesDe(e.codigo);
              return (
                <TableRow key={e.codigo} className={cn(!e.activo && "opacity-70")} data-testid={`establecimiento-${e.codigo}`}>
                  <TableCell className="py-2 pr-3 pl-4">
                    <span className={cn("rounded px-2 py-0.5 font-mono text-cuerpo font-semibold tracking-tight", e.activo ? "bg-secondary text-primary" : "bg-muted text-muted-foreground")}>{e.codigo}</span>
                  </TableCell>
                  <TableCell className="px-3 py-2 font-medium text-foreground">{e.nombre}</TableCell>
                  <TableCell className="px-3 py-2 text-foreground/90">
                    {e.domicilio.direccion}
                    {e.domicilio.urbanizacion ? <span className="text-muted-foreground">, {e.domicilio.urbanizacion}</span> : null}
                    <div className="text-etiqueta text-muted-foreground">
                      {[e.domicilio.distrito, e.domicilio.provincia, e.domicilio.departamento].filter(Boolean).join(" · ")}
                    </div>
                  </TableCell>
                  <TableCell className="px-3 py-2 font-mono text-control text-muted-foreground tabular-nums">{e.domicilio.ubigeo}</TableCell>
                  <TableCell className="px-3 py-2 font-mono text-control text-foreground/90">{activas.length > 0 ? activas.join(", ") : <span className="text-muted-foreground/60">—</span>}</TableCell>
                  <TableCell className="px-4 py-2 whitespace-nowrap">
                    <Estado activo={e.activo} principal={e.principal} />
                  </TableCell>
                  <TableCell className="py-2 pr-4 pl-2 text-right">
                    <Acciones establecimiento={e} seriesActivas={activas} />
                  </TableCell>
                </TableRow>
              );
            })}
            {establecimientos.length === 0 ? (
              <FilaVacia columnas={7} icon={<StorefrontIcon className="size-6" />}>
                {t.vacioAntes}{" "}
                <Link href={enlaceASeccion("datos")} className="text-primary hover:underline">
                  {t.vacioEnlace}
                </Link>{" "}
                {t.vacioDespues}
              </FilaVacia>
            ) : null}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
