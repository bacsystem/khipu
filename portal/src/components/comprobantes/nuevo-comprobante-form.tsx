"use client";

import { PlusIcon, Settings2Icon, Trash2Icon } from "lucide-react";
import { useRouter } from "next/navigation";
import { Fragment, type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from "react";
import { Alerta } from "@/components/feedback/alerta";
import { Campo } from "@/components/formularios/campo";
import { EntradaFecha } from "@/components/formularios/entrada-fecha";
import { EntradaMonto } from "@/components/formularios/entrada-monto";
import { StepperNumerico } from "@/components/formularios/stepper-numerico";
import { BotonAsync } from "@/components/patrones/boton-async";
import { apiRequest } from "@/lib/api/browser";
import type { Serie } from "@/lib/api/series";
import { calcularTotales, TASA_GENERAL, type ItemParaTotales } from "@/lib/comprobantes/totales";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearMonto, hoyLima, sumarDias } from "@/lib/formato";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

/**
 * Las monedas salen del `@Pattern("PEN|USD|EUR")` de `FacturaRequest`, no del catálogo 02: el catálogo lista todas las
 * ISO 4217 y la API rechaza el resto, así que ofrecerlas sería prometer algo que falla al emitir.
 */
const MONEDAS = [
  { codigo: "PEN", etiqueta: "S/ Soles (PEN)" },
  { codigo: "USD", etiqueta: "$ Dólares (USD)" },
  { codigo: "EUR", etiqueta: "€ Euros (EUR)" },
];

/**
 * Afectaciones válidas en una venta interna (`tipo_operacion` 0101). Del catálogo 07 se excluye la 40 (exportación),
 * que exige tipo de operación 0200–0208, y los demás gratuitos que no aplican a una venta corriente.
 */
const AFECTACIONES = [
  { codigo: "10", etiqueta: "Gravado" },
  { codigo: "20", etiqueta: "Exonerado" },
  { codigo: "30", etiqueta: "Inafecto" },
  { codigo: "11", etiqueta: "Gratuito (bonificación)" },
];

type EntradaCatalogo = { codigo: string; descripcion: string };

type Linea = { descripcion: string; cantidad: number | null; precioUnitario: number | null; unidad: string; tipoAfectacionIgv: string };

/** El diálogo usa la escala compacta del design system (h-8, la de la barra de filtros) en vez de la de formulario
 *  (h-10): con cliente, ítems y totales en una sola vista, 8px por control cambian cuántas líneas entran. */
const CAMPO_DENSO = cn(CAMPO, "h-8 text-[13px]");

const LINEA_VACIA: Linea = { descripcion: "", cantidad: 1, precioUnitario: null, unidad: "NIU", tipoAfectacionIgv: "10" };

/**
 * SUNAT recibe la factura hasta el 3.er día calendario contado desde el día siguiente a la emisión; pasado eso
 * rechaza con 2108 y el número queda consumido. Espeja `PlazoEnvio.dias` del dominio (RS 193-2020).
 */
const PLAZO_ENVIO_DIAS = 3;

export function NuevoComprobanteForm({
  series,
  tasaIgv,
  onEmitido,
  onCancelar,
}: {
  series: Serie[];
  /**
   * Tasa vigente de la empresa: 10.5 % si está en el padrón de tasa especial, 18 % si no, y `null` mientras no se
   * sabe. El `null` importa: previsualizar al 18 % a un tenant del padrón muestra importes que no son los que
   * emitirá, y encima cambian solos cuando llega la respuesta.
   */
  tasaIgv: number | null;
  onEmitido?: () => void;
  onCancelar?: () => void;
}) {
  const router = useRouter();
  const seriesFactura = series.filter((s) => s.tipo === "01" && s.activa);

  const [serie, setSerie] = useState(seriesFactura[0]?.serie ?? "");
  // Por render, no a nivel de módulo: el diálogo puede quedar abierto cruzando la medianoche de Lima.
  const hoy = hoyLima();
  const [fecha, setFecha] = useState(hoy);
  const [moneda, setMoneda] = useState("PEN");
  const [numDoc, setNumDoc] = useState("");
  const [razonSocial, setRazonSocial] = useState("");
  const [direccion, setDireccion] = useState("");
  const [lineas, setLineas] = useState<Linea[]>([{ ...LINEA_VACIA }]);
  const [detalle, setDetalle] = useState<number | null>(null);
  const [unidades, setUnidades] = useState<EntradaCatalogo[] | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Tras un error el botón estuvo `disabled` y el navegador soltó el foco a `<body>`: un usuario de teclado perdía
  // su posición. Llevarlo a la alerta lo reubica y, de paso, garantiza que se lea.
  const alertaRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  /**
   * Enter en un campo de texto NO emite. El envío implícito del navegador convierte el reflejo de «Enter para pasar
   * al siguiente campo» en una factura real con correlativo consumido —medido: Enter en el RUC emitía—. Se emite
   * solo desde el botón (clic, o Enter/Espacio con el foco en él). Los selects y botones conservan su Enter.
   */
  function sinEnvioImplicito(e: KeyboardEvent<HTMLFormElement>) {
    if (e.key !== "Enter") return;
    const t = e.target as HTMLElement;
    if (t.tagName === "INPUT" && (t as HTMLInputElement).type !== "submit") e.preventDefault();
  }

  // Unidades de medida del catálogo 03, servido por el backend: no se hardcodean porque la lista cambia con SUNAT.
  useEffect(() => {
    if (detalle === null || unidades !== null) return;
    let vigente = true;
    apiRequest<{ entradas: EntradaCatalogo[] }>("/api/proxy/catalogos/03", { method: "GET" })
      .then((res) => {
        if (vigente) setUnidades(res.estado === "exito" && res.datos ? res.datos.entradas : []);
      })
      .catch(() => vigente && setUnidades([]));
    return () => {
      vigente = false;
    };
  }, [detalle, unidades]);

  // Una sola definición de "línea que cuenta", para previsualizar y para enviar: si difieren, el total que el
  // usuario revisa no es el del comprobante que se emite.
  //
  // El precio también decide: sin él, una línea con solo la descripción escrita (la cantidad ya viene en 1 desde
  // `LINEA_VACIA`) se daba por completa, el aviso de ítem incompleto desaparecía y se emitía una línea de S/ 0.00
  // —que ya no se puede corregir sin nota de crédito—. Las gratuitas se cobran a 0 pero llevan valor referencial,
  // así que tampoco son una excepción: su precio es el valor de referencia y debe estar.
  const lineasCompletas = lineas.filter((l) => l.descripcion.trim() && (l.cantidad ?? 0) > 0 && (l.precioUnitario ?? 0) > 0);
  const paraTotales: ItemParaTotales[] = lineasCompletas.map((l) => ({
    cantidad: l.cantidad ?? 0,
    precioUnitario: l.precioUnitario ?? 0,
    tipoAfectacionIgv: l.tipoAfectacionIgv,
  }));
  // Sin tasa confirmada se previsualiza con la general, pero la etiqueta del pie no la afirma (ver abajo).
  const totales = calcularTotales(paraTotales, tasaIgv ?? TASA_GENERAL);
  // Una línea a medio cargar no se descarta en silencio: se avisa, porque su importe no está en el total de
  // arriba. El texto no nombra la causa: `lineasCompletas` excluye por descripción, por cantidad y por precio.
  const lineasIncompletas = lineas.length - lineasCompletas.length;

  /** `detalle` guarda un índice: al borrar una fila hay que reubicarlo o el panel queda abierto sobre otro ítem. */
  function quitarLinea(i: number) {
    setLineas((p) => (p.length === 1 ? p : p.filter((_, n) => n !== i)));
    setDetalle((abierto) => (abierto === null || abierto === i ? null : abierto > i ? abierto - 1 : abierto));
  }

  function actualizar(i: number, cambio: Partial<Linea>) {
    setLineas((prev) => prev.map((l, n) => (n === i ? { ...l, ...cambio } : l)));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    // `EntradaMonto` muestra el texto crudo mientras tiene el foco y recién al perderlo pinta el valor redondeado
    // que es el que se envía. Soltar el foco antes de emitir hace que lo que el usuario está mirando en ese
    // instante sea exactamente lo que viaja.
    (document.activeElement as HTMLElement | null)?.blur();
    setError(null);
    const items = lineasCompletas.map((l) => ({
      descripcion: l.descripcion.trim(),
      unidad: l.unidad,
      cantidad: l.cantidad,
      precio_unitario: l.precioUnitario ?? 0,
      tipo_afectacion_igv: l.tipoAfectacionIgv,
    }));
    if (items.length === 0) {
      setError("Agrega al menos un ítem con descripción, cantidad y precio.");
      return;
    }

    setEnviando(true);
    let res: Awaited<ReturnType<typeof apiRequest<{ id: string }>>>;
    try {
      res = await apiRequest<{ id: string }>("/api/proxy/facturas", {
        method: "POST",
        body: {
          serie,
          fecha_emision: fecha,
          moneda,
          cliente: { tipo_doc: "6", num_doc: numDoc.trim(), razon_social: razonSocial.trim(), direccion: direccion.trim() || undefined },
          items,
        },
      });
    } catch {
      // `fetch` no resuelve con error: RECHAZA ante un corte de conexión, y sin este catch el `finally` de abajo no
      // existía —el botón quedaba en «Emitiendo…» para siempre, sin alerta—. Y es el peor momento para fallar: el
      // POST pudo haber llegado y consumido correlativo. No se pide reintentar a ciegas; se manda a mirar primero.
      setError(
        "Se cortó la conexión mientras se emitía. La factura pudo haberse emitido igual: revisá el listado de comprobantes antes de volver a intentarlo, para no duplicarla.",
      );
      // El listado de fondo puede tener ya la factura nueva: que se vea sin recargar la página.
      router.refresh();
      return;
    } finally {
      setEnviando(false);
    }

    if (res.estado !== "exito" || !res.datos) {
      // El backend devuelve el detalle de SUNAT en `mensaje`; el código genérico solo dice la familia del error.
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    onEmitido?.();
    router.push(`/comprobantes/${res.datos.id}`);
    router.refresh();
  }

  if (seriesFactura.length === 0) {
    return (
      <div className="flex flex-col gap-3 px-5 py-4">
        <Alerta tono="aviso" titulo="No tienes series de factura">
          Para emitir necesitas al menos una serie de tipo 01 activa.
        </Alerta>
        <button type="button" className={cn(BOTON_SECUNDARIO, "self-end")} onClick={() => router.push("/series")}>
          Ir a series
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} onKeyDown={sinEnvioImplicito} className="flex min-h-0 flex-1 flex-col" data-testid="form-nuevo-comprobante">
      {/* El scroll vive solo en la lista de ítems: así la serie, la fecha y el cliente quedan siempre a la vista
          por largo que sea el comprobante. */}
      <div className="flex min-h-0 flex-1 flex-col gap-4 px-5 py-4">
      <div className="grid items-start gap-4 sm:grid-cols-3">
        <Campo id="nc-serie" etiqueta="Serie" ayuda="Correlativo automático">
          <select id="nc-serie" value={serie} onChange={(e) => setSerie(e.target.value)} className={cn(CAMPO_DENSO, "font-mono")}>
            {seriesFactura.map((s) => (
              <option key={s.serie} value={s.serie}>
                {s.serie} · siguiente N.º {s.ultimo_numero + 1}
              </option>
            ))}
          </select>
        </Campo>

        <Campo id="nc-fecha" etiqueta="Fecha de emisión" ayuda="Máx. 3 días">
          {/* Los dos extremos los rechaza el backend (futura y fuera del plazo de envío, regla 2108), así que el
              calendario los cierra acá en vez de gastar un viaje para que lo diga SUNAT. */}
          {/* `||` y no `??`: un input de fecha vaciado emite `""`, no `null`, así que `??` dejaba pasar el vacío
              hasta el `@NotNull LocalDate` del backend y la emisión moría con un error de deserialización. */}
          <EntradaFecha id="nc-fecha" valor={fecha} onCambio={(v) => setFecha(v || hoy)} min={sumarDias(hoy, -PLAZO_ENVIO_DIAS)} max={hoy} variante="filtro" />
        </Campo>

        <Campo id="nc-moneda" etiqueta="Moneda">
          <select id="nc-moneda" value={moneda} onChange={(e) => setMoneda(e.target.value)} className={CAMPO_DENSO}>
            {MONEDAS.map((m) => (
              <option key={m.codigo} value={m.codigo}>
                {m.etiqueta}
              </option>
            ))}
          </select>
        </Campo>
      </div>

      <section className="grid gap-3">
        <h3 className={ETIQUETA_CAMPO}>Cliente</h3>
        <div className="grid gap-4 sm:grid-cols-[180px_1fr]">
          <Campo id="nc-ruc" etiqueta="RUC" >
            <input
              id="nc-ruc"
              value={numDoc}
              onChange={(e) => setNumDoc(e.target.value.replace(/\D/g, "").slice(0, 11))}
              inputMode="numeric"
              placeholder="20123456786"
              required
              // Lo que exige `Receptor.esRuc` (`\d{11}`): antes solo se recortaba a 11 y un RUC de 5 dígitos
              // viajaba al backend para volver como 422 «2017».
              minLength={11}
              pattern="[0-9]{11}"
              title="RUC de 11 dígitos"
              className={cn(CAMPO_DENSO, "font-mono tabular-nums")}
            />
          </Campo>
          <Campo id="nc-razon" etiqueta="Razón social">
            <input
              id="nc-razon"
              value={razonSocial}
              onChange={(e) => setRazonSocial(e.target.value)}
              placeholder="Comercial Andina SAC"
              required
              // `Receptor` exige de 3 a 1500 caracteres (regla 2022).
              minLength={3}
              maxLength={1500}
              className={CAMPO_DENSO}
            />
          </Campo>
          <div className="sm:col-span-2">
            <Campo id="nc-direccion" etiqueta="Dirección" opcional>
              <input id="nc-direccion" value={direccion} onChange={(e) => setDireccion(e.target.value)} placeholder="Av. Prueba 123" className={CAMPO_DENSO} />
            </Campo>
          </div>
        </div>
      </section>

      <section className="flex min-h-0 flex-1 flex-col gap-3">
        <div className="flex items-baseline justify-between gap-3">
          <h3 className={ETIQUETA_CAMPO}>
            Ítems <span className="font-normal text-muted-foreground">· {lineas.length}</span>
          </h3>
          <button type="button" onClick={() => setLineas((p) => [...p, { ...LINEA_VACIA }])} className="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline">
            <PlusIcon className="size-3.5" />
            Agregar ítem
          </button>
        </div>

        {/* Tabla compacta: encabezados una sola vez y filas de alto reducido; las etiquetas por fila quedan
            solo para lectores de pantalla. */}
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-border">
          <div className="hidden grid-cols-[minmax(0,1fr)_124px_188px_72px] gap-3 border-b border-border/60 bg-muted/40 px-3 py-2 sm:grid">
            <span className={AYUDA_CAMPO}>Descripción</span>
            <span className={AYUDA_CAMPO}>Cantidad</span>
            <span className={AYUDA_CAMPO}>Precio unit. (con IGV)</span>
            <span className="sr-only">Acciones</span>
          </div>

          <div className="min-h-0 flex-1 divide-y divide-border/60 overflow-y-auto">
          {lineas.map((linea, i) => (
            <div key={i} className="px-3 py-2">
              <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_124px_188px_72px] sm:items-center">
                <Campo id={`nc-desc-${i}`} etiqueta="Descripción" className="[&>label]:sr-only">
                  <input
                    id={`nc-desc-${i}`}
                    value={linea.descripcion}
                    onChange={(e) => actualizar(i, { descripcion: e.target.value })}
                    placeholder="Ej. Servicio de consultoría"
                    maxLength={500}
                    className={CAMPO_DENSO}
                  />
                </Campo>
                <Campo id={`nc-cant-${i}`} etiqueta="Cantidad" className="[&>label]:sr-only">
                  {/* `paso="any"`: sin él, el input oculto de base-ui queda en `stepMismatch` con cualquier cantidad no
                      entera y el navegador aborta el submit sin decir nada. Kilos, horas y metros son medio SUNAT. */}
                  <StepperNumerico id={`nc-cant-${i}`} valor={linea.cantidad} onCambio={(v) => actualizar(i, { cantidad: v })} min={0} paso="any" variante="filtro" />
                </Campo>
                <Campo id={`nc-precio-${i}`} etiqueta="Precio unit. (con IGV)" className="[&>label]:sr-only">
                  <EntradaMonto id={`nc-precio-${i}`} valor={linea.precioUnitario} onCambio={(v) => actualizar(i, { precioUnitario: v })} moneda={moneda} variante="filtro" />
                </Campo>
                <div className="flex items-center justify-end gap-0.5">
                  <button
                    type="button"
                    onClick={() => setDetalle(detalle === i ? null : i)}
                    title="Unidad y afectación del IGV"
                    aria-label={`Más opciones del ítem ${i + 1}`}
                    aria-expanded={detalle === i}
                    className={cn(
                      "rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-foreground",
                      detalle === i && "bg-muted text-foreground",
                    )}
                  >
                    <Settings2Icon className="size-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => quitarLinea(i)}
                    disabled={lineas.length === 1}
                    title={lineas.length === 1 ? "Un comprobante necesita al menos un ítem" : "Quitar ítem"}
                    aria-label={`Quitar ítem ${i + 1}`}
                    className="rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-destructive disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <Trash2Icon className="size-4" />
                  </button>
                </div>
              </div>

              {detalle === i ? (
                <div className="mt-3 grid gap-3 rounded-lg bg-muted/40 px-3 py-3 sm:grid-cols-2">
                  <Campo id={`nc-unidad-${i}`} etiqueta="Unidad de medida" ayuda="Catálogo 03">
                    <select id={`nc-unidad-${i}`} value={linea.unidad} onChange={(e) => actualizar(i, { unidad: e.target.value })} className={cn(CAMPO_DENSO, "font-mono")}>
                      {(unidades ?? []).length === 0 ? <option value={linea.unidad}>{linea.unidad}</option> : null}
                      {(unidades ?? []).map((u) => (
                        <option key={u.codigo} value={u.codigo}>
                          {u.codigo} · {u.descripcion}
                        </option>
                      ))}
                    </select>
                  </Campo>
                  <Campo id={`nc-afect-${i}`} etiqueta="Afectación del IGV" ayuda="Catálogo 07">
                    <select
                      id={`nc-afect-${i}`}
                      value={linea.tipoAfectacionIgv}
                      onChange={(e) => actualizar(i, { tipoAfectacionIgv: e.target.value })}
                      className={CAMPO_DENSO}
                    >
                      {AFECTACIONES.map((a) => (
                        <option key={a.codigo} value={a.codigo}>
                          {a.codigo} · {a.etiqueta}
                        </option>
                      ))}
                    </select>
                  </Campo>
                </div>
              ) : null}
            </div>
          ))}
          </div>
        </div>
      </section>

        {error ? (
          <div ref={alertaRef} tabIndex={-1} className="outline-none">
            <Alerta tono="error">{error}</Alerta>
          </div>
        ) : null}
      </div>

      {/* Los totales viven en el pie fijo: con muchos ítems el importe a emitir no debe perderse al scrollear. */}
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-x-6 gap-y-3 border-t border-border/60 bg-muted/40 px-5 py-3" data-testid="totales-previsualizados">
        <div className="flex flex-wrap items-baseline gap-x-5 gap-y-1">
          <dl className={cn(AYUDA_CAMPO, "flex flex-wrap items-baseline gap-x-4 gap-y-0.5")}>
            {[
              { etiqueta: "Gravado", monto: totales.gravado, siempre: true },
              { etiqueta: "Exonerado", monto: totales.exonerado, siempre: false },
              { etiqueta: "Inafecto", monto: totales.inafecto, siempre: false },
              { etiqueta: "Gratuito", monto: totales.gratuito, siempre: false },
              // Mientras la empresa carga no se sabe si es 18 % o la reducida del padrón: se muestra sin tasa en
              // vez de afirmar una que puede cambiarle los importes bajo los ojos un instante después.
              { etiqueta: tasaIgv === null ? "IGV" : `IGV (${tasaIgv} %)`, monto: totales.igv, siempre: true },
            ]
              .filter((f) => f.siempre || f.monto > 0)
              .map((f) => (
                <Fragment key={f.etiqueta}>
                  <dt className="after:content-[':']">{f.etiqueta}</dt>
                  <dd className="tabular-nums">{formatearMonto(moneda, f.monto)}</dd>
                </Fragment>
              ))}
          </dl>
          {/* `role="status"` (región viva): sin él, un usuario con lector de pantalla cargaba tres ítems, dejaba
              uno sin precio y emitía dos líneas creyendo que iban tres. El botón de emitir lo referencia con
              `aria-describedby` para que se lea justo antes de confirmar. */}
          {lineasIncompletas > 0 ? (
            <span id="nc-aviso-incompletos" role="status" className={cn(AYUDA_CAMPO, "text-warning-foreground")}>
              {lineasIncompletas === 1 ? "1 ítem incompleto no se emitirá" : `${lineasIncompletas} ítems incompletos no se emitirán`}
            </span>
          ) : null}
          <div className="flex items-baseline gap-2">
            <span className={cn(ETIQUETA_CAMPO, "text-muted-foreground")}>Total a pagar</span>
            <span className="font-mono text-lg font-semibold tabular-nums" data-testid="total-a-pagar">
              {formatearMonto(moneda, totales.total)}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
        {onCancelar ? (
          <button type="button" onClick={onCancelar} className={BOTON_SECUNDARIO}>
            Cancelar
          </button>
        ) : null}
          <BotonAsync
            type="submit"
            pendiente={enviando}
            className={BOTON_PRIMARIO}
            textoPendiente="Emitiendo…"
            aria-describedby={lineasIncompletas > 0 ? "nc-aviso-incompletos" : undefined}
          >
            Emitir factura
          </BotonAsync>
        </div>
      </div>
    </form>
  );
}
