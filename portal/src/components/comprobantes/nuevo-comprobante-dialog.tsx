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

/**
 * Emisión manual desde el portal (#17). El disparador vive en el top bar, como el resto de acciones principales.
 *
 * Se autoabastece de datos (series y empresa) al abrirse, como los demás diálogos del top bar: así no depende de
 * props que la cabecera —que es común a todas las páginas— no tiene de dónde sacar.
 */
export function NuevoComprobanteDialog({ className }: { className?: string }) {
  const [abierto, setAbierto] = useState(false);

  // Mismo patrón en los dos: `null` = todavía no llegó; `"error"` = no se pudo leer. Un fallo no se guarda como
  // dato vacío, porque "no tienes series" y "no pude leer tus series" mandan al usuario a lugares distintos —y el
  // estado sobrevive a cerrar y reabrir el diálogo, así que un 502 pasajero dejaría la emisión muerta hasta recargar.
  const [series, setSeries] = useState<Serie[] | "error" | null>(null);
  // Sin empresa no se puede afirmar el ambiente, y decir "Homologación" cuando el tenant está en producción
  // invita a emitir sin cuidado.
  const [empresa, setEmpresa] = useState<EmpresaDetalle | "error" | null>(null);

  useEffect(() => {
    if (!abierto || series !== null) return;
    let vigente = true;
    // Cada llamada se resuelve por su cuenta: si fallara la de empresa, juntarlas en un Promise.all dejaría
    // `series` sin cargar y el diálogo pediría reintentar algo que no está roto. La empresa solo aporta la tasa
    // de IGV y el ambiente, y ambos tienen un default razonable.
    apiRequest<Serie[]>("/api/proxy/series", { method: "GET" })
      .then((r) => vigente && setSeries(r.estado === "exito" && r.datos ? r.datos : "error"))
      .catch(() => vigente && setSeries("error"));
    apiRequest<EmpresaDetalle>("/api/proxy/empresa", { method: "GET" })
      .then((r) => vigente && setEmpresa(r.estado === "exito" && r.datos ? r.datos : "error"))
      .catch(() => vigente && setEmpresa("error"));
    return () => {
      vigente = false;
    };
  }, [abierto, series]);

  const datosEmpresa = empresa === "error" ? null : empresa;
  // La tasa de la empresa decide el IGV que se previsualiza: 10.5 % en el padrón de tasa especial, 18 % si no (#84).
  const tasaIgv = datosEmpresa?.padron_tasa_especial_igv ? 10.5 : 18;

  const ambiente =
    empresa === "error"
      ? "No se pudo leer el ambiente de la empresa — se emitirá igual contra el que tenga configurado"
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
            {/* Volver a `null` relanza el efecto: el reintento no obliga a recargar la página. */}
            <button type="button" className={cn(BOTON_SECUNDARIO, "self-end")} onClick={() => setSeries(null)}>
              Reintentar
            </button>
          </div>
        ) : (
          <NuevoComprobanteForm series={series} tasaIgv={tasaIgv} onEmitido={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        )}
      </DialogContent>
    </Dialog>
  );
}
