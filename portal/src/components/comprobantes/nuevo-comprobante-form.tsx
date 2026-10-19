"use client";

import { PlusIcon, Settings2Icon, Trash2Icon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Fragment, type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Alerta } from "@/components/feedback/alerta";
import { Campo } from "@/components/formularios/campo";
import { EntradaFecha } from "@/components/formularios/entrada-fecha";
import { EntradaMonto } from "@/components/formularios/entrada-monto";
import { StepperNumerico } from "@/components/formularios/stepper-numerico";
import { BotonAsync } from "@/components/patrones/boton-async";
import { apiRequest, noSeSabeSiLlego, seVencioLaEspera } from "@/lib/api/browser";
import type { Serie } from "@/lib/api/series";
import { DOCUMENTOS_BOLETA } from "@/lib/comprobantes/documentos";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { BuscadorDeClientes } from "./buscador-de-clientes";
import { guardarAvisosDeEmision } from "@/lib/comprobantes/avisos-de-emision";
import { MAX_LINEAS } from "@/lib/comprobantes/limites";
import { fechaMinimaDeEmision, PLAZO_ENVIO, soloPorResumen } from "@/lib/comprobantes/plazo";
import { anticiposAApi, errorDeAnticipos, type AnticipoForm } from "@/lib/comprobantes/anticipos";
import { AJUSTE_VACIO, ajusteAApi, errorDeAjuste, type AjusteForm } from "@/lib/comprobantes/ajustes";
import { CREDITO_VACIO, errorDeCredito, formaPagoAApi, type CreditoForm } from "@/lib/comprobantes/credito";
import { documentosDeExportacion, errorDeExportacion, esExportacion, EXIGE_PAIS_DE_USO, INCOTERMS, OPERACIONES } from "@/lib/comprobantes/operaciones";
import { listarPaises } from "@/lib/comprobantes/paises";
import { avisoDeDetraccion, DETRACCION_VACIA, detraccionAApi, errorDeDetraccion, operacionDeDetraccion, type DetraccionForm } from "@/lib/comprobantes/detraccion";
import { errorDePercepcion, OPERACION_PERCEPCION, PERCEPCION_VACIA, percepcionAApi, type PercepcionForm } from "@/lib/comprobantes/percepcion";
import { errorDeTipoCambio, MONEDAS, superaElTopeSinDocumento as superaElTope, tipoCambioAApi } from "@/lib/comprobantes/monedas";
import type { Plantilla } from "@/lib/comprobantes/duplicar";
import { redondeoDe } from "@/lib/comprobantes/redondeo";
import { errorDeRetencion, RETENCION_VACIA, retencionAApi, type RetencionForm } from "@/lib/comprobantes/retencion";
import { errorDeReferencias, REFERENCIAS_VACIAS, referenciasAApi, type ReferenciasForm } from "@/lib/comprobantes/referencias";
import { errorDeIsc, GRUPOS_AFECTACION, incompatibilidadDeTributos, ISC_VACIO, iscAApi, type IscForm } from "@/lib/comprobantes/tributos";
import { useCalculo } from "@/lib/comprobantes/use-calculo";
import { calcularTotales, TASA_GENERAL, type ItemParaTotales } from "@/lib/comprobantes/totales";
import { ACCION_SECUNDARIA, AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearMonto, hoyLima } from "@/lib/formato";
import { sinEnvioImplicito } from "@/lib/formularios";
import { CABECERA_IDEMPOTENCIA, intentoPara, type Intento } from "@/lib/idempotencia";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";
import { AjusteEditor } from "./ajuste-editor";
import { AnticiposEditor } from "./anticipos-editor";
import { CreditoEditor } from "./credito-editor";
import { DetraccionEditor } from "./detraccion-editor";
import { PercepcionEditor } from "./percepcion-editor";
import { ReferenciasEditor } from "./referencias-editor";
import { RetencionEditor } from "./retencion-editor";
import { EstadoConsultaRuc } from "./estado-consulta-ruc";
import { useAnticiposDisponibles } from "./use-anticipos-disponibles";
import { useConsultaRuc } from "./use-consulta-ruc";



type EntradaCatalogo = { codigo: string; descripcion: string; extra?: Record<string, string> };

/**
 * #308: el catálogo 03 es la rec 20 completa (más de 2000 códigos, contra los que el backend valida); el selector ofrece las
 * que el backend marca «Más usada». Si ninguna viene marcada, la lista tal cual.
 */
function unidadesDelSelector(entradas: EntradaCatalogo[]): EntradaCatalogo[] {
  const masUsadas = entradas.filter((e) => e.extra?.["Más usada"]);
  return masUsadas.length ? masUsadas : entradas;
}

type Linea = { descripcion: string; cantidad: number | null; precioUnitario: number | null; unidad: string; tipoAfectacionIgv: string; descuento: AjusteForm; cargo: AjusteForm; isc: IscForm; icbper: boolean };

/** El diálogo usa la escala compacta del design system (h-8, la de la barra de filtros) en vez de la de formulario
 *  (h-10): con cliente, ítems y totales en una sola vista, 8px por control cambian cuántas líneas entran. */
const CAMPO_DENSO = cn(CAMPO, "text-[13px]");

const LINEA_VACIA: Linea = { descripcion: "", cantidad: 1, precioUnitario: null, unidad: "NIU", tipoAfectacionIgv: "10", descuento: AJUSTE_VACIO, cargo: AJUSTE_VACIO, isc: ISC_VACIO, icbper: false };

export type TipoEmitible = "factura" | "boleta";

/** Tipo de serie (catálogo 01) de cada comprobante que se emite desde aquí: la letra de la serie decide el tipo en el backend. */
const TIPO_DE_SERIE: Record<TipoEmitible, string> = { factura: "01", boleta: "03" };

/**
 * Con qué tipo abre la emisión (#286): factura, salvo que la empresa solo tenga series activas de boleta. Con las dos, o con ninguna, factura: con
 * ninguna, el formulario dice que falta la serie.
 */
export function tipoInicial(series: Serie[]): TipoEmitible {
  const tiene = (t: TipoEmitible) => series.some((s) => s.tipo === TIPO_DE_SERIE[t] && s.activa);
  return !tiene("factura") && tiene("boleta") ? "boleta" : "factura";
}


const SIN_DOCUMENTO = "-";

/**
 * Lo que es el precio de la línea según su afectación (#117): con IGV en la gravada, con IVAP en la IVAP, y sin impuesto en la exonerada, la inafecta, la gratuita (valor
 * referencial) y la exportación. «con IGV» en una exonerada inducía a cargar el precio mal. `""`: líneas de afectaciones distintas, el encabezado no afirma una.
 */
export function etiquetaDelPrecio(afectacion: string): string {
  if (afectacion === "") return "Precio unit.";
  if (afectacion === "10") return "Precio unit. (con IGV)";
  if (afectacion === "17") return "Precio unit. (con IVAP)";
  return "Precio unit. (sin IGV)";
}
/** Espeja `Receptor.NOMBRE_SIN_DOCUMENTO`: cumple la regla 2022 (al menos 3 caracteres), error en SUNAT desde 2027-01-01. */
const COMPRADOR_SIN_DOCUMENTO = "CLIENTES VARIOS";
/**
 * Regla 2022 tal como la cuenta `Receptor`: al menos 3 caracteres sin los espacios de los extremos (#303). `minLength`
 * cuenta los espacios, así que «  JP  » pasaba el formulario para volver como 422; el tope de 1500 lo pone `maxLength`.
 */
const PATRON_NOMBRE = "\\s*\\S.+\\S\\s*";
const TITULO_NOMBRE = "Al menos 3 caracteres, sin contar los espacios de los extremos";
/** Espeja `Comprobante.TOPE_BOLETA_SIN_DOCUMENTO`: hasta este total (en soles) la boleta puede no identificar al comprador. */
const TOPE_SIN_DOCUMENTO = 700;

export function NuevoComprobanteForm({
  tipo = "factura",
  series,
  tasaIgv,
  cuentaDetracciones = null,
  plantilla = null,
  onEmitido,
  onCancelar,
  onCambioDatos,
}: {
  /** Factura (series F###, comprador con RUC) o boleta (series B###, comprador con cualquier documento o, hasta S/ 700, sin documento). */
  tipo?: TipoEmitible;
  series: Serie[];
  /**
   * Tasa vigente de la empresa: 10.5 % si está en el padrón de tasa especial, 18 % si no, y `null` mientras no se
   * sabe. El `null` importa: previsualizar al 18 % a un tenant del padrón muestra importes que no son los que
   * emitirá, y encima cambian solos cuando llega la respuesta.
   */
  tasaIgv: number | null;
  /** La cuenta de detracciones de la empresa (datos fiscales), para precargarla en la detracción (#288). */
  cuentaDetracciones?: string | null;
  /** #323: lo que se copia de otro comprobante (cliente, moneda, operación y líneas); fecha de hoy y sin número. Solo cuenta al montarse: el diálogo cambia la `key`. */
  plantilla?: Plantilla | null;
  onEmitido?: () => void;
  onCancelar?: () => void;
  /** Avisa si el formulario tiene algo escrito, para que quien lo contiene confirme antes de descartarlo (#117). */
  onCambioDatos?: (hayDatos: boolean) => void;
}) {
  const router = useRouter();
  const boleta = tipo === "boleta";
  const nombre = boleta ? "boleta" : "factura";
  const seriesDelTipo = series.filter((s) => s.tipo === TIPO_DE_SERIE[tipo] && s.activa);

  const [serie, setSerie] = useState(seriesDelTipo[0]?.serie ?? "");
  // Por render, no a nivel de módulo: el diálogo puede quedar abierto cruzando la medianoche de Lima.
  const hoy = hoyLima();
  const [fecha, setFecha] = useState(hoy);
  const [moneda, setMoneda] = useState(plantilla?.moneda ?? "PEN");
  // #316: soles por unidad de la moneda; opcional, solo fuera de soles. SUNAT no lo recibe: sirve al PDF, al registro de ventas y al tope de la boleta sin documento.
  const [tipoCambio, setTipoCambio] = useState("");
  // La factura siempre lleva RUC; la boleta, el documento que elija el usuario (DNI por defecto: es la venta más común).
  const [tipoDoc, setTipoDoc] = useState(plantilla?.cliente.tipoDoc ?? (boleta ? "1" : "6"));
  const [numDoc, setNumDoc] = useState(plantilla?.cliente.numDoc ?? "");
  const [razonSocial, setRazonSocial] = useState(plantilla ? (plantilla.cliente.tipoDoc === SIN_DOCUMENTO ? COMPRADOR_SIN_DOCUMENTO : plantilla.cliente.razonSocial) : "");
  const [direccion, setDireccion] = useState(plantilla?.cliente.direccion ?? "");
  // #319: guardar al comprador en «Clientes» al emitir (el guardado es idempotente y conserva lo que ya estaba guardado), y el aviso de un cliente guardado que no sirve para este comprobante.
  const [guardarCliente, setGuardarCliente] = useState(true);
  const [avisoCliente, setAvisoCliente] = useState<string | null>(null);
  const [lineas, setLineas] = useState<Linea[]>(plantilla?.lineas.length ? plantilla.lineas.map((l) => ({ ...l })) : [{ ...LINEA_VACIA }]);
  // #358: ajuste del importe total para cobrar en efectivo sin céntimos (`PayableRoundingAmount`). Texto, para poder escribir «-» y «0.» sin que el campo lo reescriba.
  const [redondeoTexto, setRedondeoTexto] = useState(plantilla?.redondeo ?? "");
  // #310: descuento y cargo globales (02/03 y 46/49/50); los de cada línea viven en la línea.
  const [descuentoGlobal, setDescuentoGlobal] = useState<AjusteForm>(plantilla?.descuentoGlobal ?? AJUSTE_VACIO);
  const [cargoGlobal, setCargoGlobal] = useState<AjusteForm>(plantilla?.cargoGlobal ?? AJUSTE_VACIO);
  // #309: la factura puede ser al crédito (la boleta es siempre al contado, RS 193-2020); `fechaVencimiento` es el DueDate informativo.
  const [alCredito, setAlCredito] = useState(false);
  const [credito, setCredito] = useState<CreditoForm>(CREDITO_VACIO);
  const [fechaVencimiento, setFechaVencimiento] = useState("");
  // #311: tipo de operación (catálogo 51) y, en una exportación, el país del cliente, el Incoterm y el país de uso del servicio.
  const [operacion, setOperacion] = useState(plantilla?.operacion ?? "0101");
  // #288: detracción (SPOT), solo en una factura de venta interna; fija el tipo de operación 1001 (1003 para el transporte de pasajeros).
  const [detraccion, setDetraccion] = useState<DetraccionForm>(DETRACCION_VACIA);
  // #297: percepción del IGV (operación 2001), en soles y al contado; es excluyente con la detracción (cada una fija su tipo de operación).
  const [percepcion, setPercepcion] = useState<PercepcionForm>(PERCEPCION_VACIA);
  // #313: retención del IGV (el cliente es agente de retención), solo en factura; informativa. Excluye a la detracción y a la percepción.
  const [retencion, setRetencion] = useState<RetencionForm>(RETENCION_VACIA);
  // #300: orden de compra, guías, otros documentos relacionados y observaciones; no cambian importes, así que no entran en el cálculo.
  const [referencias, setReferencias] = useState<ReferenciasForm>(REFERENCIAS_VACIAS);
  // Abierta por quien la abre (no por si hay algo escrito: al borrar lo escrito se cerraba sola mientras se usaba).
  const [datosAbiertos, setDatosAbiertos] = useState(false);
  // #312: anticipos. `esAnticipo` solo ayuda a describir la línea (SUNAT no marca la factura de anticipo); `anticiposAbierto` activa la búsqueda de los del cliente.
  const [esAnticipo, setEsAnticipo] = useState(false);
  const [anticiposAbierto, setAnticiposAbierto] = useState(false);
  const [anticiposElegidos, setAnticiposElegidos] = useState<AnticipoForm[]>([]);
  const [pais, setPais] = useState(plantilla?.cliente.pais ?? "");
  const [incoterm, setIncoterm] = useState("");
  const [paisDeUso, setPaisDeUso] = useState("");
  const paises = useMemo(() => listarPaises(), []);
  const [detalle, setDetalle] = useState<number | null>(null);
  const [unidades, setUnidades] = useState<EntradaCatalogo[] | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 328-H2: el diálogo avisa del tope con lo aceptado, pero el backend cuenta también lo que está en camino; si igual responde LIMITE_PLAN, se lleva al plan.
  // #285: lo mismo si el plan venció con el diálogo abierto (PLAN_VENCIDO).
  const [llevaAlPlan, setLlevaAlPlan] = useState(false);
  // Clave de idempotencia (#115): la misma mientras se reintente el mismo contenido, otra si el contenido cambia.
  const intento = useRef<Intento | null>(null);

  // #379: con un RUC completo (la factura siempre; la boleta solo si el documento elegido es RUC) se consulta el padrón de SUNAT y se rellenan el nombre y la
  // dirección. `auto` guarda lo que se rellenó: solo eso se quita al cambiar de RUC, nunca lo que el usuario escribió.
  const conRuc = tipoDoc === "6";
  const { resultado: consultaRuc, consultando: consultandoRuc, reintentar: reintentarConsultaRuc } = useConsultaRuc(numDoc, conRuc);
  const [auto, setAuto] = useState({ razon: "", direccion: "" });
  useEffect(() => {
    if (consultaRuc?.tipo !== "encontrado") return;
    const c = consultaRuc.contribuyente;
    // Solo se rellena lo vacío: lo que el usuario ya escribió se respeta (y se le ofrece el nombre de SUNAT aparte). La boleta no tiene campo de dirección.
    const razon = razonSocial.trim() ? "" : c.razon_social;
    const dir = !boleta && !direccion.trim() && c.direccion ? c.direccion : "";
    if (razon) setRazonSocial(razon);
    if (dir) setDireccion(dir);
    setAuto({ razon, direccion: dir });
    // Solo cuando llega el resultado de otro RUC: depender de lo que se escribe volvería a rellenar cada vez que el usuario vacía un campo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [consultaRuc]);

  /** Lo rellenado por el padrón deja de valer al cambiar el RUC: el nombre de un cliente no puede quedar bajo el RUC de otro. */
  function soltarAutocompletado() {
    if (auto.razon && razonSocial === auto.razon) setRazonSocial("");
    if (auto.direccion && direccion === auto.direccion) setDireccion("");
    setAuto({ razon: "", direccion: "" });
  }

  function cambiarNumDoc(valor: string) {
    if (valor !== numDoc) soltarAutocompletado();
    setNumDoc(valor);
  }

  /** Rellena al comprador con un cliente guardado. Un documento que este comprobante no admite (un DNI en una factura) no se aplica: se dice por qué. */
  function elegirClienteGuardado(c: ClienteGuardado) {
    const documento = DOCUMENTOS_BOLETA.find((d) => d.codigo === c.tipo_doc);
    // La factura sólo lleva RUC: el selector de documentos (con DNI y otros) es de la boleta y la exportación.
    const admitido = usaSelectorDeDocumento ? documentosOfrecidos.some((d) => d.codigo === c.tipo_doc) : c.tipo_doc === "6";
    if (!admitido) {
      setAvisoCliente(
        `«${c.razon_social}» tiene ${documento?.etiqueta ?? `el documento ${c.tipo_doc}`}, que ${exportacion ? "esta exportación" : `una ${nombre}`} no admite${!boleta && !exportacion ? ": la factura necesita un cliente con RUC" : ""}.`,
      );
      return;
    }
    setAvisoCliente(null);
    soltarAutocompletado();
    setTipoDoc(c.tipo_doc);
    setNumDoc(c.num_doc);
    setRazonSocial(c.razon_social);
    // La boleta no muestra la dirección: no se arrastra una que la persona no ve ni puede borrar.
    setDireccion(boleta ? "" : (c.direccion ?? ""));
    setPais(c.pais ?? "");
    setGuardarCliente(false);
  }

  // Tras un error el botón estuvo `disabled` y el navegador soltó el foco a `<body>`: un usuario de teclado perdía
  // su posición. Llevarlo a la alerta lo reubica y, de paso, garantiza que se lea.
  const alertaRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  // La guarda contra el envío implícito (Enter solo emite desde un botón) vive en `lib/formularios`: la comparten
  // este formulario y el de notas, que la había heredado sin ella.

  // Unidades de medida del catálogo 03, servido por el backend: no se hardcodean porque la lista cambia con SUNAT.
  useEffect(() => {
    if (detalle === null || unidades !== null) return;
    let vigente = true;
    apiRequest<{ entradas: EntradaCatalogo[] }>("/api/proxy/catalogos/03", { method: "GET" })
      .then((res) => {
        if (vigente) setUnidades(res.estado === "exito" && res.datos ? unidadesDelSelector(res.datos.entradas) : []);
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

  const sinDocumento = boleta && tipoDoc === SIN_DOCUMENTO;
  const documento = DOCUMENTOS_BOLETA.find((d) => d.codigo === tipoDoc) ?? DOCUMENTOS_BOLETA[0];
  const redondeo = redondeoDe(redondeoTexto);
  const exportacion = esExportacion(operacion);
  const conDetraccion = detraccion.activa && operacion === "0101";
  const conPercepcion = percepcion.activa && operacion === "0101" && !conDetraccion;
  const conRetencion = retencion.activa && !boleta && operacion === "0101" && !conDetraccion && !conPercepcion;
  const operacionEnviada = conDetraccion ? operacionDeDetraccion(detraccion.codigo) : conPercepcion ? OPERACION_PERCEPCION : operacion;
  const errorDetraccion = conDetraccion ? errorDeDetraccion(detraccion, moneda) : null;
  const errorReferencias = errorDeReferencias(referencias, boleta);
  const documentoDelCliente = sinDocumento ? "" : numDoc.trim();
  const disponibles = useAnticiposDisponibles(anticiposAbierto || anticiposElegidos.length > 0, documentoDelCliente, moneda);
  // Lo elegido para otro cliente u otra moneda ya no está entre los candidatos y no se manda.
  const anticiposVigentes = disponibles.estado === "listo" ? anticiposElegidos.filter((a) => disponibles.candidatos.some((c) => c.serie === a.serie && c.numero === a.numero)) : [];
  const errorAnticipos = errorDeAnticipos(anticiposVigentes, disponibles.candidatos);
  const errorTipoCambio = errorDeTipoCambio(tipoCambio, moneda);
  const errorRetencion = conRetencion ? errorDeRetencion(retencion) : null;
  const errorPercepcion = conPercepcion ? errorDePercepcion(percepcion, { moneda, alCredito: alCredito && !boleta }) : null;
  const errorExportacion = errorDeExportacion({ operacion, incoterm, paisDeUso, paisDelComprador: pais }, boleta);
  // En una exportación el comprador es del exterior: se elige su documento (como en la boleta) en vez de pedir un RUC.
  const usaSelectorDeDocumento = boleta || exportacion;
  const documentosOfrecidos = exportacion
    ? [...DOCUMENTOS_BOLETA.filter((d) => documentosDeExportacion(operacion, boleta).includes(d.codigo)), ...(boleta ? DOCUMENTOS_BOLETA.filter((d) => d.codigo === SIN_DOCUMENTO) : [])]
    : DOCUMENTOS_BOLETA;

  /** Al pasar a una exportación las líneas son de afectación 40, la moneda suele ser extranjera y el comprador no lleva RUC; al volver, todo regresa a la venta interna. */
  function cambiarOperacion(nueva: string) {
    const eraExp = esExportacion(operacion);
    const esExp = esExportacion(nueva);
    setOperacion(nueva);
    if (esExp && !eraExp) {
      setLineas((prev) => prev.map((l) => ({ ...l, tipoAfectacionIgv: "40" })));
      if (moneda === "PEN") setMoneda("USD");
      if (!boleta) { setTipoDoc("0"); setNumDoc(""); }
    }
    if (!esExp && eraExp) {
      setLineas((prev) => prev.map((l) => ({ ...l, tipoAfectacionIgv: "10" })));
      setMoneda((m) => (m === "USD" ? "PEN" : m));
      setPais(""); setIncoterm(""); setPaisDeUso("");
      if (!boleta) { setTipoDoc("6"); setNumDoc(""); }
    }
    // El RUC deja de valer donde la regla lo prohíbe (2800).
    if (esExp && tipoDoc === "6" && !documentosDeExportacion(nueva, boleta).includes("6")) { setTipoDoc(boleta ? "1" : "0"); setNumDoc(""); }
    if (!EXIGE_PAIS_DE_USO.has(nueva)) setPaisDeUso("");
  }
  // Los ajustes mal escritos no se envían ni se calculan: se muestran en su campo y la emisión no sale.
  // #298: tampoco se envía un ISC mal escrito ni una combinación de tributos que el dominio rechaza (IVAP mezclado, ICBPER fuera de NIU o con cantidad fraccionaria).
  const incompatibilidad = incompatibilidadDeTributos(lineasCompletas, operacion);
  const hayAjusteInvalido =
    errorDeAjuste(descuentoGlobal, "descuento") !== null || errorDeAjuste(cargoGlobal, "cargo") !== null ||
    lineasCompletas.some((l) => errorDeAjuste(l.descuento, "descuento") !== null || errorDeAjuste(l.cargo, "cargo") !== null || errorDeIsc(l.isc) !== null) ||
    incompatibilidad !== null || errorExportacion !== null || errorDetraccion !== null || errorPercepcion !== null || errorAnticipos !== null || errorRetencion !== null || errorTipoCambio !== null;

  /**
   * El cuerpo de `POST /v1/facturas`, también el de `POST /v1/facturas/calcular` (#405): lo comparten para que el total que se ve sea el de lo que se emite. Para
   * calcular se manda un comprador de relleno válido, porque los importes no dependen de él y un RUC a medio teclear haría que el dominio rechace todo.
   */
  function armarCuerpo(paraCalcular: boolean, netoPendiente: number | null = null) {
    return {
      serie,
      fecha_emision: fecha,
      moneda,
      tipo_cambio: tipoCambioAApi(tipoCambio, moneda),
      tipo_operacion: operacionEnviada === "0101" ? undefined : operacionEnviada,
      detraccion: conDetraccion ? detraccionAApi(detraccion, moneda) : undefined,
      ...(paraCalcular ? {} : referenciasAApi(referencias, boleta)),
      anticipos: anticiposAApi(anticiposVigentes, disponibles.candidatos),
      retencion_igv: conRetencion ? retencionAApi(retencion) : undefined,
      percepcion: conPercepcion ? percepcionAApi(percepcion, { moneda, alCredito: alCredito && !boleta }) : undefined,
      exportacion: exportacion && (incoterm || paisDeUso) ? { incoterm: incoterm || undefined, pais_uso: paisDeUso || undefined } : undefined,
      cliente: paraCalcular
        ? exportacion
          ? { tipo_doc: "0", num_doc: "EXT123456", razon_social: "Cliente", pais: pais || "US" }
          : boleta
            ? { tipo_doc: "1", num_doc: "12345678", razon_social: "Comprador" }
            : { tipo_doc: "6", num_doc: "20131312955", razon_social: "Cliente" }
        : { tipo_doc: tipoDoc, num_doc: sinDocumento ? SIN_DOCUMENTO : numDoc.trim(), razon_social: razonSocial.trim(), direccion: direccion.trim() || undefined, pais: exportacion && pais ? pais : undefined },
      items: lineasCompletas.map((l) => ({
        descripcion: l.descripcion.trim(),
        unidad: l.unidad,
        cantidad: l.cantidad,
        precio_unitario: l.precioUnitario ?? 0,
        tipo_afectacion_igv: l.tipoAfectacionIgv,
        descuento: ajusteAApi(l.descuento, "descuento"),
        cargos: ajusteAApi(l.cargo, "cargo") ? [ajusteAApi(l.cargo, "cargo")] : undefined,
        isc: iscAApi(l.isc),
        icbper: l.icbper ? true : undefined,
      })),
      descuento_global: ajusteAApi(descuentoGlobal, "descuento"),
      cargos: ajusteAApi(cargoGlobal, "cargo") ? [ajusteAApi(cargoGlobal, "cargo")] : undefined,
      redondeo: redondeo.valor ?? undefined,
      forma_pago: alCredito && !boleta && netoPendiente !== null ? formaPagoAApi(credito, netoPendiente) : undefined,
      fecha_vencimiento: fechaVencimiento || undefined,
    };
  }
  // Con líneas completas y un redondeo legible se le pide el cálculo al backend; sin ellas, o si no responde, el pie usa el cálculo local.
  const completo = lineasCompletas.length > 0 && redondeo.error === null && !hayAjusteInvalido && serie;
  // Primero los importes sin forma de pago: el neto pendiente por defecto es el total que calcula el backend. Con crédito, un segundo cálculo lleva las cuotas.
  const calculoBase = useCalculo(completo ? armarCuerpo(true) : null);
  const montoPercepcion = calculoBase.estado === "listo" ? (calculoBase.comprobante.percepcion?.monto ?? null) : null;
  // 3319: con detracción, el neto pendiente por defecto es lo que queda por cobrar (el total menos el depósito en la cuenta de detracciones); solo cuando ambos son en soles.
  const montoDetraccion = calculoBase.estado === "listo" ? (calculoBase.comprobante.detraccion?.monto ?? null) : null;
  const montoRetencion = calculoBase.estado === "listo" ? (calculoBase.comprobante.retencion_igv?.monto ?? null) : null;
  // La retención se descuenta en la moneda de la factura: el cliente retiene sobre el importe total.
  const netoBase = calculoBase.estado === "listo" ? calculoBase.totales.total - (moneda === "PEN" ? (montoDetraccion ?? 0) : 0) - (conRetencion ? (montoRetencion ?? 0) : 0) : totales.total + (redondeo.valor ?? 0);
  const errorCredito = alCredito && !boleta ? errorDeCredito(credito, netoBase, fecha) : null;
  const calculoCredito = useCalculo(completo && alCredito && !boleta && errorCredito === null ? armarCuerpo(true, netoBase) : null);
  const calculo = alCredito && !boleta && calculoCredito.estado !== "inactivo" ? calculoCredito : calculoBase;
  // Lo que se muestra en el pie: los importes del backend cuando respondió; si no, la aproximación local (sin descuentos, cargos, ISC ni redondeo).
  const delServidor = calculo.estado === "listo" ? calculo.totales : null;
  const mostrados = delServidor
    ? { gravado: delServidor.gravado, exonerado: delServidor.exonerado, inafecto: delServidor.inafecto, gratuito: delServidor.gratuito ?? 0, igv: delServidor.igv, total: delServidor.total }
    : { ...totales, total: totales.total + (redondeo.valor ?? 0) };
  // Algo del cliente o de las líneas está escrito: cerrar el diálogo (Esc, la X) lo perdería, y el diálogo pide confirmación (#117).
  const hayDatos =
    numDoc.trim() !== "" ||
    (razonSocial.trim() !== "" && razonSocial !== COMPRADOR_SIN_DOCUMENTO) ||
    direccion.trim() !== "" ||
    lineas.some((l) => l.descripcion.trim() !== "" || l.precioUnitario !== null);
  useEffect(() => {
    onCambioDatos?.(hayDatos);
  }, [hayDatos, onCambioDatos]);
  // IVAP o exportación mientras el backend no responde: la previsualización local no las suma y mostraría S/ 0.00 (#117).
  const sinImporteLocal = !delServidor && totales.noContempladas.length > 0;
  // Con tipo de cambio el tope de S/ 700 se mide en soles; sin él no hay cómo comparar otra moneda y el backend exige el documento: aquí se avisa antes.
  const superaTopeSinDocumento = sinDocumento && superaElTope(mostrados.total, moneda, tipoCambioAApi(tipoCambio, moneda));

  /** Al pasar a «sin documento» el nombre se completa con el habitual; al volver a un documento, ese nombre de relleno se quita. */
  function cambiarTipoDoc(nuevo: string) {
    // El nombre que rellenó el padrón ya no es de este comprador: se cuenta como vacío para lo de abajo (el estado todavía no se actualizó).
    const nombre = auto.razon && razonSocial === auto.razon ? "" : razonSocial;
    soltarAutocompletado();
    setTipoDoc(nuevo);
    setNumDoc("");
    if (nuevo === SIN_DOCUMENTO && !nombre.trim()) setRazonSocial(COMPRADOR_SIN_DOCUMENTO);
    if (nuevo !== SIN_DOCUMENTO && nombre === COMPRADOR_SIN_DOCUMENTO) setRazonSocial("");
  }

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
    // `EntradaMonto` redondea en cada pulsación (el valor enviado ya está fijado) pero muestra el texto crudo
    // mientras tiene el foco. Soltarlo acá repinta el valor redondeado; en el camino feliz se navega antes de
    // verlo, así que solo importa si la emisión falla y el formulario sigue en pantalla. Cosmético, no de datos.
    (document.activeElement as HTMLElement | null)?.blur();
    setError(null);
    setLlevaAlPlan(false);
    if (lineasCompletas.length === 0) {
      setError("Agrega al menos un ítem con descripción, cantidad y precio.");
      return;
    }
    if (superaTopeSinDocumento) {
      setError(`Una boleta de más de S/ ${TOPE_SIN_DOCUMENTO}.00 (o en otra moneda) identifica al comprador con su documento.`);
      return;
    }

    if (redondeo.error) {
      setError(redondeo.error);
      return;
    }
    // Las referencias no cambian importes: bloquean la emisión pero no el cálculo (si no, el pie saltaba al cálculo local mientras se escribe una guía).
    if (errorReferencias) {
      setError(errorReferencias);
      return;
    }
    if (hayAjusteInvalido) {
      setError(incompatibilidad ?? errorExportacion ?? errorDetraccion ?? errorPercepcion ?? errorAnticipos ?? errorRetencion ?? errorTipoCambio ?? "Corrija los descuentos, cargos o tributos marcados antes de emitir.");
      return;
    }
    if (errorCredito) {
      setError(errorCredito);
      return;
    }
    if (fechaVencimiento && fechaVencimiento < fecha) {
      setError("La fecha de vencimiento no puede ser anterior a la de emisión.");
      return;
    }
    const factura = armarCuerpo(false, netoBase);
    intento.current = intentoPara(intento.current, JSON.stringify(factura));

    setEnviando(true);
    let res: Awaited<ReturnType<typeof apiRequest<{ id: string }>>>;
    try {
      res = await apiRequest<{ id: string }>("/api/proxy/facturas", {
        method: "POST",
        body: factura,
        headers: { [CABECERA_IDEMPOTENCIA]: intento.current.clave },
      });
      // `finally` sin `catch` a propósito: el cliente no lanza, pero si algo inesperado lo hiciera, el botón tiene
      // que volver a habilitarse igual en vez de quedarse en «Emitiendo…».
    } finally {
      setEnviando(false);
    }

    // Un corte de conexión no dice si el POST llegó: pudo haber consumido correlativo. Con la clave de idempotencia (#115)
    // reintentar sin cambiar nada es seguro: si la factura ya se emitió, el backend devuelve la misma. (El cliente no lanza:
    // devuelve el sobre de error, así que esto no puede ir en un catch.)
    if (noSeSabeSiLlego(res)) {
      setError(
        `${seVencioLaEspera(res) ? "El servidor tardó demasiado en responder mientras se emitía" : "Se cortó la conexión mientras se emitía"}. Vuelve a emitir sin cambiar nada: si la ${nombre} ya se había emitido, verás la misma, sin duplicarla.`,
      );
      // El listado de fondo puede tener ya la factura nueva: que se vea sin recargar la página.
      router.refresh();
      return;
    }

    if (res.estado !== "exito" || !res.datos) {
      // El backend devuelve el detalle de SUNAT en `mensaje`; el código genérico solo dice la familia del error.
      setError(res.mensaje ?? mensajeError(res.codigo));
      setLlevaAlPlan(res.codigo === "LIMITE_PLAN" || res.codigo === "PLAN_VENCIDO");
      return;
    }
    // Los avisos de SUNAT (#378) solo vienen en esta respuesta: la ficha del comprobante los muestra desde aquí.
    guardarAvisosDeEmision(res.datos.id, res.avisos);
    // #319: el guardado del cliente no puede estorbar la emisión ya hecha; si falla, la factura sigue emitida y el cliente se guarda a mano desde «Clientes».
    if (guardarCliente && !sinDocumento && numDoc.trim() !== "") {
      void apiRequest(`/api/proxy/clientes/documento/${encodeURIComponent(tipoDoc)}/${encodeURIComponent(numDoc.trim())}`, {
        method: "PUT",
        body: { razon_social: razonSocial.trim(), direccion: direccion.trim() || undefined, pais: exportacion && pais ? pais : undefined },
      }).catch(() => undefined);
    }
    onEmitido?.();
    router.push(`/comprobantes/${res.datos.id}`);
    router.refresh();
  }

  if (seriesDelTipo.length === 0) {
    return (
      <div className="flex flex-col gap-3 px-5 py-4">
        <Alerta tono="aviso" titulo={`No tienes series de ${nombre}`}>
          Para emitir necesitas al menos una serie de tipo {TIPO_DE_SERIE[tipo]} activa ({boleta ? "B###" : "F###"}).
        </Alerta>
        <button type="button" className={cn(ACCION_SECUNDARIA, "self-end")} onClick={() => router.push("/series")}>
          Ir a series
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} onKeyDown={sinEnvioImplicito} className="flex min-h-0 flex-1 flex-col" data-testid="form-nuevo-comprobante">
      {/* El scroll vive solo en la lista de ítems: así la serie, la fecha y el cliente quedan siempre a la vista
          por largo que sea el comprobante. */}
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-5 py-4">
      <div className="grid items-start gap-4 sm:grid-cols-3">
        <Campo id="nc-serie" etiqueta="Serie" ayuda="El correlativo lo asigna khipu al emitir">
          <select id="nc-serie" value={serie} onChange={(e) => setSerie(e.target.value)} className={cn(CAMPO_DENSO, "font-mono")}>
            {seriesDelTipo.map((s) => (
              <option key={s.serie} value={s.serie}>
                {s.serie} · {s.ultimo_numero > 0 ? `último emitido N.º ${s.ultimo_numero}` : "aún sin emitir"}
              </option>
            ))}
          </select>
        </Campo>

        <Campo id="nc-fecha" etiqueta="Fecha de emisión" ayuda={`Máx. ${PLAZO_ENVIO[tipo].dias} días`}>
          {/* Los dos extremos los rechaza el backend (futura y fuera del plazo de envío: 3 días la factura, 2108; 7 la boleta, 1079), así que
              el calendario los cierra acá en vez de gastar un viaje para que lo diga SUNAT (#292). */}
          {/* `||` y no `??`: un input de fecha vaciado emite `""`, no `null`, así que `??` dejaba pasar el vacío
              hasta el `@NotNull LocalDate` del backend y la emisión moría con un error de deserialización. */}
          <EntradaFecha id="nc-fecha" valor={fecha} onCambio={(v) => setFecha(v || hoy)} min={fechaMinimaDeEmision(tipo, hoy)} max={hoy} variante="filtro" />
          {soloPorResumen(tipo, fecha, hoy) ? (
            <p className={cn(AYUDA_CAMPO, "mt-1")} data-testid="aviso-resumen-diario" role="status">
              Pasado el 5.º día SUNAT ya no la recibe sola: khipu la informa en el resumen diario, hasta el 7.º.
            </p>
          ) : null}
        </Campo>

        <Campo id="nc-moneda" etiqueta="Moneda">
          <select id="nc-moneda" value={moneda} onChange={(e) => { setMoneda(e.target.value); setTipoCambio(""); }} className={CAMPO_DENSO}>
            {MONEDAS.map((m) => (
              <option key={m.codigo} value={m.codigo}>
                {m.etiqueta}
              </option>
            ))}
          </select>
        </Campo>
        {moneda === "PEN" ? null : (
          <Campo id="nc-tipo-cambio" etiqueta="Tipo de cambio" opcional ayuda={`Soles por 1 ${moneda}; va al PDF y al registro de ventas, no a SUNAT`} error={errorTipoCambio ?? undefined}>
            <input id="nc-tipo-cambio" value={tipoCambio} onChange={(e) => setTipoCambio(e.target.value)} inputMode="decimal" placeholder="3.756" aria-invalid={errorTipoCambio !== null} className={cn(CAMPO_DENSO, "font-mono tabular-nums")} />
          </Campo>
        )}
      </div>

      <section className="grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className={ETIQUETA_CAMPO}>{boleta ? "Comprador" : "Cliente"}</h3>
          {sinDocumento ? null : (
            <label className="flex cursor-pointer items-center gap-1.5 text-[12px] text-muted-foreground">
              <input type="checkbox" checked={guardarCliente} onChange={(e) => setGuardarCliente(e.target.checked)} className="size-3.5 accent-primary" />
              Guardar en mis clientes al emitir
            </label>
          )}
        </div>
        <BuscadorDeClientes onElegir={elegirClienteGuardado} />
        {avisoCliente ? (
          <p role="status" className="text-[12px] text-warning-foreground" data-testid="aviso-cliente-guardado">
            {avisoCliente}
          </p>
        ) : null}
        {usaSelectorDeDocumento ? (
          <div className="grid gap-4 sm:grid-cols-[200px_180px_1fr]">
            <Campo id="nc-tipo-doc" etiqueta="Documento del comprador">
              <select id="nc-tipo-doc" value={tipoDoc} onChange={(e) => cambiarTipoDoc(e.target.value)} className={CAMPO_DENSO}>
                {documentosOfrecidos.map((d) => (
                  <option key={d.codigo} value={d.codigo}>
                    {d.etiqueta}
                  </option>
                ))}
              </select>
            </Campo>
            {sinDocumento ? (
              <p className={cn(AYUDA_CAMPO, "self-center")}>SUNAT lo admite hasta S/ {TOPE_SIN_DOCUMENTO}.00 de total; en otra moneda, con el tipo de cambio.</p>
            ) : (
              <Campo id="nc-num-doc" etiqueta="Número de documento">
                <input
                  id="nc-num-doc"
                  // `key` por documento: el patrón y el largo cambian con él y el valor anterior ya no aplica.
                  key={tipoDoc}
                  value={numDoc}
                  onChange={(e) => cambiarNumDoc((documento.codigo === "1" || documento.codigo === "6" ? e.target.value.replace(/\D/g, "") : e.target.value.replace(/\s/g, "")).slice(0, documento.max))}
                  inputMode={documento.codigo === "1" || documento.codigo === "6" ? "numeric" : "text"}
                  placeholder={documento.ejemplo}
                  required
                  pattern={documento.patron}
                  title={documento.titulo}
                  className={cn(CAMPO_DENSO, "font-mono tabular-nums")}
                />
              </Campo>
            )}
            <Campo id="nc-razon" etiqueta={boleta ? "Nombre del comprador" : "Razón social"}>
              <input
                id="nc-razon"
                value={razonSocial}
                onChange={(e) => setRazonSocial(e.target.value)}
                placeholder="Juan Pérez"
                required
                // `Receptor` exige de 3 a 1500 caracteres también en boleta, incluso sin documento (regla 2022).
                minLength={3}
                maxLength={1500}
                pattern={PATRON_NOMBRE}
                title={TITULO_NOMBRE}
                className={CAMPO_DENSO}
              />
            </Campo>
            {exportacion ? (
              <>
                <Campo id="nc-pais" etiqueta="País del cliente" ayuda={boleta ? "Opcional en una boleta" : "Obligatorio en una factura de exportación"}>
                  <select id="nc-pais" value={pais} onChange={(e) => setPais(e.target.value)} className={CAMPO_DENSO}>
                    <option value="">Seleccione</option>
                    {paises.map((p) => (
                      <option key={p.codigo} value={p.codigo}>
                        {p.nombre}
                      </option>
                    ))}
                  </select>
                </Campo>
                {boleta ? null : (
                  <div className="sm:col-span-2">
                    <Campo id="nc-direccion" etiqueta="Dirección" opcional>
                      <input id="nc-direccion" value={direccion} onChange={(e) => setDireccion(e.target.value)} placeholder="1200 Main St, Miami" className={CAMPO_DENSO} />
                    </Campo>
                  </div>
                )}
              </>
            ) : null}
            {conRuc ? (
              <EstadoConsultaRuc
                className="sm:col-span-3"
                consultando={consultandoRuc}
                resultado={consultaRuc}
                nombreActual={razonSocial}
                onReintentar={reintentarConsultaRuc}
                onUsarNombre={(n) => { setRazonSocial(n); setAuto((a) => ({ ...a, razon: n })); }}
              />
            ) : null}
            {superaTopeSinDocumento ? (
              <p data-testid="aviso-sin-documento" role="status" className={cn(AYUDA_CAMPO, "text-warning-foreground sm:col-span-3")}>
                Esta boleta pasa de S/ {TOPE_SIN_DOCUMENTO}.00{moneda !== "PEN" && tipoCambioAApi(tipoCambio, moneda) === undefined ? " o no es en soles y no indica el tipo de cambio" : ""}: identifica al comprador con su documento.
              </p>
            ) : null}
          </div>
        ) : (
        <div className="grid gap-4 sm:grid-cols-[180px_1fr]">
          <Campo id="nc-ruc" etiqueta="RUC" >
            <input
              id="nc-ruc"
              value={numDoc}
              onChange={(e) => cambiarNumDoc(e.target.value.replace(/\D/g, "").slice(0, 11))}
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
              pattern={PATRON_NOMBRE}
              title={TITULO_NOMBRE}
              className={CAMPO_DENSO}
            />
          </Campo>
          <EstadoConsultaRuc
            className="sm:col-span-2"
            consultando={consultandoRuc}
            resultado={consultaRuc}
            nombreActual={razonSocial}
            onReintentar={reintentarConsultaRuc}
            onUsarNombre={(n) => { setRazonSocial(n); setAuto((a) => ({ ...a, razon: n })); }}
          />
          <div className="sm:col-span-2">
            <Campo id="nc-direccion" etiqueta="Dirección" opcional>
              <input id="nc-direccion" value={direccion} onChange={(e) => setDireccion(e.target.value)} placeholder="Av. Prueba 123" className={CAMPO_DENSO} />
            </Campo>
          </div>
        </div>
        )}
      </section>

      {/* #309: forma de pago, solo en factura. Al crédito se arman las cuotas; la boleta se emite siempre al contado. */}
      <section className="grid gap-3">
        <div className="grid items-start gap-4 sm:grid-cols-2">
          {/* #311: tipo de operación (catálogo 51): venta interna o exportación. */}
          <Campo id="nc-operacion" etiqueta="Tipo de operación">
            <select id="nc-operacion" value={operacion} onChange={(e) => cambiarOperacion(e.target.value)} className={CAMPO_DENSO}>
              {(["Venta interna", "Exportación"] as const).map((g) => (
                <optgroup key={g} label={g}>
                  {OPERACIONES.filter((o) => o.grupo === g && (boleta || !o.soloBoleta)).map((o) => (
                    <option key={o.codigo} value={o.codigo}>
                      {o.codigo} · {o.etiqueta}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </Campo>
          {boleta ? null : (
            <Campo id="nc-forma-pago" etiqueta="Forma de pago">
              <select id="nc-forma-pago" value={alCredito ? "credito" : "contado"} onChange={(e) => setAlCredito(e.target.value === "credito")} className={CAMPO_DENSO}>
                <option value="contado">Al contado</option>
                <option value="credito">Al crédito (cuotas)</option>
              </select>
            </Campo>
          )}
        </div>
        {exportacion ? (
          <div className="grid items-start gap-4 sm:grid-cols-2" data-testid="datos-exportacion">
            <Campo id="nc-incoterm" etiqueta="Incoterm" opcional ayuda="Incoterms 2020; lo exige Aduanas, SUNAT no lo valida">
              <select id="nc-incoterm" value={incoterm} onChange={(e) => setIncoterm(e.target.value)} className={CAMPO_DENSO}>
                <option value="">Sin incoterm</option>
                {INCOTERMS.map((i) => (
                  <option key={i} value={i}>
                    {i}
                  </option>
                ))}
              </select>
            </Campo>
            {EXIGE_PAIS_DE_USO.has(operacion) ? (
              <Campo id="nc-pais-uso" etiqueta="País de uso del servicio" ayuda="Donde se usa o aprovecha el servicio (3098); distinto de Perú">
                <select id="nc-pais-uso" value={paisDeUso} onChange={(e) => setPaisDeUso(e.target.value)} className={CAMPO_DENSO}>
                  <option value="">Seleccione</option>
                  {paises.filter((p) => p.codigo !== "PE").map((p) => (
                    <option key={p.codigo} value={p.codigo}>
                      {p.nombre}
                    </option>
                  ))}
                </select>
              </Campo>
            ) : null}
          </div>
        ) : null}
        {/* #288: detracción, #297: percepción. Solo en una venta interna (factura o boleta); se excluyen entre sí porque cada una fija su tipo de operación. */}
        {operacion !== "0101" ? null : (
          <div className="grid gap-3">
            <label className="flex items-center gap-2 text-[13px] font-medium">
              <input
                type="checkbox"
                checked={detraccion.activa}
                onChange={(e) => {
                  setDetraccion((d) => ({ ...d, activa: e.target.checked, cuenta: d.cuenta || cuentaDetracciones || "" }));
                  if (e.target.checked) {
                    setPercepcion((p) => ({ ...p, activa: false }));
                    setRetencion((r) => ({ ...r, activa: false }));
                  }
                }}
                className="size-3.5 accent-primary"
              />
              Operación sujeta a detracción (SPOT)
            </label>
            {detraccion.activa ? (
              <DetraccionEditor valor={detraccion} onCambio={setDetraccion} moneda={moneda} montoCalculado={montoDetraccion} cuentaDeLaEmpresa={cuentaDetracciones} />
            ) : avisoDeDetraccion({ activa: false, moneda, total: mostrados.total, boleta }) ? (
              <p role="status" className={cn(AYUDA_CAMPO, "text-warning-foreground")} data-testid="aviso-detraccion">
                Esta factura pasa de S/ 700.00. Si es un servicio sujeto al SPOT, marque la detracción.
              </p>
            ) : null}
            {detraccion.activa ? null : (
              <>
                <label className="flex items-center gap-2 text-[13px] font-medium">
                  <input
                    type="checkbox"
                    checked={percepcion.activa}
                    onChange={(e) => {
                      setPercepcion((p) => ({ ...p, activa: e.target.checked }));
                      if (e.target.checked) setRetencion((r) => ({ ...r, activa: false }));
                    }}
                    className="size-3.5 accent-primary"
                  />
                  Operación con percepción del IGV
                </label>
                {percepcion.activa ? (
                  <PercepcionEditor valor={percepcion} onCambio={setPercepcion} moneda={moneda} alCredito={alCredito && !boleta} montoCalculado={montoPercepcion} />
                ) : null}
                {boleta || percepcion.activa ? null : (
                  <>
                    <label className="flex items-center gap-2 text-[13px] font-medium">
                      <input type="checkbox" checked={retencion.activa} onChange={(e) => setRetencion((r) => ({ ...r, activa: e.target.checked }))} className="size-3.5 accent-primary" />
                      El cliente retiene el IGV (agente de retención)
                    </label>
                    {retencion.activa ? <RetencionEditor valor={retencion} onCambio={setRetencion} moneda={moneda} montoCalculado={montoRetencion} /> : null}
                  </>
                )}
              </>
            )}
          </div>
        )}

        {errorExportacion ? (
          <p role="alert" className={cn(AYUDA_CAMPO, "text-destructive")} data-testid="error-exportacion">
            {errorExportacion}
          </p>
        ) : null}
        {boleta || !alCredito ? null : <CreditoEditor credito={credito} onCambio={setCredito} neto={netoBase} moneda={moneda} emision={fecha} error={credito.cuotas.length === 0 ? null : errorCredito} />}
      </section>

      <section className="flex min-h-[11rem] flex-1 flex-col gap-3">
        <div className="flex items-baseline justify-between gap-3">
          <h3 className={ETIQUETA_CAMPO}>
            Ítems <span className="font-normal text-muted-foreground">· {lineas.length}</span>
          </h3>
          {/* El número de orden del ítem es de hasta 3 dígitos: pasado el tope el backend responde 422 (2023). */}
          <button
            type="button"
            onClick={() => setLineas((p) => [...p, { ...LINEA_VACIA }])}
            disabled={lineas.length >= MAX_LINEAS}
            title={lineas.length >= MAX_LINEAS ? `Un comprobante admite hasta ${MAX_LINEAS} ítems` : undefined}
            className="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline disabled:cursor-not-allowed disabled:opacity-50 disabled:no-underline"
          >
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
            <span className={AYUDA_CAMPO} data-testid="encabezado-precio">{etiquetaDelPrecio(lineas.every((l) => l.tipoAfectacionIgv === lineas[0].tipoAfectacionIgv) ? lineas[0].tipoAfectacionIgv : "")}</span>
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
                <Campo id={`nc-precio-${i}`} etiqueta={etiquetaDelPrecio(linea.tipoAfectacionIgv)} className="[&>label]:sr-only">
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
                      {exportacion ? <option value="40">40 · Exportación de bienes o servicios</option> : null}
                      {(exportacion ? [] : GRUPOS_AFECTACION).map((g) => (
                        <optgroup key={g.grupo} label={g.grupo}>
                          {g.opciones.map((a) => (
                            <option key={a.codigo} value={a.codigo}>
                              {a.codigo} · {a.etiqueta}
                            </option>
                          ))}
                        </optgroup>
                      ))}
                    </select>
                  </Campo>
                  {/* #298: ISC (catálogo 08) y bolsas de plástico (ICBPER). El precio que se escribe ya los incluye; el importe de cada tributo lo calcula el backend. */}
                  <fieldset className="grid content-start gap-1.5">
                    <legend className="mb-1 text-[12px] font-medium text-foreground">ISC del ítem</legend>
                    <Campo id={`nc-isc-sistema-${i}`} etiqueta="Sistema del ISC" className="[&>label]:sr-only">
                      <select
                        id={`nc-isc-sistema-${i}`}
                        value={linea.isc.sistema}
                        onChange={(e) => actualizar(i, { isc: { ...ISC_VACIO, sistema: e.target.value as IscForm["sistema"] } })}
                        className={CAMPO_DENSO}
                      >
                        <option value="">Sin ISC</option>
                        <option value="01">01 · Al valor (tasa %)</option>
                        <option value="02">02 · Monto fijo por unidad</option>
                        <option value="03">03 · Precio de venta al público</option>
                      </select>
                    </Campo>
                    {linea.isc.sistema === "02" ? (
                      <Campo id={`nc-isc-monto-${i}`} etiqueta="Monto por unidad del ISC" className="[&>label]:sr-only" error={errorDeIsc(linea.isc) ?? undefined}>
                        <input id={`nc-isc-monto-${i}`} value={linea.isc.montoUnitario} onChange={(e) => actualizar(i, { isc: { ...linea.isc, montoUnitario: e.target.value } })} inputMode="decimal" placeholder="Monto por unidad, ej. 2.25" aria-invalid={errorDeIsc(linea.isc) !== null} className={cn(CAMPO_DENSO, "font-mono tabular-nums")} />
                      </Campo>
                    ) : linea.isc.sistema !== "" ? (
                      <>
                        <Campo id={`nc-isc-tasa-${i}`} etiqueta="Tasa del ISC (%)" className="[&>label]:sr-only" error={linea.isc.sistema === "01" ? (errorDeIsc(linea.isc) ?? undefined) : undefined}>
                          <input id={`nc-isc-tasa-${i}`} value={linea.isc.tasa} onChange={(e) => actualizar(i, { isc: { ...linea.isc, tasa: e.target.value } })} inputMode="decimal" placeholder="Tasa %, ej. 35" aria-invalid={errorDeIsc(linea.isc) !== null} className={cn(CAMPO_DENSO, "font-mono tabular-nums")} />
                        </Campo>
                        {linea.isc.sistema === "03" ? (
                          <Campo id={`nc-isc-pvp-${i}`} etiqueta="Precio de venta al público sugerido" className="[&>label]:sr-only" error={errorDeIsc(linea.isc) ?? undefined}>
                            <input id={`nc-isc-pvp-${i}`} value={linea.isc.basePvp} onChange={(e) => actualizar(i, { isc: { ...linea.isc, basePvp: e.target.value } })} inputMode="decimal" placeholder="PVP sugerido por unidad, ej. 3.50" aria-invalid={errorDeIsc(linea.isc) !== null} className={cn(CAMPO_DENSO, "font-mono tabular-nums")} />
                          </Campo>
                        ) : null}
                      </>
                    ) : null}
                    <label className="mt-1 flex items-center gap-2 text-[12px] text-muted-foreground">
                      <input
                        type="checkbox"
                        checked={linea.icbper}
                        onChange={(e) => actualizar(i, e.target.checked ? { icbper: true, unidad: "NIU" } : { icbper: false })}
                        className="size-3.5 accent-primary"
                      />
                      Bolsas de plástico (ICBPER)
                    </label>
                    <p className={AYUDA_CAMPO}>Una bolsa por unidad: se cobra el impuesto por cada una.</p>
                  </fieldset>
                  {/* #310: descuento y cargo de la línea (catálogo 53: 00/01 y 47/48). */}
                  <AjusteEditor id={`nc-desc-linea-${i}`} etiqueta="Descuento del ítem" clase="descuento" valor={linea.descuento} onCambio={(a) => actualizar(i, { descuento: a })} />
                  <AjusteEditor id={`nc-cargo-linea-${i}`} etiqueta="Cargo del ítem" clase="cargo" valor={linea.cargo} onCambio={(a) => actualizar(i, { cargo: a })} />
                </div>
              ) : null}
            </div>
          ))}
          </div>
        </div>
      </section>

        {/* #358: ajustes poco frecuentes del comprobante. Cerrado por defecto: la vista básica es la de siempre. Los issues siguientes (#310, #300, #309…) agregan sus campos acá. */}
        <details className="rounded-xl border border-border px-3 py-2" data-testid="mas-opciones" open={redondeoTexto !== "" || descuentoGlobal.valor !== "" || cargoGlobal.valor !== "" || fechaVencimiento !== ""}>
          <summary className="cursor-pointer text-[13px] font-medium">Más opciones del comprobante</summary>
          <div className="mt-3 grid gap-4 sm:grid-cols-3">
            <Campo id="nc-vencimiento" etiqueta="Fecha de vencimiento" ayuda="Informativa; no anterior a la emisión" opcional>
              <EntradaFecha id="nc-vencimiento" valor={fechaVencimiento} onCambio={setFechaVencimiento} min={fecha} variante="filtro" />
            </Campo>
            <Campo id="nc-redondeo" etiqueta="Redondeo" ayuda={redondeo.error ?? "Entre -1.00 y 1.00, p. ej. -0.05 para cobrar en efectivo sin céntimos"}>
              <input
                id="nc-redondeo"
                value={redondeoTexto}
                onChange={(e) => setRedondeoTexto(e.target.value)}
                inputMode="decimal"
                placeholder="0.00"
                aria-invalid={redondeo.error !== null}
                className={cn(CAMPO_DENSO, "font-mono tabular-nums")}
              />
            </Campo>
            {/* #310: descuento global (02/03) y cargos globales (46 recargo al consumo, 49, 50). */}
            <AjusteEditor id="nc-desc-global" etiqueta="Descuento global" clase="descuento" valor={descuentoGlobal} onCambio={setDescuentoGlobal} />
            <AjusteEditor id="nc-cargo-global" etiqueta="Cargo global" clase="cargo" valor={cargoGlobal} onCambio={setCargoGlobal} ofreceRecargoConsumo />
          </div>
        </details>

        {/* #312: anticipos. Una factura de anticipo es común; la final descuenta los del mismo cliente. */}
        <details className="rounded-xl border border-border px-3 py-2" data-testid="anticipos" open={anticiposAbierto || anticiposElegidos.length > 0} onToggle={(e) => setAnticiposAbierto((e.currentTarget as HTMLDetailsElement).open)}>
          <summary className="cursor-pointer text-[13px] font-medium">Anticipos</summary>
          <div className="mt-3 grid gap-3">
            <label className="flex items-center gap-2 text-[13px] font-medium">
              <input
                type="checkbox"
                checked={esAnticipo}
                onChange={(e) => {
                  setEsAnticipo(e.target.checked);
                  if (e.target.checked) {
                    setAnticiposElegidos([]);
                    setLineas((prev) => prev.map((l, n) => (n === 0 && l.descripcion.trim() === "" ? { ...l, descripcion: "Anticipo por " } : l)));
                  }
                }}
                className="size-3.5 accent-primary"
              />
              Esta {nombre} es un anticipo
            </label>
            {esAnticipo ? (
              <p className={AYUDA_CAMPO} data-testid="anticipo-ayuda">
                Se emite como cualquier {nombre}. Cuando entregue el bien o servicio, en la {nombre} final elija este anticipo en «Descontar anticipos» para regularizarlo.
              </p>
            ) : (
              <div className="grid gap-2">
                <div className={ETIQUETA_CAMPO}>Descontar anticipos</div>
                <AnticiposEditor disponibles={disponibles} elegidos={anticiposVigentes} onCambio={setAnticiposElegidos} moneda={moneda} hayCliente={documentoDelCliente !== ""} />
              </div>
            )}
          </div>
        </details>

        {/* #300: datos adicionales, colapsados; se abren solos si ya hay algo escrito. */}
        <details className="rounded-xl border border-border px-3 py-2" data-testid="datos-adicionales" open={datosAbiertos} onToggle={(e) => setDatosAbiertos((e.currentTarget as HTMLDetailsElement).open)}>
          <summary className="cursor-pointer text-[13px] font-medium">Datos adicionales: guías, documentos relacionados y observaciones</summary>
          <div className="mt-3">
            <ReferenciasEditor valor={referencias} onCambio={setReferencias} boleta={boleta} />
          </div>
        </details>

        {incompatibilidad ? (
          <p role="alert" className={cn(AYUDA_CAMPO, "text-destructive")} data-testid="tributos-incompatibles">
            {incompatibilidad}
          </p>
        ) : null}

        {calculo.estado === "rechazado" ? (
          <p role="alert" className={cn(AYUDA_CAMPO, "text-destructive")} data-testid="calculo-rechazado">
            {calculo.mensaje}
          </p>
        ) : null}

        {error ? (
          <div ref={alertaRef} tabIndex={-1} className="outline-none">
            <Alerta
              tono="error"
              accion={
                llevaAlPlan ? (
                  <Link href="/cuenta/plan" onClick={onCancelar} className={ACCION_SECUNDARIA}>
                    Ver plan y consumo
                  </Link>
                ) : undefined
              }
            >
              {error}
            </Alerta>
          </div>
        ) : null}
      </div>

      {/* Los totales viven en el pie fijo: con muchos ítems el importe a emitir no debe perderse al scrollear. */}
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-x-6 gap-y-3 border-t border-border/60 bg-muted/40 px-5 py-3" data-testid="totales-previsualizados">
        <div className="flex flex-wrap items-baseline gap-x-5 gap-y-1">
          <dl className={cn(AYUDA_CAMPO, "flex flex-wrap items-baseline gap-x-4 gap-y-0.5")}>
            {(sinImporteLocal ? [] : [
              { etiqueta: "Gravado", monto: mostrados.gravado, siempre: true },
              { etiqueta: "Exonerado", monto: mostrados.exonerado, siempre: false },
              { etiqueta: "Inafecto", monto: mostrados.inafecto, siempre: false },
              { etiqueta: "Gratuito", monto: mostrados.gratuito, siempre: false },
              // Mientras la empresa carga no se sabe si es 18 % o la reducida del padrón: se muestra sin tasa en
              // vez de afirmar una que puede cambiarle los importes bajo los ojos un instante después.
              { etiqueta: tasaIgv === null ? "IGV" : `IGV (${tasaIgv} %)`, monto: mostrados.igv, siempre: true },
              // Solo los conoce el backend (#405): aparecen cuando respondió.
              { etiqueta: "Redondeo", monto: delServidor?.redondeo ?? 0, siempre: false },
            ])
              // Sin importe local (IVAP o exportación mientras el backend no responde) no hay filas que mostrar: todas dirían 0.00.
              // El IGV no se muestra en 0.00 cuando no hay operaciones gravadas (una factura toda exonerada no tiene «IGV (18 %)»).
              .filter((f) => f.monto !== 0 || (f.siempre && (f.etiqueta.startsWith("IGV") ? lineasCompletas.length === 0 || mostrados.gravado !== 0 || totales.noContempladas.length > 0 : true)))
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
          {conDetraccion && montoDetraccion !== null ? (
            <span className={AYUDA_CAMPO} data-testid="detraccion-pie">
              Detracción {formatearMonto("PEN", montoDetraccion)} · Neto a cobrar {formatearMonto(moneda, mostrados.total - (moneda === "PEN" ? montoDetraccion : 0))}
            </span>
          ) : null}
          {moneda !== "PEN" && calculo.estado === "listo" && calculo.totalEnSoles !== null ? (
            <span className={AYUDA_CAMPO} data-testid="total-en-soles">
              Equivale a {formatearMonto("PEN", calculo.totalEnSoles)} al tipo de cambio {tipoCambio.trim()}
            </span>
          ) : null}
          {conRetencion && montoRetencion !== null ? (
            <span className={AYUDA_CAMPO} data-testid="retencion-pie">
              Retención {formatearMonto(moneda, montoRetencion)} · Neto a cobrar {formatearMonto(moneda, mostrados.total - montoRetencion)}
            </span>
          ) : null}
          {conPercepcion && montoPercepcion !== null ? (
            <span className={AYUDA_CAMPO} data-testid="percepcion-pie">
              Percepción {formatearMonto("PEN", montoPercepcion)} · Total a cobrar {formatearMonto("PEN", mostrados.total + montoPercepcion)}
            </span>
          ) : null}
          <div className="flex items-baseline gap-2">
            <span className={cn(ETIQUETA_CAMPO, "text-muted-foreground")}>Total a pagar</span>
            <span className="font-mono text-lg font-semibold tabular-nums" data-testid="total-a-pagar">
              {sinImporteLocal ? "—" : formatearMonto(moneda, mostrados.total)}
            </span>
            {sinImporteLocal ? (
              <span className={AYUDA_CAMPO} data-testid="importe-por-calcular">
                Lo calcula khipu
              </span>
            ) : null}
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
            Emitir {nombre}
          </BotonAsync>
        </div>
      </div>
    </form>
  );
}
