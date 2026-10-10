"use client";

import { FileTextIcon, PlusIcon } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { NuevoComprobanteForm, type TipoEmitible } from "@/components/comprobantes/nuevo-comprobante-form";
import { Alerta } from "@/components/feedback/alerta";
import { Spinner } from "@/components/feedback/spinner";
import { CabeceraDialogo } from "@/components/patrones/cabecera-dialogo";
import { Dialog, DialogContent, DialogTrigger } from "@/components/ui/dialog";
import { GrupoBotones } from "@/components/ui/grupo-botones";
import { apiRequest } from "@/lib/api/browser";
import type { MiCuenta } from "@/lib/api/cuenta";
import type { EmpresaDetalle } from "@/lib/api/empresas";
import type { Serie } from "@/lib/api/series";
import { faltaParaEmitir, type Faltante } from "@/lib/comprobantes/listo-para-emitir";
import { TASA_GENERAL, TASA_PADRON } from "@/lib/comprobantes/totales";
import { enlaceASeccion } from "@/lib/empresa/secciones";
import { ACCION_SECUNDARIA, BOTON_PRIMARIO } from "@/lib/estilos";
import { hoyLima } from "@/lib/formato";
import { cn } from "@/lib/utils";
import { useSoloLectura } from "@/lib/solo-lectura";

const FALTANTES: Record<Faltante, string> = {
  certificado: "Cargar el certificado digital (.p12 o .pfx) de la empresa.",
  "certificado-vencido": "Renovar el certificado digital: el cargado ya venció.",
  "credenciales-sol": "Guardar el usuario SOL secundario y su clave.",
};

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
  const soloLectura = useSoloLectura();
  const [abierto, setAbierto] = useState(false);
  const [tipo, setTipo] = useState<TipoEmitible>("factura");

  const series = useRecursoDelDialogo<Serie[]>(abierto, "/api/proxy/series");
  const empresa = useRecursoDelDialogo<EmpresaDetalle>(abierto, "/api/proxy/empresa");
  // #18: en el tope del plan el backend rechaza la emisión; se dice antes. Si la cuenta no se puede leer no se bloquea: el backend tiene la última palabra.
  const cuenta = useRecursoDelDialogo<MiCuenta>(abierto, "/api/proxy/cuenta");
  const consumo = cuenta.dato?.consumo;
  const enElTope = consumo?.maximo !== undefined && consumo.documentos >= consumo.maximo;

  // La tasa de la empresa decide el IGV que se previsualiza, y hasta saberla no se afirma ninguna: `null` mientras
  // carga (el pie muestra "IGV" a secas), la general si la lectura falló (con el aviso de abajo diciéndolo).
  const tasaIgv = empresa.dato ? (empresa.dato.padron_tasa_especial_igv ? TASA_PADRON : TASA_GENERAL) : empresa.error ? TASA_GENERAL : null;
  const faltan = empresa.dato ? faltaParaEmitir(empresa.dato, hoyLima()) : [];

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
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined} className={className}>
        <PlusIcon className="size-4" />
        Nuevo comprobante
      </DialogTrigger>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 overflow-hidden p-0 sm:max-w-4xl">
        <CabeceraDialogo
          icon={FileTextIcon}
          titulo="Nuevo comprobante"
          descripcion={ambiente}
        />
        {/* C2: si el backend va a rechazar la emisión (sin certificado, vencido o sin credenciales SOL), se dice antes de que el
            usuario llene nada, y se lo lleva a donde se arregla. */}
        {faltan.length > 0 ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="aviso" titulo="Esta empresa todavía no puede emitir">
              <p>Para firmar y enviar comprobantes a SUNAT falta:</p>
              <ul className="mt-1 list-disc pl-5">
                {faltan.map((f) => (
                  <li key={f}>{FALTANTES[f]}</li>
                ))}
              </ul>
            </Alerta>
            {/* #276: a la pestaña de lo primero que falta. */}
            <Link
              href={enlaceASeccion(faltan[0] === "credenciales-sol" ? "sol" : "certificado")}
              onClick={() => setAbierto(false)}
              className={cn(BOTON_PRIMARIO, "self-end")}
            >
              Ir a Fiscal &amp; certificado
            </Link>
          </div>
        ) : enElTope && cuenta.dato ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="aviso" titulo="Llegaste al tope de documentos del mes">
              Tu plan {cuenta.dato.plan.plan.nombre} permite {consumo.maximo} documentos al mes y ya los usaste. Vuelves a emitir el mes que viene, o antes si
              cambias a un plan más grande.
            </Alerta>
            <Link href="/cuenta/plan" onClick={() => setAbierto(false)} className={cn(BOTON_PRIMARIO, "self-end")}>
              Ver plan y consumo
            </Link>
          </div>
        ) : /* El dato primero: así TypeScript sabe que `series.dato` no es null al pasárselo al formulario. Y la empresa y la cuenta ya
               leídas (o su error): si llegaran después, una sin certificado o en el tope desmontaría el formulario con lo que el usuario ya
               escribió (264-H1). */
        series.dato && (empresa.dato || empresa.error) && (cuenta.dato || cuenta.error) ? (
          <>
            {/* La empresa no bloquea la emisión, pero sí decide la tasa: sin ella se previsualiza con la general, y
                un tenant del padrón vería totales que no son los que va a emitir. Se avisa en vez de callarlo. */}
            {empresa.error ? (
              <div className="shrink-0 px-5 pt-4">
                <Alerta
                  tono="aviso"
                  titulo="No se pudo leer la configuración de la empresa"
                  accion={
                    <button type="button" className={ACCION_SECUNDARIA} onClick={empresa.reintentar}>
                      Reintentar
                    </button>
                  }
                >
                  Se emitirá contra el ambiente que tenga configurado y los totales se previsualizan con IGV {TASA_GENERAL} %: si
                  está en el padrón de tasa especial ({TASA_PADRON} %), no coincidirán con los del comprobante.
                </Alerta>
              </div>
            ) : null}
            {/* #107: se puede emitir (se firma y queda guardado). Al emitir se intenta enviar igual, y SUNAT lo va a rechazar mientras las
                credenciales sigan mal; los envíos pendientes se prueban una vez por hora (270-H1/H3). */}
            {empresa.dato?.credenciales_sol_rechazadas ? (
              <div className="shrink-0 px-5 pt-4">
                <Alerta
                  tono="aviso"
                  titulo="SUNAT rechazó las credenciales SOL de esta empresa"
                  accion={
                    <Link href={enlaceASeccion("sol")} onClick={() => setAbierto(false)} className={ACCION_SECUNDARIA}>
                      Corregirlas
                    </Link>
                  }
                >
                  El comprobante se firma y queda guardado, pero SUNAT no lo va a recibir hasta que las corrijas: entonces sale solo.
                </Alerta>
              </div>
            ) : null}
            {/* #20: factura o boleta. Las dos se emiten igual; cambian las series que se ofrecen y lo que se pide del comprador. */}
            <div className="shrink-0 px-5 pt-4">
              <GrupoBotones
                etiqueta="Tipo de comprobante"
                valor={tipo}
                onCambio={setTipo}
                opciones={[
                  { valor: "factura", etiqueta: "Factura" },
                  { valor: "boleta", etiqueta: "Boleta" },
                ]}
              />
            </div>
            {/* `key`: cambiar de tipo es empezar otro comprobante, con su serie y su comprador; lo cargado del otro no aplica. */}
            <NuevoComprobanteForm key={tipo} tipo={tipo} series={series.dato} tasaIgv={tasaIgv} onEmitido={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
          </>
        ) : series.error ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="error" titulo="No se pudieron cargar tus series">
              Puede ser un problema pasajero de conexión. Tus series y sus correlativos no se tocaron.
            </Alerta>
            <button type="button" className={cn(ACCION_SECUNDARIA, "self-end")} onClick={series.reintentar}>
              Reintentar
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-2 px-5 py-8 text-sm text-muted-foreground">
            <Spinner tamano="sm" />
            Cargando…
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
