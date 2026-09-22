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
 * Carga un recurso mientras el diálogo está abierto, y lo deja reintentable.
 *
 * El dato y el fallo son campos separados, no un valor centinela: un `"error"` metido en el mismo estado deja de
 * distinguirse del dato en cuanto el recurso es de tipo texto. Fallar tampoco se guarda como dato vacío, porque
 * "no tienes series" y "no pude leer tus series" mandan al usuario a lugares distintos.
 *
 * **Se olvida al cerrar**: cada apertura recarga. Cachearlo entre aperturas dejaba el diálogo anunciando el
 * correlativo que la emisión anterior ya consumió, y un 502 pasajero roto hasta recargar la página.
 *
 * Cada recurso tiene su propio efecto: si compartieran uno, el que falla arrastraría al que no, y el que ya cargó
 * se volvería a pedir en cada reintento del otro.
 */
function useRecursoDelDialogo<T>(abierto: boolean, ruta: string) {
  const [dato, setDato] = useState<T | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!abierto) {
      // Estados primitivos a propósito: al reasignar el mismo `null`/`false` React corta el re-render, así que este
      // reset no vuelve a disparar el efecto. Con un objeto de estado (`{ estado: "cargando" }`) cada reset crearía
      // una referencia nueva y el efecto se relanzaría en bucle.
      setDato(null);
      setError(false);
      return;
    }
    if (dato !== null || error) return;
    let vigente = true;
    apiRequest<T>(ruta, { method: "GET" })
      .then((r) => {
        if (!vigente) return;
        if (r.estado === "exito" && r.datos) setDato(r.datos);
        else setError(true);
      })
      .catch(() => vigente && setError(true));
    return () => {
      vigente = false;
    };
  }, [abierto, dato, error, ruta]);

  // Sin `cargando`: es `!dato && !error`, y quien renderiza ya decide por descarte tras mirar los otros dos.
  return { dato, error, reintentar: () => setError(false) };
}

/**
 * Emisión manual desde el portal (#17). El disparador vive en el top bar, como el resto de acciones principales.
 *
 * Se autoabastece de datos (series y empresa) al abrirse, como los demás diálogos del top bar: así no depende de
 * props que la cabecera —que es común a todas las páginas— no tiene de dónde sacar.
 */
export function NuevoComprobanteDialog({ className }: { className?: string }) {
  const [abierto, setAbierto] = useState(false);

  const series = useRecursoDelDialogo<Serie[]>(abierto, "/api/proxy/series");
  const empresa = useRecursoDelDialogo<EmpresaDetalle>(abierto, "/api/proxy/empresa");

  // La tasa de la empresa decide el IGV que se previsualiza. Si no se pudo leer se cae a la general, y eso hay que
  // decirlo: con la reducida los totales previsualizados no serían los del comprobante (ver el aviso de abajo).
  const tasaIgv = empresa.dato?.padron_tasa_especial_igv ? TASA_PADRON : TASA_GENERAL;

  const ambiente =
    // El detalle lo da el aviso del cuerpo; acá solo se deja de afirmar un ambiente que no se conoce.
    empresa.error
      ? "Ambiente sin confirmar"
      : empresa.dato?.entorno === "PRODUCCION"
        ? "Ambiente: Producción — se emite ante SUNAT"
        : empresa.dato
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
        {/* El dato primero: así TypeScript sabe que `series.dato` no es null al pasárselo al formulario. */}
        {series.dato ? (
          <>
            {/* La empresa no bloquea la emisión, pero sí decide la tasa: sin ella se previsualiza con la general, y
                un tenant del padrón vería totales que no son los que va a emitir. Se avisa en vez de callarlo. */}
            {empresa.error ? (
              <div className="shrink-0 px-5 pt-4">
                <Alerta
                  tono="aviso"
                  titulo="No se pudo leer la configuración de la empresa"
                  accion={
                    <button type="button" className={BOTON_SECUNDARIO} onClick={empresa.reintentar}>
                      Reintentar
                    </button>
                  }
                >
                  Se emitirá contra el ambiente que tenga configurado y los totales se previsualizan con IGV {TASA_GENERAL} %: si
                  está en el padrón de tasa especial ({TASA_PADRON} %), no coincidirán con los del comprobante.
                </Alerta>
              </div>
            ) : null}
            <NuevoComprobanteForm series={series.dato} tasaIgv={tasaIgv} onEmitido={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
          </>
        ) : series.error ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="error" titulo="No se pudieron cargar tus series">
              Puede ser un problema pasajero de conexión. Tus series y sus correlativos no se tocaron.
            </Alerta>
            <button type="button" className={cn(BOTON_SECUNDARIO, "self-end")} onClick={series.reintentar}>
              Reintentar
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-2 px-5 py-8 text-sm text-muted-foreground">
            <Spinner tamano="sm" />
            Cargando series…
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
