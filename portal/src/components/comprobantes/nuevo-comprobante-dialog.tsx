"use client";

import { FileTextIcon, PlusIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { NuevoComprobanteForm } from "@/components/comprobantes/nuevo-comprobante-form";
import { Alerta } from "@/components/feedback/alerta";
import { Spinner } from "@/components/feedback/spinner";
import { CabeceraDialogo } from "@/components/patrones/cabecera-dialogo";
import { Dialog, DialogContent, DialogTrigger } from "@/components/ui/dialog";
import { GrupoBotones } from "@/components/ui/grupo-botones";
import { apiRequest } from "@/lib/api/browser";
import type { EmpresaDetalle } from "@/lib/api/empresas";
import type { Serie } from "@/lib/api/series";
import { BOTON_SECUNDARIO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/** Tasas del IGV (#84): la general y la reducida del Padrón de Tasa Especial (Ley 31556). */
const TASA_GENERAL = 18;
const TASA_PADRON = 10.5;

/**
 * Carga un recurso la primera vez que se abre el diálogo, y lo deja reintentable.
 *
 * Tres estados, sin colapsarlos: `null` = todavía no llegó, `"error"` = no se pudo leer, y el dato. Un fallo no se
 * guarda como dato vacío porque "no tienes series" y "no pude leer tus series" mandan al usuario a lugares
 * distintos. `reintentar` vuelve a `null`, que es lo que relanza el efecto —el estado sobrevive a cerrar y reabrir,
 * así que sin reintento un 502 pasajero dejaría el diálogo roto hasta recargar la página.
 *
 * Cada recurso tiene su propio efecto: si compartieran uno, el que falla arrastraría al que no, y el que ya cargó
 * se volvería a pedir en cada reintento del otro.
 */
function useRecursoDelDialogo<T>(abierto: boolean, ruta: string) {
  const [estado, setEstado] = useState<T | "error" | null>(null);

  useEffect(() => {
    if (!abierto || estado !== null) return;
    let vigente = true;
    apiRequest<T>(ruta, { method: "GET" })
      .then((r) => vigente && setEstado(r.estado === "exito" && r.datos ? r.datos : "error"))
      .catch(() => vigente && setEstado("error"));
    return () => {
      vigente = false;
    };
  }, [abierto, estado, ruta]);

  return [estado, () => setEstado(null)] as const;
}

/**
 * Emisión manual desde el portal (#17). El disparador vive en el top bar, como el resto de acciones principales.
 *
 * Se autoabastece de datos (series y empresa) al abrirse, como los demás diálogos del top bar: así no depende de
 * props que la cabecera —que es común a todas las páginas— no tiene de dónde sacar.
 */
export function NuevoComprobanteDialog({ className }: { className?: string }) {
  const [abierto, setAbierto] = useState(false);

  const [series, reintentarSeries] = useRecursoDelDialogo<Serie[]>(abierto, "/api/proxy/series");
  const [empresa, reintentarEmpresa] = useRecursoDelDialogo<EmpresaDetalle>(abierto, "/api/proxy/empresa");

  const datosEmpresa = empresa === "error" ? null : empresa;
  // La tasa de la empresa decide el IGV que se previsualiza. Si no se pudo leer se cae a la general, y eso hay que
  // decirlo: con la reducida los totales previsualizados no serían los del comprobante (ver el aviso de abajo).
  const tasaIgv = datosEmpresa?.padron_tasa_especial_igv ? TASA_PADRON : TASA_GENERAL;

  const ambiente =
    // El detalle lo da el aviso del cuerpo; acá solo se deja de afirmar un ambiente que no se conoce.
    empresa === "error"
      ? "Ambiente sin confirmar"
      : datosEmpresa?.entorno === "PRODUCCION"
        ? "Ambiente: Producción — se emite ante SUNAT"
        : datosEmpresa
          ? "Ambiente: Homologación (beta de SUNAT)"
          : "Cargando ambiente…";

  return (
    // Sin cierre al hacer clic fuera: el formulario tiene datos escritos y un clic al pasar no debe perderlos.
    <Dialog open={abierto} onOpenChange={setAbierto} disablePointerDismissal>
      <DialogTrigger className={className}>
        <PlusIcon className="size-4" />
        Nuevo comprobante
      </DialogTrigger>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 overflow-hidden p-0 sm:max-w-4xl">
        <CabeceraDialogo
          icon={FileTextIcon}
          titulo="Nuevo comprobante"
          descripcion={ambiente}
        />
        <div className="shrink-0 px-5 pt-4">
          <GrupoBotones
            etiqueta="Tipo de comprobante"
            valor="factura"
            opciones={[
              { valor: "factura", etiqueta: "Factura" },
              // Boleta existe como serie (tipo 03) pero `POST /v1/facturas` solo acepta series F###: hasta #20 no
              // hay por dónde emitirla. Se muestra deshabilitada en vez de ocultarla, como pide el design system.
              { valor: "boleta", etiqueta: "Boleta", disabled: true },
            ]}
          />
        </div>
        {series === null ? (
          <div className="flex items-center gap-2 px-5 py-8 text-sm text-muted-foreground">
            <Spinner tamano="sm" />
            Cargando series…
          </div>
        ) : series === "error" ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="error" titulo="No se pudieron cargar tus series">
              Puede ser un problema pasajero de conexión. Tus series y sus correlativos no se tocaron.
            </Alerta>
            <button type="button" className={cn(BOTON_SECUNDARIO, "self-end")} onClick={reintentarSeries}>
              Reintentar
            </button>
          </div>
        ) : (
          <>
            {/* La empresa no bloquea la emisión, pero sí decide la tasa: sin ella se previsualiza con la general, y
                un tenant del padrón vería totales que no son los que va a emitir. Se avisa en vez de callarlo. */}
            {empresa === "error" ? (
              <div className="shrink-0 px-5 pt-4">
                <Alerta
                  tono="aviso"
                  titulo="No se pudo leer la configuración de la empresa"
                  accion={
                    <button type="button" className={BOTON_SECUNDARIO} onClick={reintentarEmpresa}>
                      Reintentar
                    </button>
                  }
                >
                  Se emitirá contra el ambiente que tenga configurado y los totales se previsualizan con IGV {TASA_GENERAL} %: si
                  está en el padrón de tasa especial ({TASA_PADRON} %), no coincidirán con los del comprobante.
                </Alerta>
              </div>
            ) : null}
            <NuevoComprobanteForm series={series} tasaIgv={tasaIgv} onEmitido={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
