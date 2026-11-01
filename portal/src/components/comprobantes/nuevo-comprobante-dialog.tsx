"use client";

import { FileTextIcon, PlusIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useMessages } from "next-intl";
import { useEffect, useRef, useState } from "react";
import { NuevoComprobanteForm, tipoInicial, type TipoEmitible } from "@/components/comprobantes/nuevo-comprobante-form";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { Spinner } from "khipu-design-system/feedback/spinner";
import { CabeceraDialogo } from "khipu-design-system/patrones/cabecera-dialogo";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import { GrupoBotones } from "khipu-design-system/ui/grupo-botones";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { apiRequest } from "@/lib/api/browser";
import type { MiCuenta } from "@/lib/api/cuenta";
import type { EmpresaDetalle } from "@/lib/api/empresas";
import type { Comprobante } from "@/lib/api/facturas";
import type { Serie } from "@/lib/api/series";
import { alPedirDuplicado, type Plantilla, plantillaDesde } from "@/lib/comprobantes/duplicar";
import { faltaParaEmitir, type Faltante } from "@/lib/comprobantes/listo-para-emitir";
import { TASA_GENERAL, TASA_PADRON } from "@/lib/comprobantes/totales";
import { enlaceASeccion } from "@/lib/empresa/secciones";
import { ACCION_SECUNDARIA, BOTON_PRIMARIO } from "@/lib/estilos";
import { hoyLima } from "@/lib/formato";
import { cn } from "@/lib/utils";
import { useSoloLectura } from "@/lib/solo-lectura";

const faltantes = (t: Mensajes["comprobantes"]["dialogo"]["faltantes"]): Record<Faltante, string> => ({
  certificado: t.certificado,
  "certificado-vencido": t.certificadoVencido,
  "credenciales-sol": t.credencialesSol,
});

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
  const messages = useMessages();
  const t = messages.comprobantes.dialogo;
  const soloLectura = useSoloLectura();
  const [abierto, setAbierto] = useState(false);
  // Mientras el usuario no elige (`null`), el que la empresa puede emitir (#286, `tipoInicial`). Cada apertura vuelve a ese: como las series, que se
  // recargan, la elección de la vez anterior pudo ser con otra empresa.
  const [tipoElegido, setTipo] = useState<TipoEmitible | null>(null);
  // #323: «Duplicar» (en la tabla y en la ficha) pide abrir este diálogo con la copia de un comprobante. `copias` cambia la `key` del formulario en cada copia.
  const [plantilla, setPlantilla] = useState<Plantilla | null>(null);
  const [copias, setCopias] = useState(0);
  const [falloLaCopia, setFalloLaCopia] = useState(false);
  // #117: Esc y la X descartaban lo escrito sin preguntar (el clic fuera ya estaba protegido). Con datos en el formulario piden confirmación; «Cancelar» es explícito y cierra.
  const [hayDatos, setHayDatos] = useState(false);
  const [confirmandoCierre, setConfirmandoCierre] = useState(false);
  const focoAntesDeConfirmar = useRef<HTMLElement | null>(null);
  // Al cerrarse no queda nada escrito que proteger (el formulario se desmonta), ni siquiera si el último estado fue «con datos».
  useEffect(() => {
    if (!abierto) {
      setHayDatos(false);
      setConfirmandoCierre(false);
    }
  }, [abierto]);
  // Cada pedido de duplicar lleva un número: solo vale la respuesta del último, y abrir el diálogo a mano anula los pendientes (si no, una copia que llega tarde pisaría lo que se está escribiendo).
  const pedido = useRef(0);
  // El suscriptor se registra una vez; lee los textos del idioma vigente por ref (`omitidos` de la copia).
  const messagesRef = useRef(messages);
  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);
  useEffect(
    () =>
      alPedirDuplicado((id) => {
        const mio = ++pedido.current;
        setFalloLaCopia(false);
        apiRequest<Comprobante>(`/api/proxy/facturas/${id}`, { method: "GET" })
          .then((res) => {
            if (mio !== pedido.current) return;
            const copia = res.estado === "exito" && res.datos ? plantillaDesde(res.datos, messagesRef.current) : null;
            setFalloLaCopia(copia === null);
            setPlantilla(copia);
            setTipo(copia?.tipo ?? null);
            setCopias((n) => n + 1);
            setAbierto(true);
          })
          .catch(() => {
            if (mio !== pedido.current) return;
            setFalloLaCopia(true);
            setPlantilla(null);
            setTipo(null);
            setCopias((n) => n + 1);
            setAbierto(true);
          });
      }),
    [],
  );

  const series = useRecursoDelDialogo<Serie[]>(abierto, "/api/proxy/series");
  const tipo = tipoElegido ?? tipoInicial(series.dato ?? []);
  const empresa = useRecursoDelDialogo<EmpresaDetalle>(abierto, "/api/proxy/empresa");
  // #18: en el tope del plan el backend rechaza la emisión; se dice antes. Si la cuenta no se puede leer no se bloquea: el backend tiene la última palabra.
  const cuenta = useRecursoDelDialogo<MiCuenta>(abierto, "/api/proxy/cuenta");
  const consumo = cuenta.dato?.consumo;
  const enElTope = consumo?.maximo !== undefined && consumo.documentos >= consumo.maximo;
  // #285: vencido (pasada la gracia) tampoco emite, quede o no lugar en el tope; en gracia sí.
  const vencido = cuenta.dato?.plan?.estado === "VENCIDA";

  // La tasa de la empresa decide el IGV que se previsualiza, y hasta saberla no se afirma ninguna: `null` mientras
  // carga (el pie muestra "IGV" a secas), la general si la lectura falló (con el aviso de abajo diciéndolo).
  const tasaIgv = empresa.dato ? (empresa.dato.padron_tasa_especial_igv ? TASA_PADRON : TASA_GENERAL) : empresa.error ? TASA_GENERAL : null;
  const faltan = empresa.dato ? faltaParaEmitir(empresa.dato, hoyLima()) : [];

  const ambiente =
    // El detalle lo da el aviso del cuerpo; acá solo se deja de afirmar un ambiente que no se conoce.
    empresa.error
      ? t.ambiente.sinConfirmar
      : empresa.dato?.entorno === "PRODUCCION"
        ? t.ambiente.produccion
        : empresa.dato
          ? t.ambiente.homologacion
          : t.ambiente.cargando;

  return (
    <Dialog
      open={abierto}
      onOpenChange={(a) => {
        if (!a && abierto && hayDatos) {
          // Dónde estaba el foco, para devolvérselo si el usuario sigue editando (la alerta de confirmación toma el foco y, al irse, lo dejaría en el cuerpo).
          focoAntesDeConfirmar.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
          setConfirmandoCierre(true);
          return;
        }
        // Abrir a mano es empezar de cero: la copia solo vive hasta cerrar.
        if (a && !abierto) {
          pedido.current++;
          setTipo(null);
          setPlantilla(null);
          setFalloLaCopia(false);
        }
        setConfirmandoCierre(false);
        setAbierto(a);
      }}
    >
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined} className={className}>
        <PlusIcon weight="bold" className="size-4" />
        {t.titulo}
      </DialogTrigger>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 overflow-hidden p-0 sm:max-w-4xl">
        <CabeceraDialogo
          icon={FileTextIcon}
          titulo={t.titulo}
          descripcion={ambiente}
        />
        {/* C2: si el backend va a rechazar la emisión (sin certificado, vencido o sin credenciales SOL), se dice antes de que el
            usuario llene nada, y se lo lleva a donde se arregla. */}
        {faltan.length > 0 ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="aviso" titulo={t.noPuedeEmitir.titulo}>
              <p>{t.noPuedeEmitir.falta}</p>
              <ul className="mt-1 list-disc pl-5">
                {faltan.map((f) => (
                  <li key={f}>{faltantes(t.faltantes)[f]}</li>
                ))}
              </ul>
            </Alerta>
            {/* #276: a la pestaña de lo primero que falta. */}
            <Link
              href={enlaceASeccion(faltan[0] === "credenciales-sol" ? "sol" : "certificado")}
              onClick={() => setAbierto(false)}
              className={cn(BOTON_PRIMARIO, "self-end")}
            >
              {t.noPuedeEmitir.ir}
            </Link>
          </div>
        ) : vencido && cuenta.dato ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="aviso" titulo={t.vencido.titulo}>
              {interpolar(t.vencido.texto, { plan: cuenta.dato.plan.plan.nombre })}
            </Alerta>
            <Link href="/cuenta/plan" onClick={() => setAbierto(false)} className={cn(BOTON_PRIMARIO, "self-end")}>
              {t.verPlan}
            </Link>
          </div>
        ) : enElTope && cuenta.dato ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="aviso" titulo={t.tope.titulo}>
              {interpolar(t.tope.texto, { plan: cuenta.dato.plan.plan.nombre, maximo: consumo.maximo ?? "" })}
            </Alerta>
            <Link href="/cuenta/plan" onClick={() => setAbierto(false)} className={cn(BOTON_PRIMARIO, "self-end")}>
              {t.verPlan}
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
                  titulo={t.sinEmpresa.titulo}
                  accion={
                    <button type="button" className={ACCION_SECUNDARIA} onClick={empresa.reintentar}>
                      {t.reintentar}
                    </button>
                  }
                >
                  {interpolar(t.sinEmpresa.texto, { general: TASA_GENERAL, padron: TASA_PADRON })}
                </Alerta>
              </div>
            ) : null}
            {/* #107: se puede emitir (se firma y queda guardado). Al emitir se intenta enviar igual, y SUNAT lo va a rechazar mientras las
                credenciales sigan mal; los envíos pendientes se prueban una vez por hora (270-H1/H3). */}
            {empresa.dato?.credenciales_sol_rechazadas ? (
              <div className="shrink-0 px-5 pt-4">
                <Alerta
                  tono="aviso"
                  titulo={t.solRechazadas.titulo}
                  accion={
                    <Link href={enlaceASeccion("sol")} onClick={() => setAbierto(false)} className={ACCION_SECUNDARIA}>
                      {t.solRechazadas.corregir}
                    </Link>
                  }
                >
                  {t.solRechazadas.texto}
                </Alerta>
              </div>
            ) : null}
            {confirmandoCierre ? (
              <div className="shrink-0 px-5 pt-4" data-testid="confirmar-cierre">
                <Alerta
                  tono="aviso"
                  titulo={t.cerrar.titulo}
                  accion={
                    <span className="flex gap-3">
                      <button type="button" className={ACCION_SECUNDARIA} onClick={() => {
                          setConfirmandoCierre(false);
                          focoAntesDeConfirmar.current?.focus();
                        }}
                        autoFocus
                      >
                        {t.cerrar.seguir}
                      </button>
                      <button
                        type="button"
                        className={ACCION_SECUNDARIA}
                        onClick={() => {
                          setConfirmandoCierre(false);
                          setAbierto(false);
                        }}
                      >
                        {t.cerrar.descartar}
                      </button>
                    </span>
                  }
                >
                  {t.cerrar.texto}
                </Alerta>
              </div>
            ) : null}
            {plantilla && plantilla.omitidos.length > 0 ? (
              <div className="shrink-0 px-5 pt-4" data-testid="copia-omitidos">
                <Alerta tono="aviso" titulo={t.copia.titulo}>
                  {interpolar(t.copia.omitidos, { omitidos: plantilla.omitidos.join("; ") })}
                </Alerta>
              </div>
            ) : null}
            {falloLaCopia ? (
              <div className="shrink-0 px-5 pt-4">
                <Alerta tono="aviso" titulo={t.copia.falloTitulo}>
                  {t.copia.falloTexto}
                </Alerta>
              </div>
            ) : null}
            {/* #20: factura o boleta. Las dos se emiten igual; cambian las series que se ofrecen y lo que se pide del comprador. */}
            <div className="shrink-0 px-5 pt-4">
              <GrupoBotones
                etiqueta={t.tipo}
                valor={tipo}
                onCambio={setTipo}
                opciones={[
                  { valor: "factura", etiqueta: messages.documentos.tipos["01"] },
                  { valor: "boleta", etiqueta: messages.documentos.tipos["03"] },
                ]}
              />
            </div>
            {/* `key`: cambiar de tipo es empezar otro comprobante, con su serie y su comprador; lo cargado del otro no aplica. */}
            <NuevoComprobanteForm key={`${tipo}-${copias}`} tipo={tipo} plantilla={plantilla?.tipo === tipo ? plantilla : null} series={series.dato} tasaIgv={tasaIgv} cuentaDetracciones={empresa.dato?.cuenta_detracciones ?? null} onEmitido={() => setAbierto(false)} onCancelar={() => setAbierto(false)} onCambioDatos={setHayDatos} />
          </>
        ) : series.error ? (
          <div className="flex flex-col gap-3 px-5 py-4">
            <Alerta tono="error" titulo={t.series.titulo}>
              {t.series.texto}
            </Alerta>
            <button type="button" className={cn(ACCION_SECUNDARIA, "self-end")} onClick={series.reintentar}>
              {t.reintentar}
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-2 px-5 py-8 text-sm text-muted-foreground">
            <Spinner tamano="sm" />
            {messages.comun.cargando}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
