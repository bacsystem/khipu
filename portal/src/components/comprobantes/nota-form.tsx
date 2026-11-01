"use client";

import { FileMinusIcon, PaperPlaneTiltIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale, useMessages, useTranslations } from "next-intl";
import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest, noSeSabeSiLlego, seVencioLaEspera } from "@/lib/api/browser";
import type { CatalogoSunat } from "@/lib/api/catalogos";
import type { Comprobante } from "@/lib/api/facturas";
import type { Serie } from "@/lib/api/series";
import { EntradaFecha } from "khipu-design-system/formularios/entrada-fecha";
import { guardarAvisosDeEmision } from "@/lib/comprobantes/avisos-de-emision";
import { AJUSTE_VACIO, ajusteAApi, errorDeAjuste, type AjusteForm } from "@/lib/comprobantes/ajustes";
import { fechaMinimaDeNota } from "@/lib/comprobantes/plazo";
import { afectacionPredominante, importeLineaNota, impuestoRedondeaACero, itemParaNota, lineaRedondeaACero, tieneDescuentoQueNoAfectaLaBase, topePorTributo } from "@/lib/comprobantes/notas";
import { redondear } from "@/lib/comprobantes/totales";
import { ACCION_SECUNDARIA, AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearMonto, formatearNumero, hoyLima, sumarDias } from "@/lib/formato";
import { sinEnvioImplicito } from "@/lib/formularios";
import { CABECERA_IDEMPOTENCIA, intentoPara, type Intento } from "@/lib/idempotencia";
import { cn } from "@/lib/utils";
import { AjusteEditor } from "./ajuste-editor";

type Tipo = "07" | "08";
/** Motivos de NC en los que la nota es total y se copian los ítems de la factura; el 13 no mueve importes. */
const MOTIVOS_NC_TOTAL = new Set(["01", "02", "06"]);
/**
 * Motivos de NC que acreditan un importe, no unidades (catálogo 09: descuento global, descuento por ítem, bonificación,
 * disminución en el valor, otros conceptos): una sola línea propia por el monto, como la ND. SUNAT no impone otra
 * estructura (solo 4367 observa 04/05/08 sobre boletas) y la compara con la factura por total (3286) y por tributo (3503).
 */
const MOTIVOS_NC_IMPORTE = new Set(["04", "05", "08", "09", "10"]);
/** Motivos de NC que no se emiten sobre una boleta (#293): 13 (3259) y descuentos o bonificaciones (4367). */
const MOTIVOS_NC_NO_EN_BOLETA = new Set(["13", "04", "05", "08"]);
/** Importes de dinero: hasta 12 enteros y 2 decimales (3250/3253 en cuotas; 2025 en la línea de la ND, «12 enteros y 10 decimales»). */
const DOS_DECIMALES = /^\d{1,12}(\.\d{1,2})?$/;

/**
 * Emite una nota de crédito o débito sobre una factura aceptada (`POST /v1/notas`). La NC puede ser total (copia la
 * factura), parcial (ítems de la factura con la cantidad a devolver) o de corrección de cuotas (13, importe 0); la ND lleva
 * una línea propia (interés, penalidad, aumento). Los motivos salen de los catálogos 09/10 y las series de tipo 07/08.
 */
export function NotaForm({ factura, series }: { factura: Comprobante; series: Serie[] }) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.comprobantes.nota;
  const tr = useTranslations("comprobantes.nota");
  const locale = useLocale();
  const mensajeError = useMensajeError();
  const monto = (valor: number) => formatearMonto(factura.moneda, valor, locale);
  const [tipo, setTipo] = useState<Tipo>("07");
  const [serie, setSerie] = useState("");
  const [motivo, setMotivo] = useState("");
  const [descripcion, setDescripcion] = useState("");
  // #314: fecha de emisión (hoy por defecto; no anterior a la de la factura, 2885), observaciones del PDF y, en una nota parcial, descuento global y cargo propios.
  const hoy = hoyLima();
  const [fecha, setFecha] = useState(hoy);
  const [observaciones, setObservaciones] = useState("");
  const [descuentoGlobal, setDescuentoGlobal] = useState<AjusteForm>(AJUSTE_VACIO);
  const [cargoGlobal, setCargoGlobal] = useState<AjusteForm>(AJUSTE_VACIO);
  // En 0, no en lo facturado: con las cantidades precargadas al total, elegir «09 Disminución en el valor», escribir
  // el sustento y un clic emitía una NC por el 100 % de la factura. Y sobre una factura con descuento global,
  // cargos o anticipos, el 100 % de los ítems supera el total (3286): el usuario elige qué acredita.
  const [cantidades, setCantidades] = useState<number[]>(factura.items.map(() => 0));
  const [nd, setNd] = useState({ descripcion: "", importe: "" });
  const [cuotas, setCuotas] = useState<Array<{ monto: string; vencimiento: string }>>(
    (factura.forma_pago.cuotas ?? []).map((q) => ({ monto: String(q.monto), vencimiento: q.vencimiento })),
  );
  const [catalogos, setCatalogos] = useState<Record<Tipo, CatalogoSunat | null>>({ "07": null, "08": null });
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  // Clave de idempotencia (#355), como en la emisión de facturas (#115): la misma mientras se reintente el mismo contenido, otra si el contenido cambia.
  const intento = useRef<Intento | null>(null);
  // Tras un error el botón se deshabilita y el foco caía a `body`: un lector de pantalla no llegaba al mensaje.
  const alertaRef = useRef<HTMLParagraphElement>(null);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  const [intentoCatalogos, setIntentoCatalogos] = useState(0);
  useEffect(() => {
    let vigente = true;
    const sinMotivos = () => setError(t.errorMotivos);
    Promise.all([apiRequest<CatalogoSunat>("/api/proxy/catalogos/09", { method: "GET" }), apiRequest<CatalogoSunat>("/api/proxy/catalogos/10", { method: "GET" })])
      .then(([nc, ndc]) => {
        if (!vigente) return;
        setCatalogos({ "07": nc.datos, "08": ndc.datos });
        if (!nc.datos || !ndc.datos) sinMotivos();
      })
      // Un 500 ya caía en la rama de arriba; un `fetch` RECHAZADO (red caída) no: el motivo quedaba en
      // «Cargando…» deshabilitado para siempre y la página no decía nada.
      .catch(() => vigente && sinMotivos());
    return () => {
      vigente = false;
    };
  }, [intentoCatalogos, t.errorMotivos]);

  // #294: la nota sobre una boleta sigue su régimen y su serie empieza con B (Anexo 3; el backend responde 1001/SERIE_INVALIDA si no).
  const sobreBoleta = factura.tipo === "03";
  const claseDocumento = sobreBoleta ? "boleta" : "factura";
  const documento = t.documento[claseDocumento];
  const letraSerie = sobreBoleta ? "B" : "F";
  const seriesDelTipo = useMemo(() => series.filter((s) => s.tipo === tipo && s.activa && s.serie.startsWith(letraSerie)), [series, tipo, letraSerie]);
  useEffect(() => {
    setSerie(seriesDelTipo[0]?.serie ?? "");
    setMotivo("");
  }, [seriesDelTipo]);

  const esNc = tipo === "07";
  // Con anticipos regularizados la nota "total" no puede copiar la factura (iría por el bruto, SUNAT compara con el neto): se pide por ítems.
  const conAnticipos = (factura.anticipos?.length ?? 0) > 0;
  // #393: lo mismo con un descuento que no afecta la base (01 de línea, 03 global): la nota no lo admite (3280) y se pide por ítems, por el importe neto.
  const conDescuentoSinBase = tieneDescuentoQueNoAfectaLaBase(factura);
  const esTotal = esNc && MOTIVOS_NC_TOTAL.has(motivo) && !conAnticipos && !conDescuentoSinBase;
  const esCuotas = esNc && motivo === "13";
  const esImporte = esNc && MOTIVOS_NC_IMPORTE.has(motivo);
  const esParcial = esNc && motivo !== "" && !esTotal && !esCuotas && !esImporte;
  // La ND y la NC por importe comparten la línea propia (concepto + importe).
  const lineaPropia = !esNc || esImporte;
  const lineasParciales = factura.items.map((item, idx) => ({ item, cantidad: cantidades[idx] ?? 0 })).filter((x) => x.cantidad > 0);
  const itemsParciales = lineasParciales.map(({ item, cantidad }) => itemParaNota(item, cantidad));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const body: Record<string, unknown> = {
      tipo,
      serie,
      fecha_emision: fecha || hoy,
      documento_afectado: { serie: factura.serie, numero: factura.numero },
      motivo,
      // Un `<input>` de una línea no admite saltos, pero sí un tabulador pegado (una celda de Excel): el backend lo
      // rechaza (2135, `isISOControl`). Cualquier espacio en blanco se normaliza a un espacio antes de viajar.
      descripcion: descripcion.replace(/\s+/g, " ").trim(),
    };
    if (esParcial) {
      body.items = itemsParciales;
      // En una nota el descuento y el cargo siempre afectan la base del IGV (SUNAT no admite lo contrario, 3271/3277).
      const descuento = ajusteAApi({ ...descuentoGlobal, afectaBase: true }, "descuento", messages);
      const cargo = ajusteAApi({ ...cargoGlobal, afectaBase: true }, "cargo", messages);
      if (descuento) body.descuento_global = descuento;
      if (cargo) body.cargos = [cargo];
    }
    if (observaciones.trim() !== "") body.observaciones = observaciones.trim();
    if (esCuotas) {
      // Redondeado a 2: la suma en punto flotante de 10.10 + 20.20 viaja como 30.299999999999997 y el backend la
      // rechaza (3250: hasta 2 decimales; 3319: la suma de cuotas debe ser el pendiente). Los fixtures (61.5+61.5)
      // son exactos en binario, por eso ningún e2e lo veía.
      body.forma_pago = {
        tipo: "credito",
        monto_pendiente: redondear(cuotas.reduce((acc, q) => acc + Number(q.monto || 0), 0), 2),
        cuotas: cuotas.map((q) => ({ monto: redondear(Number(q.monto), 2), vencimiento: q.vencimiento })),
      };
    }
    // La afectación de la línea propia sigue a la factura, no un «10» fijo: sobre una exportación el dominio
    // exige 40 (2642) —con el 10 fijo no se podía emitir ninguna ND sobre exportaciones— y sobre IVAP, 17.
    if (lineaPropia) body.items = [{ descripcion: nd.descripcion.trim(), unidad: "ZZ", cantidad: 1, precio_unitario: Number(nd.importe), tipo_afectacion_igv: afectacionLinea }];
    intento.current = intentoPara(intento.current, JSON.stringify(body));
    setEnviando(true);
    const res = await apiRequest<Comprobante>("/api/proxy/notas", { method: "POST", body, headers: { [CABECERA_IDEMPOTENCIA]: intento.current.clave } });
    // Un corte de conexión no dice si el POST llegó, y pudo haber consumido correlativo. Con la clave de idempotencia (#355) reintentar
    // sin cambiar nada es seguro: si la nota ya se emitió, el backend devuelve la misma. (El cliente ya no lanza: devuelve el sobre de
    // error, así que esto no puede ir en un catch.)
    if (noSeSabeSiLlego(res)) {
      setEnviando(false);
      setError(
        interpolar(t.sinRespuesta.mensaje, {
          causa: seVencioLaEspera(res) ? t.sinRespuesta.tardo : t.sinRespuesta.corte,
          documento,
          numero: `${factura.serie}-${factura.numero}`,
        }),
      );
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setEnviando(false);
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    // A propósito NO se rehabilita el botón en el camino feliz: se desmonta con la navegación. Rehabilitarlo antes
    // de `router.push` dejaba una ventana en la que un segundo clic emitía otra nota (medido: 3 notas con 3 clics).
    // Los avisos de SUNAT (#378) solo vienen en esta respuesta: la ficha de la nota los muestra desde aquí.
    guardarAvisosDeEmision(res.datos.id, res.avisos);
    router.push(`/comprobantes/${res.datos.id}`);
  }

  // Los motivos que dependen de cómo es la factura no se ofrecen si no aplican. Con el catálogo completo, «11
  // Ajustes de exportación» o «12 Ajustes IVAP» sobre una factura interna salían numeradas y SUNAT las rechazaba
  // (2642/3107) con el correlativo consumido; y «13 corrección de cuotas» sobre una factura al contado viola 3260.
  const esExportacion = /^020[0-8]$/.test(factura.tipo_operacion ?? "");
  const esIvap = factura.items.some((i) => i.tipo_afectacion_igv === "17");
  const alCredito = factura.forma_pago.tipo === "credito";
  // ND 13 «Penalidades»: SUNAT las declara operaciones INAFECTAS (regla 3507, tres variantes en la hoja
  // NotaDebito2_0: con IGV/IVAP, con 9995/9997 o con tributo 1000/1016 → ERROR). La única afectación que pasa es
  // 30. Antes salía gravada (10, o 40 en exportación): numerada, firmada y rechazada, con el correlativo consumido.
  const esPenalidad = !esNc && motivo === "13";
  // La NC por importe descuenta lo que la factura cobró: si tiene alguna línea gravada va con IGV (10); si toda la
  // factura es exonerada/inafecta, con esa afectación (3503 compara por tributo). La ND de intereses va gravada.
  const afectacionLinea = esPenalidad ? "30" : esExportacion ? "40" : esIvap ? "17" : esImporte ? afectacionPredominante(factura.items) : "10";
  // La etiqueta dice qué impuesto lleva el importe tecleado: «con IGV» mentía en exportación (40, sin IGV) e IVAP (17, 4 %).
  const etiquetaImporte = (t.etiquetaImporte as Record<string, string>)[afectacionLinea];

  // NC 13: lo que exige SUNAT (3253 monto > 0, 3321 vencimiento posterior a la factura, 3320 neto ≤ total) antes
  // «listo» solo pedía que hubiera al menos una cuota, y una cuota en blanco viajaba como monto 0 y fecha vacía.
  const cuotasValidas =
    cuotas.length > 0 &&
    // Hasta 2 decimales por cuota (3253: «12 enteros y hasta 2 decimales»): `10.123` pasaba el formulario y el
    // backend lo rechazaba después. `step=0.01` no frena lo tipeado, solo las flechas.
    cuotas.every((q) => Number(q.monto) > 0 && DOS_DECIMALES.test(q.monto.trim()) && q.vencimiento !== "" && q.vencimiento > factura.fecha_emision) &&
    redondear(cuotas.reduce((acc, q) => acc + Number(q.monto), 0), 2) <= factura.totales.total;
  const motivos = (catalogos[tipo]?.entradas ?? []).filter((m) => {
    // Sobre una boleta: la NC 13 corrige cuotas de una factura al crédito (3259) y la NC por descuento o bonificación (04, 05,
    // 08) va en la misma boleta cuando el comprador es consumidor final (RCP art. 10 num. 1.4; SUNAT observa 4367). El backend
    // rechaza las cuatro antes de numerar (`Nota.validarMotivoPara`).
    if (sobreBoleta && esNc && MOTIVOS_NC_NO_EN_BOLETA.has(m.codigo)) return false;
    if (m.codigo === "11") return esExportacion;
    if (m.codigo === "12") return esIvap;
    // ND 13 sobre una exportación no tiene salida: la penalidad va inafecta (3507) y el dominio exige 40 en toda
    // línea de una exportación (2642). Se ofrecía igual y el mock la daba por buena.
    if (m.codigo === "13") return esNc ? alCredito : !esExportacion;
    // Factura IVAP: la línea sale con 17 y SUNAT exige el motivo 12 en la NC (NotaCredito2_0 fila 223) y en la ND
    // (NotaDebito2_0 fila 206): 3230. Solo escapa el 13 (NC: línea gravada de importe 0; ND: penalidad con 30). La
    // primera corrección lo aplicó solo a la ND; la NC 01/07 seguía saliendo numerada y rechazada.
    if (esIvap) return false;
    return true;
  });

  // Importe de la nota y su tope. El tope es lo que SUNAT compara (3286): el total de la factura, menos lo que ya
  // acreditaron otras NC vigentes (el backend lo suma con lock de fila; acá se anticipa para no gastar un viaje ni
  // consumir número).
  //
  // El importe es Σ de lo que paga el cliente por cada línea (`importeLineaNota`: el `precio_venta` del backend con
  // la cantidad facturada; en proporción, con los ajustes que viajan, con menos), válido para CUALQUIER afectación.
  // La primera versión pasaba por `calcularTotales`, que solo entiende 10/20/30 y devuelve 0 para 40 (exportación) y
  // 17 (IVAP): el tope era inerte y el usuario leía «$ 0.00» antes de emitir. La segunda hacía precio × cantidad y no
  // veía los cargos de línea. Vale también para la nota total (01/02/06): copia la factura entera, y si ya hay NC
  // vigentes la supera.
  //
  // La nota total copia ítems, descuento global, cargos y —desde #123— también el redondeo del importe total, así
  // que sale exactamente por el PayableAmount de la factura (3286 sin tolerancia en facturas).
  const importeNota = esParcial
    ? redondear(lineasParciales.reduce((s, { item, cantidad }) => s + importeLineaNota(item, cantidad), 0), 2)
    : esTotal
      ? factura.totales.total
      : esImporte
        ? redondear(Number(nd.importe) || 0, 2)
        : 0;
  const acreditado = redondear(
    (factura.notas ?? [])
      .filter((n) => n.tipo === "07" && n.estado_documento !== "RECHAZADO" && n.estado_documento !== "INVALIDO" && n.estado_documento !== "ANULADO")
      .reduce((s, n) => s + n.total, 0),
    2,
  );
  // En la NC por importe el límite real puede ser el del tributo de la línea (3503), no el total (3286): en una
  // factura con ISC, anticipos o mixta el total queda por encima del gravado + IGV, y el tope mostrado invitaba al 422.
  const topeTributo = esImporte ? topePorTributo(factura.totales, afectacionLinea) : factura.totales.total;
  const limitaTributo = esImporte && topeTributo < factura.totales.total;
  const tope = redondear(Math.min(factura.totales.total, topeTributo) - acreditado, 2);
  // El motivo 10 «Otros conceptos» está exento de los ocho límites: NotaCredito2_0 fila 111 (3286) y filas 114–122
  // (3503) empiezan todas con «diferente de '10'». El backend lo exime igual desde #141.
  const exentoDeTope = esNc && motivo === "10";
  // Con descuento o cargo propios el importe de la nota cambia y lo calcula el backend: aquí no se compara con el tope (lo hace SUNAT, 3286, y el backend responde 422).
  // Un cargo solo puede subir el importe: si ya pasa el tope sin él, pasa con él, y se sigue bloqueando. Un descuento sí lo baja, y entonces lo calcula el backend.
  const conDescuentoPropio = esParcial && descuentoGlobal.valor.trim() !== "";
  const conAjustesPropios = conDescuentoPropio || (esParcial && cargoGlobal.valor.trim() !== "");
  const superaTope = (esParcial || esTotal || esImporte) && !exentoDeTope && !conDescuentoPropio && importeNota > tope;
  // Descuento y cargo globales solo caben donde el backend los acepta (`Totales`): hay líneas gravadas y ninguna lleva otros tributos (9999).
  const admiteAjustesPropios = factura.items.some((i) => i.tipo_afectacion_igv === "10") && !factura.items.some((i) => !!i.otros_tributos);
  const hayAjusteInvalido = esParcial && (errorDeAjuste(descuentoGlobal, "descuento", messages) !== null || errorDeAjuste(cargoGlobal, "cargo", messages) !== null);
  const fechaValida = (fecha || hoy) >= factura.fecha_emision && (fecha || hoy) <= hoy && (fecha || hoy) >= fechaMinimaDeNota({ tipo, sobreBoleta, motivo }, hoy);
  const conceptoTributo = (t.conceptoTributo as Record<string, string>)[afectacionLinea];
  // 3111: en una línea IVAP un importe de 0.07 a 0.12 deja el impuesto en 0.00 y SUNAT rechaza ya numerada.
  const ivapEnCero = lineaPropia && impuestoRedondeaACero(afectacionLinea, Number(nd.importe) || 0);

  // Por qué el botón está deshabilitado cuando el campo se ve lleno: un importe con 3 decimales no avisaba nada.
  const conDecimalesDeMas = (v: string) => v.trim() !== "" && !DOS_DECIMALES.test(v.trim());
  const lineaEnCero = esParcial && lineasParciales.some(({ item, cantidad }) => lineaRedondeaACero(item, cantidad));
  // 2892: la cantidad de bolsas del ICBPER es un entero; el backend rechaza una devolución de 2.5 bolsas antes de numerar, así que aquí no se deja emitir.
  const bolsasFraccionarias = esParcial && lineasParciales.some(({ item, cantidad }) => !!item.icbper && !Number.isInteger(cantidad));
  // Lo que SUNAT prohíbe no es una línea gratuita dentro de la nota, sino que el importe TOTAL sea 0 (f401, 2062): una NC
  // solo de bonificaciones no acredita nada. Una gratuita sí puede acompañar a una onerosa.
  const notaSinImporte = esParcial && itemsParciales.length > 0 && importeNota < 0.01;
  const avisoDecimales = lineaPropia && conDecimalesDeMas(nd.importe)
    ? t.avisos.importeDecimales
    : esCuotas && cuotas.some((q) => conDecimalesDeMas(q.monto))
      ? t.avisos.cuotaDecimales
      : lineaEnCero
        ? t.avisos.lineaEnCero
        : bolsasFraccionarias
          ? t.avisos.bolsas
        : notaSinImporte
          ? t.avisos.sinImporte
        : ivapEnCero
          ? t.avisos.ivapEnCero
        : lineaPropia && nd.descripcion.trim() !== "" && nd.descripcion.trim().length < 3
          ? t.avisos.conceptoCorto
          : esParcial && descripcion.trim() !== "" && lineasParciales.length === 0
            ? t.avisos.sinCantidad
            : null;

  const listo =
    serie !== "" &&
    motivo !== "" &&
    descripcion.trim() !== "" &&
    (!esParcial || (itemsParciales.length > 0 && !superaTope && !lineaEnCero && !bolsasFraccionarias && !notaSinImporte)) &&
    (!esTotal || !superaTope) &&
    (!esImporte || !superaTope) &&
    !ivapEnCero &&
    // Importe de la línea propia con hasta 12 enteros y 2 decimales (2025); concepto de 3 a 500 (4084 observa con menos de 3).
    (!lineaPropia || (nd.descripcion.trim().length >= 3 && Number(nd.importe) > 0 && DOS_DECIMALES.test(nd.importe.trim()))) &&
    (!esCuotas || cuotasValidas) &&
    !hayAjusteInvalido &&
    fechaValida &&
    observaciones.length <= 1000;

  return (
    <form onSubmit={onSubmit} onKeyDown={sinEnvioImplicito} className="space-y-5" data-testid="nota-form">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nota-tipo" className={ETIQUETA_CAMPO}>{t.tipoNota}</label>
          <select id="nota-tipo" value={tipo} onChange={(e) => setTipo(e.target.value as Tipo)} className={CAMPO}>
            <option value="07">{t.tipoNC}</option>
            <option value="08">{t.tipoND}</option>
          </select>
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nota-serie" className={ETIQUETA_CAMPO}>{t.serie}</label>
          {seriesDelTipo.length ? (
            <select id="nota-serie" value={serie} onChange={(e) => setSerie(e.target.value)} className={cn(CAMPO, "font-mono")}>
              {seriesDelTipo.map((s) => (
                <option key={s.serie} value={s.serie}>{s.serie}</option>
              ))}
            </select>
          ) : (
            <p className="text-[12px] leading-relaxed text-destructive">
              {interpolar(t.sinSeries, { nota: esNc ? t.notaCredito : t.notaDebito, documentos: t.documentoPlural[claseDocumento] })}{" "}
              <Link href="/series" className="underline">{t.creeUna}</Link>
              {interpolar(t.sinSeriesEjemplo, { ejemplo: `${letraSerie}${esNc ? "C01" : "D01"}` })}
            </p>
          )}
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nota-motivo" className={ETIQUETA_CAMPO}>{interpolar(t.motivo, { catalogo: esNc ? "09" : "10" })}</label>
          <select id="nota-motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} className={CAMPO} disabled={!catalogos[tipo]}>
            <option value="">{catalogos[tipo] ? t.seleccione : messages.comun.cargando}</option>
            {motivos.map((m) => (
              <option key={m.codigo} value={m.codigo}>{m.codigo} · {m.descripcion.length > 70 ? `${m.descripcion.slice(0, 70)}…` : m.descripcion}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="nota-descripcion" className={ETIQUETA_CAMPO}>{t.sustento}</label>
        <input id="nota-descripcion" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} maxLength={500} placeholder={t.sustentoPlaceholder} className={CAMPO} />
        <span className={AYUDA_CAMPO}>{t.sustentoAyuda}</span>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nota-fecha" className={ETIQUETA_CAMPO}>{t.fecha}</label>
          <EntradaFecha id="nota-fecha" valor={fecha} onCambio={(v) => setFecha(v || hoy)} min={[factura.fecha_emision, fechaMinimaDeNota({ tipo, sobreBoleta, motivo }, hoy)].sort().at(-1)} max={hoy} variante="filtro" />
          <span className={AYUDA_CAMPO}>{interpolar(t.fechaAyuda, { documento, fecha: factura.fecha_emision })}</span>
        </div>
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="nota-observaciones" className={ETIQUETA_CAMPO}>{t.observaciones} <span className="font-normal normal-case text-muted-foreground">{t.opcional}</span></label>
          <textarea id="nota-observaciones" value={observaciones} onChange={(e) => setObservaciones(e.target.value)} rows={2} maxLength={1000} className={CAMPO} />
          <span className={AYUDA_CAMPO}>{interpolar(t.observacionesAyuda, { n: observaciones.length })}</span>
        </div>
      </div>

      {esParcial && admiteAjustesPropios ? (
        <details className="rounded-lg border border-border px-3 py-2" data-testid="nota-ajustes" open={descuentoGlobal.valor !== "" || cargoGlobal.valor !== ""}>
          <summary className="cursor-pointer text-[13px] font-medium">{t.ajustes.titulo}</summary>
          <div className="mt-3 grid gap-4 sm:grid-cols-2">
            <AjusteEditor id="nota-desc-global" etiqueta={t.ajustes.descuento} clase="descuento" valor={descuentoGlobal} onCambio={setDescuentoGlobal} sinOpcionDeBase />
            <AjusteEditor id="nota-cargo-global" etiqueta={t.ajustes.cargo} clase="cargo" valor={cargoGlobal} onCambio={setCargoGlobal} sinOpcionDeBase />
          </div>
          <p className={cn(AYUDA_CAMPO, "mt-2")}>{interpolar(t.ajustes.ayuda, { documento })}</p>
        </details>
      ) : null}

      {esTotal ? (
        <div className={cn("rounded-lg border px-3 py-2 text-[12px] leading-relaxed", superaTope ? "border-destructive/50 bg-destructive/5 text-destructive" : "border-border bg-muted text-muted-foreground")} data-testid="nota-total">
          <p>
            {tr.rich("totalCopia", {
              b: (c) => <strong className="text-foreground">{c}</strong>,
              documento,
              numero: `${factura.serie}-${factura.numero}`,
              monto: monto(factura.totales.total),
            })}
          </p>
          {/* Una nota total sobre una factura ya acreditada por otras NC vigentes la supera (3286): es el escenario de
              la doble acreditación (#83), y antes esta pantalla no decía nada y dejaba emitir. */}
          {acreditado > 0 || superaTope ? (
            <p className="mt-1" data-testid="nota-importe">
              {t.importe}: <span className="font-mono tabular-nums">{monto(importeNota)}</span>. {t.tope}: <span className="font-mono tabular-nums">{monto(tope)}</span>
              {acreditado > 0 ? interpolar(t.yaAcreditadoOtras, { monto: monto(acreditado) }) : ""}.
              {superaTope ? <span role="alert" className="block">{interpolar(t.superaTopeTotal, { tope: monto(tope) })}</span> : null}
            </p>
          ) : null}
        </div>
      ) : null}

      {esParcial ? (
        <div className="overflow-hidden rounded-lg border border-border">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-border/70 bg-muted font-mono text-[11px] tracking-wider text-muted-foreground uppercase">
                <th className="px-3 py-2 font-medium">{interpolar(t.thItem, { documento })}</th>
                <th className="w-24 px-3 py-2 text-right font-medium">{t.thFacturado}</th>
                <th className="w-32 px-3 py-2 text-right font-medium">{t.thCantidad}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {factura.items.map((item, idx) => (
                <tr key={idx}>
                  <td className="px-3 py-2 text-foreground">{item.descripcion}</td>
                  <td className="px-3 py-2 text-right font-mono tabular-nums text-muted-foreground">{formatearNumero(item.cantidad, locale)} {item.unidad}</td>
                  <td className="px-3 py-2 text-right">
                    <input
                      type="number"
                      min={0}
                      max={Number(item.cantidad)}
                      step="any"
                      value={cantidades[idx] ?? 0}
                      aria-label={interpolar(t.ariaCantidad, { item: item.descripcion })}
                      // Hasta 10 decimales (2025): con 11 el backend rechazaba después. Y entre 0 y lo facturado.
                      onChange={(e) => setCantidades((c) => c.map((v, i) => (i === idx ? Math.min(Number(item.cantidad), Math.max(0, Number(Number(e.target.value).toFixed(10)))) : v)))}
                      className={cn(CAMPO, "w-28 text-right font-mono")}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className={cn(AYUDA_CAMPO, "border-t border-border/60 px-3 py-2")}>
            {conAnticipos && MOTIVOS_NC_TOTAL.has(motivo)
              ? factura.totales.total > 0
                ? interpolar(t.anticiposNeto, { monto: monto(factura.totales.total) })
                : interpolar(t.anticiposSinNota, { monto: monto(factura.totales.total) })
              : ""}
            {conDescuentoSinBase && MOTIVOS_NC_TOTAL.has(motivo)
              ? t.descuentoSinBase
              : ""}
            {t.ayudaParcial}
          </p>
          {/* El importe que va a salir, contra lo que SUNAT compara. Antes el formulario no mostraba ninguna cifra
              de la nota y el usuario descubría el 3286 después de emitir. */}
          <div className={cn("flex flex-wrap items-baseline justify-between gap-2 border-t border-border/60 px-3 py-2 text-[12px]", superaTope ? "text-destructive" : "text-muted-foreground")} data-testid="nota-importe">
            <span>
              {t.importe}{conAjustesPropios ? t.importeAntesDeAjustes : ""}: <strong className="font-mono tabular-nums">{monto(importeNota)}</strong>
            </span>
            <span>
              {t.tope}: <span className="font-mono tabular-nums">{monto(tope)}</span>
              {acreditado > 0 ? interpolar(t.topeMenos, { documento, total: monto(factura.totales.total), acreditado: monto(acreditado) }) : ""}
            </span>
            {superaTope ? (
              <span role="alert" className="basis-full">
                {t.superaTopeParcial}
              </span>
            ) : null}
          </div>
        </div>
      ) : null}

      {esCuotas ? (
        <div className="space-y-2 rounded-lg border border-border p-3">
          <p className="text-[12px] text-muted-foreground">{t.cuotasAyuda}</p>
          {cuotas.map((q, i) => (
            <div key={i} className="flex flex-wrap items-center gap-2">
              <span className="w-20 font-mono text-[11px] text-muted-foreground">{interpolar(t.cuotaId, { n: String(i + 1).padStart(3, "0") })}</span>
              <input type="number" min={0.01} step="0.01" value={q.monto} aria-label={interpolar(t.montoCuota, { n: i + 1 })} onChange={(e) => setCuotas((cs) => cs.map((c, j) => (j === i ? { ...c, monto: e.target.value } : c)))} className={cn(CAMPO, "w-32 font-mono")} />
              <input type="date" value={q.vencimiento} min={sumarDias(factura.fecha_emision, 1)} aria-label={interpolar(t.vencimientoCuota, { n: i + 1 })} onChange={(e) => setCuotas((cs) => cs.map((c, j) => (j === i ? { ...c, vencimiento: e.target.value } : c)))} className={cn(CAMPO, "w-40 font-mono")} />
              <button type="button" aria-label={interpolar(t.quitarCuota, { n: i + 1 })} onClick={() => setCuotas((cs) => cs.filter((_, j) => j !== i))} className="text-[12px] text-muted-foreground hover:text-destructive">{t.quitar}</button>
            </div>
          ))}
          <button type="button" onClick={() => setCuotas((cs) => [...cs, { monto: "", vencimiento: "" }])} className={ACCION_SECUNDARIA}>{t.anadirCuota}</button>
        </div>
      ) : null}

      {lineaPropia ? (
        <div className="space-y-2">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-[1fr_180px]">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="nd-descripcion" className={ETIQUETA_CAMPO}>{t.concepto}</label>
              {/* Hasta 500 como el sustento (2027, `Item`): la recert #4 midió 501 caracteres viajando y un 422 evitable. */}
              <input id="nd-descripcion" value={nd.descripcion} onChange={(e) => setNd({ ...nd, descripcion: e.target.value })} maxLength={500} placeholder={esNc ? t.placeholderNc : t.placeholderNd} className={CAMPO} />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="nd-importe" className={ETIQUETA_CAMPO}>{etiquetaImporte} ({factura.moneda})</label>
              <input id="nd-importe" type="number" min={0.01} step="0.01" value={nd.importe} onChange={(e) => setNd({ ...nd, importe: e.target.value })} className={cn(CAMPO, "font-mono")} />
            </div>
          </div>
          {esImporte ? (
            <div className={cn("flex flex-wrap items-baseline justify-between gap-2 rounded-lg border border-border px-3 py-2 text-[12px]", superaTope ? "text-destructive" : "text-muted-foreground")} data-testid="nota-importe">
              <span>
                {t.importe}: <strong className="font-mono tabular-nums">{monto(importeNota)}</strong>
              </span>
              <span>
                {exentoDeTope ? t.sinTope : <>{t.tope}: <span className="font-mono tabular-nums">{monto(tope)}</span>{limitaTributo ? interpolar(t.topePorTributo, { concepto: conceptoTributo, documento, monto: monto(topeTributo) }) : ""}{acreditado > 0 ? interpolar(t.topeMenosAcreditado, { monto: monto(acreditado) }) : ""}</>}
              </span>
              {superaTope ? <span role="alert" className="basis-full">{interpolar(t.superaTopeImporte, { regla: limitaTributo ? t.reglaTributo : "3286" })}</span> : null}
            </div>
          ) : null}
        </div>
      ) : null}

      {error ? (
        <div className="flex flex-wrap items-center gap-3">
          <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">{error}</p>
          {/* Si falló UNO de los dos catálogos, la otra pestaña funciona pero la alerta queda pegada: el botón tiene
              que seguir ahí para recargar el que falta, no solo cuando falta el de la pestaña actual. */}
          {!catalogos["07"] || !catalogos["08"] ? (
            <button type="button" onClick={() => { setError(null); setIntentoCatalogos((n) => n + 1); }} className={ACCION_SECUNDARIA}>
              {t.reintentar}
            </button>
          ) : null}
        </div>
      ) : null}

      {avisoDecimales ? (
        <p id="nota-aviso-decimales" role="status" className={cn(AYUDA_CAMPO, "text-warning-foreground")}>{avisoDecimales}</p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <button type="submit" disabled={!listo || enviando} aria-describedby={avisoDecimales ? "nota-aviso-decimales" : undefined} className={BOTON_PRIMARIO}>
          {enviando ? <PaperPlaneTiltIcon className="size-4 animate-pulse" /> : <FileMinusIcon className="size-4" />}
          {enviando ? t.emitiendo : interpolar(t.emitir, { nota: esNc ? t.notaCredito : t.notaDebito })}
        </button>
        <Link href={`/comprobantes/${factura.id}`} className={BOTON_SECUNDARIO}>{messages.comun.cancelar}</Link>
        {/* El cambio de texto del botón no se anuncia; esto sí. */}
        <span role="status" className="sr-only">{enviando ? t.emitiendo : ""}</span>
      </div>
    </form>
  );
}
