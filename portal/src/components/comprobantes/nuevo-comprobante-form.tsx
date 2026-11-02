"use client";

import { PanelQuitable } from "khipu-design-system/patrones/panel-quitable";
import { CheckIcon, PlusIcon, SlidersHorizontalIcon, TrashIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useLocale, useMessages } from "next-intl";
import { useRouter } from "next/navigation";
import { Fragment, type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { Campo } from "khipu-design-system/formularios/campo";
import { Casilla } from "khipu-design-system/formularios/casilla";
import { Combobox } from "khipu-design-system/formularios/combobox";
import { EntradaFecha } from "khipu-design-system/formularios/entrada-fecha";
import { EntradaMonto } from "khipu-design-system/formularios/entrada-monto";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { StepperNumerico } from "khipu-design-system/formularios/stepper-numerico";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { Button } from "khipu-design-system/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "khipu-design-system/ui/select";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest, noSeSabeSiLlego, seVencioLaEspera } from "@/lib/api/browser";
import type { Serie } from "@/lib/api/series";
import { documentosBoleta } from "@/lib/comprobantes/documentos";
import type { ClienteGuardado } from "@/lib/api/clientes";
import type { ProductoGuardado } from "@/lib/api/productos";
import { BuscadorDeClientes } from "./buscador-de-clientes";
import { BuscadorDeProductos } from "./buscador-de-productos";
import { guardarAvisosDeEmision } from "@/lib/comprobantes/avisos-de-emision";
import { MAX_LINEAS } from "@/lib/comprobantes/limites";
import { fechaMinimaDeEmision, PLAZO_ENVIO, soloPorResumen } from "@/lib/comprobantes/plazo";
import { anticiposAApi, errorDeAnticipos, type AnticipoForm } from "@/lib/comprobantes/anticipos";
import { AJUSTE_VACIO, ajusteAApi, errorDeAjuste, type AjusteForm } from "@/lib/comprobantes/ajustes";
import { CREDITO_VACIO, errorDeCredito, formaPagoAApi, type CreditoForm } from "@/lib/comprobantes/credito";
import { documentosDeExportacion, errorDeExportacion, esExportacion, EXIGE_PAIS_DE_USO, GRUPOS_OPERACION, INCOTERMS, operaciones } from "@/lib/comprobantes/operaciones";
import { listarPaises } from "@/lib/comprobantes/paises";
import { avisoDeDetraccion, DETRACCION_VACIA, detraccionAApi, errorDeDetraccion, operacionDeDetraccion, type DetraccionForm } from "@/lib/comprobantes/detraccion";
import { errorDePercepcion, OPERACION_PERCEPCION, PERCEPCION_VACIA, percepcionAApi, type PercepcionForm } from "@/lib/comprobantes/percepcion";
import { errorDeTipoCambio, monedas, superaElTopeSinDocumento as superaElTope, tipoCambioAApi } from "@/lib/comprobantes/monedas";
import type { Plantilla } from "@/lib/comprobantes/duplicar";
import { redondeoDe } from "@/lib/comprobantes/redondeo";
import { errorDeRetencion, RETENCION_VACIA, retencionAApi, type RetencionForm } from "@/lib/comprobantes/retencion";
import { errorDeReferencias, REFERENCIAS_VACIAS, referenciasAApi, type ReferenciasForm } from "@/lib/comprobantes/referencias";
import { errorDeIsc, gruposAfectacion, incompatibilidadDeTributos, ISC_VACIO, iscAApi, type IscForm, etiquetaDelPrecio } from "@/lib/comprobantes/tributos";
import { type EntradaCatalogo, unidadesDelSelector } from "@/lib/comprobantes/unidades";
import { useCalculo } from "@/lib/comprobantes/use-calculo";
import { calcularTotales, esGratuita, TASA_GENERAL, type ItemParaTotales } from "@/lib/comprobantes/totales";
import { ACCION_SECUNDARIA, AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, CONTROL_FILTRO, ERROR_CAMPO, ETIQUETA_CAMPO, FOCO_CAMPO } from "@/lib/estilos";
import { formatearMonto, hoyLima } from "@/lib/formato";
import { sinEnvioImplicito } from "@/lib/formularios";
import { CABECERA_IDEMPOTENCIA, intentoPara, type Intento } from "@/lib/idempotencia";
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
import { extrasOfrecidos, type Extra } from "@/lib/comprobantes/extras";
import { enlaceASeccion } from "@/lib/empresa/secciones";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { rucValido } from "@/lib/validacion";
import { TarjetaDeCliente } from "./tarjeta-de-cliente";
import { usePreferenciasDeEmision } from "@/lib/use-preferencias-de-emision";




type Linea = { codigo?: string; codigoSunat?: string; descripcion: string; cantidad: number | null; precioUnitario: number | null; unidad: string; tipoAfectacionIgv: string; descuento: AjusteForm; cargo: AjusteForm; isc: IscForm; icbper: boolean };

/** El diálogo usa la escala compacta del design system (h-8, la de la barra de filtros) en vez de la de formulario
 *  (h-10): con cliente, ítems y totales en una sola vista, 8px por control cambian cuántas líneas entran. */

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

/** Espeja `Receptor.NOMBRE_SIN_DOCUMENTO`: cumple la regla 2022 (al menos 3 caracteres), error en SUNAT desde 2027-01-01. */
const COMPRADOR_SIN_DOCUMENTO = "CLIENTES VARIOS";
/**
 * Regla 2022 tal como la cuenta `Receptor`: al menos 3 caracteres sin los espacios de los extremos (#303). `minLength`
 * cuenta los espacios, así que «  JP  » pasaba el formulario para volver como 422; el tope de 1500 lo pone `maxLength`.
 */
const PATRON_NOMBRE = "\\s*\\S.+\\S\\s*";
/** Espeja `Comprobante.TOPE_BOLETA_SIN_DOCUMENTO`: hasta este total (en soles) la boleta puede no identificar al comprador. */
const TOPE_SIN_DOCUMENTO = 700;

/** Un panel de la fila «Agregar:»: su título y el botón que lo quita (con su contenido). */
function PanelExtra({ id, titulo, etiquetaQuitar, onQuitar, children }: { id: Extra; titulo: string; etiquetaQuitar: string; onQuitar: () => void; children: ReactNode }) {
  return (
    <PanelQuitable titulo={titulo} etiquetaQuitar={etiquetaQuitar} onQuitar={onQuitar} data-testid={`extra-${id}`}>
      {children}
    </PanelQuitable>
  );
}

export function NuevoComprobanteForm({
  tipo = "factura",
  series,
  tasaIgv,
  cuentaDetracciones = null,
  plantilla = null,
  selectorDeTipo = null,
  onEmitido,
  onCancelar,
  onCambioDatos,
}: {
  /** Factura (series F###, comprador con RUC) o boleta (series B###, comprador con cualquier documento o, hasta S/ 700, sin documento). */
  tipo?: TipoEmitible;
  /** #557: el selector Factura/Boleta del diálogo, que va en la misma línea que la serie, la fecha y la moneda (y sobre el aviso de que faltan series). */
  selectorDeTipo?: ReactNode;
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
  const messages = useMessages();
  const locale = useLocale();
  const mensajeError = useMensajeError();
  const t = messages.comprobantes.formulario;
  // #560: los «+» avanzados, solo si la empresa los pidió; mientras se lee (o si falla), el formulario simple.
  const { opcionesAvanzadas } = usePreferenciasDeEmision();
  const sinPermisoDeConfigurar = useSinPermisoDeConfigurar();
  const docsBoleta = useMemo(() => documentosBoleta(messages), [messages]);
  const boleta = tipo === "boleta";
  const nombre = t.nombre[tipo];
  const seriesDelTipo = series.filter((s) => s.tipo === TIPO_DE_SERIE[tipo] && s.activa);

  const [serie, setSerie] = useState(seriesDelTipo[0]?.serie ?? "");
  // Por render, no a nivel de módulo: el diálogo puede quedar abierto cruzando la medianoche de Lima.
  const hoy = hoyLima();
  const [fecha, setFecha] = useState(hoy);
  const [moneda, setMoneda] = useState(plantilla?.moneda ?? "PEN");
  // #316: soles por unidad de la moneda; opcional, solo fuera de soles. SUNAT no lo recibe: sirve al PDF, al registro de ventas y al tope de la boleta sin documento.
  const [tipoCambio, setTipoCambio] = useState("");
  // La factura siempre lleva RUC; la boleta, el documento que elija el usuario. #557: la boleta empieza como «Clientes varios» (sin documento): el campo «Cliente»
  // vacío es eso, y un DNI o un RUC escrito ahí lo reemplaza.
  const [tipoDoc, setTipoDoc] = useState(plantilla?.cliente.tipoDoc ?? (boleta ? SIN_DOCUMENTO : "6"));
  const [numDoc, setNumDoc] = useState(plantilla?.cliente.numDoc ?? "");
  const [razonSocial, setRazonSocial] = useState(plantilla ? (plantilla.cliente.tipoDoc === SIN_DOCUMENTO ? COMPRADOR_SIN_DOCUMENTO : plantilla.cliente.razonSocial) : boleta ? COMPRADOR_SIN_DOCUMENTO : "");
  // #557: lo escrito en el campo «Cliente» mientras no es un cliente (busca en los guardados; un documento completo se toma como tal), si los campos del comprador están
  // desplegados («Editar datos» o «Ingresar a mano») y si el campo debe tomar el foco al volver de «Cambiar».
  const [textoCliente, setTextoCliente] = useState("");
  const [editandoCliente, setEditandoCliente] = useState(false);
  const [enfocarCliente, setEnfocarCliente] = useState(false);
  const [direccion, setDireccion] = useState(plantilla?.cliente.direccion ?? "");
  // #325: el correo del cliente viaja con el comprobante; si la empresa activó el envío automático, le llega el PDF y el XML cuando SUNAT lo acepta.
  const [email, setEmail] = useState(plantilla?.cliente.email ?? "");
  // #319: al emitir, el comprador con documento se guarda en «Clientes» (el guardado es idempotente y conserva lo que ya estaba guardado). #557: sin casilla; solo no se
  // vuelve a guardar el cliente que se eligió de los guardados (su «tipo/número»), mientras siga siendo ese. También el aviso de un cliente guardado que no sirve para este comprobante.
  const [clienteElegido, setClienteElegido] = useState<string | null>(null);
  const [avisoCliente, setAvisoCliente] = useState<string | null>(null);
  const [avisoProducto, setAvisoProducto] = useState<string | null>(null);
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
  // #557: los paneles «+» que el usuario abrió. Un panel también se ve si ya trae datos (una copia, un cliente guardado con correo), y no se cierra solo al borrar lo
  // escrito mientras se usa: solo con su botón de quitar, que además lo limpia. Crédito, detracción, percepción y retención llevan su propia bandera (`alCredito`, `activa`).
  const [datosAbiertos, setDatosAbiertos] = useState(false);
  const [operacionAbierta, setOperacionAbierta] = useState(false);
  const [masAbierto, setMasAbierto] = useState(false);
  const [correoAbierto, setCorreoAbierto] = useState(false);
  const botonesExtra = useRef<Partial<Record<Extra, HTMLButtonElement | null>>>({});
  // #312: anticipos. `esAnticipo` solo ayuda a describir la línea (SUNAT no marca la factura de anticipo); `anticiposAbierto` activa la búsqueda de los del cliente.
  const [esAnticipo, setEsAnticipo] = useState(false);
  const [anticiposAbierto, setAnticiposAbierto] = useState(false);
  const [anticiposElegidos, setAnticiposElegidos] = useState<AnticipoForm[]>([]);
  const [pais, setPais] = useState(plantilla?.cliente.pais ?? "");
  const [incoterm, setIncoterm] = useState("");
  const [paisDeUso, setPaisDeUso] = useState("");
  const paises = useMemo(() => listarPaises(locale), [locale]);
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
    // #557: sin el padrón (no figura, sin cargar, cupo, error) el nombre se escribe a mano: se despliegan los campos del cliente para que se vea dónde.
    if (consultaRuc && consultaRuc.tipo !== "encontrado" && !razonSocial.trim()) setEditandoCliente(true);
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
    const documento = docsBoleta.find((d) => d.codigo === c.tipo_doc);
    // La factura sólo lleva RUC: el selector de documentos (con DNI y otros) es de la boleta y la exportación.
    const admitido = usaSelectorDeDocumento ? documentosOfrecidos.some((d) => d.codigo === c.tipo_doc) : c.tipo_doc === "6";
    if (!admitido) {
      setAvisoCliente(
        interpolar(t.clienteNoAdmitido, {
          razon: c.razon_social,
          documento: documento?.etiqueta ?? interpolar(t.documentoCodigo, { codigo: c.tipo_doc }),
          destino: exportacion ? t.estaExportacion : t.una[tipo],
          motivo: !boleta && !exportacion ? t.facturaNecesitaRuc : "",
        }),
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
    setEmail(c.email ?? "");
    // Con correo, su panel se abre para que se vea a quién se le enviará; queda abierto aunque el siguiente cliente elegido no tenga.
    if (c.email) setCorreoAbierto(true);
    setClienteElegido(`${c.tipo_doc}/${c.num_doc}`);
    setTextoCliente("");
  }

  /** Si este comprobante admite ese documento como comprador: la factura (venta interna), solo RUC; la boleta y la exportación, los de su selector. */
  function admiteDocumento(codigo: string) {
    return usaSelectorDeDocumento ? documentosOfrecidos.some((d) => d.codigo === codigo) : codigo === "6";
  }

  /** Qué documento es lo escrito en el campo «Cliente»: 11 dígitos, un RUC; 8, un DNI (en boleta). Lo demás es un nombre para buscar en los guardados. */
  function documentoDelTexto(texto: string): string | null {
    const v = texto.trim();
    if (/^\d{11}$/.test(v) && admiteDocumento("6")) return "6";
    if (/^\d{8}$/.test(v) && admiteDocumento("1")) return "1";
    return null;
  }

  /** Toma lo escrito como el documento del comprador: llena tipo y número (y con un RUC, la consulta al padrón llena el nombre y la dirección). */
  function aplicarDocumento(codigo: string, numero: string) {
    setAvisoCliente(null);
    if (codigo !== tipoDoc) cambiarTipoDoc(codigo);
    else soltarAutocompletado();
    setNumDoc(numero);
    setTextoCliente("");
    // El DNI no se consulta y un RUC con el dígito verificador mal tampoco: el nombre se escribe a mano, así que los campos se despliegan.
    if (codigo !== "6" || !rucValido(numero)) setEditandoCliente(true);
  }

  /** Mientras se escribe: un RUC completo (11 dígitos, no hay documento numérico más largo) se toma al instante; un DNI espera a Enter o a salir del campo, porque son los primeros 8 de un RUC. */
  function escribirCliente(texto: string) {
    setTextoCliente(texto);
    if (documentoDelTexto(texto) === "6") aplicarDocumento("6", texto.trim());
  }

  function resolverTextoCliente(texto: string) {
    const codigo = documentoDelTexto(texto);
    if (codigo) aplicarDocumento(codigo, texto.trim());
  }

  /** «Ingresar a mano»: despliega los campos sin cliente elegido. En boleta pasa de «Clientes varios» al DNI, el documento más común. */
  function ingresarAMano() {
    if (boleta && sinDocumento) cambiarTipoDoc("1");
    setEditandoCliente(true);
  }

  /** «Cambiar» en la tarjeta (y descartar lo ingresado a mano): vuelve al campo «Cliente» vacío; la boleta, a «Clientes varios». */
  function limpiarCliente() {
    soltarAutocompletado();
    setTipoDoc(boleta ? SIN_DOCUMENTO : exportacion ? "0" : "6");
    setNumDoc("");
    setRazonSocial(boleta ? COMPRADOR_SIN_DOCUMENTO : "");
    setDireccion("");
    setPais("");
    // El correo que vino con un cliente guardado es de ese cliente: no se le manda el comprobante de otro.
    if (clienteElegido) setEmail("");
    setClienteElegido(null);
    setAvisoCliente(null);
    setTextoCliente("");
    setEditandoCliente(false);
    setEnfocarCliente(true);
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
  const documento = docsBoleta.find((d) => d.codigo === tipoDoc) ?? docsBoleta[0];
  // #557: hay un cliente que mostrar en la tarjeta: con documento, o en boleta sin documento con un nombre que no es el de relleno.
  const conCliente = sinDocumento ? razonSocial.trim() !== "" && razonSocial !== COMPRADOR_SIN_DOCUMENTO : numDoc.trim() !== "";
  const etiquetaCliente = boleta ? t.comprador : t.cliente;
  const redondeo = redondeoDe(redondeoTexto, messages);
  const exportacion = esExportacion(operacion);
  const conDetraccion = detraccion.activa && operacion === "0101";
  const conPercepcion = percepcion.activa && operacion === "0101" && !conDetraccion;
  const conRetencion = retencion.activa && !boleta && operacion === "0101" && !conDetraccion && !conPercepcion;
  const operacionEnviada = conDetraccion ? operacionDeDetraccion(detraccion.codigo) : conPercepcion ? OPERACION_PERCEPCION : operacion;
  const errorDetraccion = conDetraccion ? errorDeDetraccion(detraccion, moneda, messages) : null;
  const errorReferencias = errorDeReferencias(referencias, boleta, messages);
  const documentoDelCliente = sinDocumento ? "" : numDoc.trim();
  const disponibles = useAnticiposDisponibles(anticiposAbierto || anticiposElegidos.length > 0, documentoDelCliente, moneda);
  // Lo elegido para otro cliente u otra moneda ya no está entre los candidatos y no se manda.
  const anticiposVigentes = disponibles.estado === "listo" ? anticiposElegidos.filter((a) => disponibles.candidatos.some((c) => c.serie === a.serie && c.numero === a.numero)) : [];
  const errorAnticipos = errorDeAnticipos(anticiposVigentes, disponibles.candidatos, messages);
  const errorTipoCambio = errorDeTipoCambio(tipoCambio, moneda, messages);
  const errorRetencion = conRetencion ? errorDeRetencion(retencion, messages) : null;
  const errorPercepcion = conPercepcion ? errorDePercepcion(percepcion, { moneda, alCredito: alCredito && !boleta }, messages) : null;
  const errorExportacion = errorDeExportacion({ operacion, incoterm, paisDeUso, paisDelComprador: pais }, boleta, messages);
  // En una exportación el comprador es del exterior: se elige su documento (como en la boleta) en vez de pedir un RUC.
  const usaSelectorDeDocumento = boleta || exportacion;
  const documentosOfrecidos = exportacion
    ? [...docsBoleta.filter((d) => documentosDeExportacion(operacion, boleta).includes(d.codigo)), ...(boleta ? docsBoleta.filter((d) => d.codigo === SIN_DOCUMENTO) : [])]
    : docsBoleta;

  /** Al pasar a una exportación las líneas son de afectación 40, la moneda suele ser extranjera y el comprador no lleva RUC; al volver, todo regresa a la venta interna. */
  function cambiarOperacion(nueva: string) {
    const eraExp = esExportacion(operacion);
    const esExp = esExportacion(nueva);
    setOperacion(nueva);
    setAvisoProducto(null);
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
  const incompatibilidad = incompatibilidadDeTributos(lineasCompletas, operacion, messages);
  const hayAjusteInvalido =
    errorDeAjuste(descuentoGlobal, "descuento", messages) !== null || errorDeAjuste(cargoGlobal, "cargo", messages) !== null ||
    lineasCompletas.some((l) => errorDeAjuste(l.descuento, "descuento", messages) !== null || errorDeAjuste(l.cargo, "cargo", messages) !== null || errorDeIsc(l.isc, messages) !== null) ||
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
      tipo_cambio: tipoCambioAApi(tipoCambio, moneda, messages),
      tipo_operacion: operacionEnviada === "0101" ? undefined : operacionEnviada,
      detraccion: conDetraccion ? detraccionAApi(detraccion, moneda, messages) : undefined,
      ...(paraCalcular ? {} : referenciasAApi(referencias, boleta, messages)),
      anticipos: anticiposAApi(anticiposVigentes, disponibles.candidatos, messages),
      retencion_igv: conRetencion ? retencionAApi(retencion, messages) : undefined,
      percepcion: conPercepcion ? percepcionAApi(percepcion, { moneda, alCredito: alCredito && !boleta }, messages) : undefined,
      exportacion: exportacion && (incoterm || paisDeUso) ? { incoterm: incoterm || undefined, pais_uso: paisDeUso || undefined } : undefined,
      cliente: paraCalcular
        ? exportacion
          ? // eslint-disable-next-line khipu/no-literal-de-ui -- comprador de relleno para /calcular: no se muestra ni se emite
            { tipo_doc: "0", num_doc: "EXT123456", razon_social: "Cliente", pais: pais || "US" }
          : boleta
            ? // eslint-disable-next-line khipu/no-literal-de-ui -- comprador de relleno para /calcular: no se muestra ni se emite
              { tipo_doc: "1", num_doc: "12345678", razon_social: "Comprador" }
            : // eslint-disable-next-line khipu/no-literal-de-ui -- comprador de relleno para /calcular: no se muestra ni se emite
              { tipo_doc: "6", num_doc: "20131312955", razon_social: "Cliente" }
        : {
            tipo_doc: tipoDoc,
            num_doc: sinDocumento ? SIN_DOCUMENTO : numDoc.trim(),
            razon_social: razonSocial.trim(),
            direccion: direccion.trim() || undefined,
            pais: exportacion && pais ? pais : undefined,
            email: email.trim() || undefined,
          },
      items: lineasCompletas.map((l) => ({
        codigo: l.codigo,
        codigo_sunat: l.codigoSunat,
        descripcion: l.descripcion.trim(),
        unidad: l.unidad,
        cantidad: l.cantidad,
        precio_unitario: l.precioUnitario ?? 0,
        tipo_afectacion_igv: l.tipoAfectacionIgv,
        descuento: ajusteAApi(l.descuento, "descuento", messages),
        cargos: ajusteAApi(l.cargo, "cargo", messages) ? [ajusteAApi(l.cargo, "cargo", messages)] : undefined,
        isc: iscAApi(l.isc, messages),
        icbper: l.icbper ? true : undefined,
      })),
      descuento_global: ajusteAApi(descuentoGlobal, "descuento", messages),
      cargos: ajusteAApi(cargoGlobal, "cargo", messages) ? [ajusteAApi(cargoGlobal, "cargo", messages)] : undefined,
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
  const errorCredito = alCredito && !boleta ? errorDeCredito(credito, netoBase, fecha, messages) : null;
  const calculoCredito = useCalculo(completo && alCredito && !boleta && errorCredito === null ? armarCuerpo(true, netoBase) : null);
  const calculo = alCredito && !boleta && calculoCredito.estado !== "inactivo" ? calculoCredito : calculoBase;
  // Lo que se muestra en el pie: los importes del backend cuando respondió; si no, la aproximación local (sin descuentos, cargos, ISC ni redondeo).
  const delServidor = calculo.estado === "listo" ? calculo.totales : null;
  const mostrados = delServidor
    ? { gravado: delServidor.gravado, exonerado: delServidor.exonerado, inafecto: delServidor.inafecto, gratuito: delServidor.gratuito ?? 0, igv: delServidor.igv, total: delServidor.total }
    : { ...totales, total: totales.total + (redondeo.valor ?? 0) };
  // Algo del cliente o de las líneas está escrito: cerrar el diálogo (Esc, la X) lo perdería, y el diálogo pide confirmación (#117).
  const hayDatos =
    textoCliente.trim() !== "" ||
    numDoc.trim() !== "" ||
    (razonSocial.trim() !== "" && razonSocial !== COMPRADOR_SIN_DOCUMENTO) ||
    direccion.trim() !== "" ||
    email.trim() !== "" ||
    lineas.some((l) => l.descripcion.trim() !== "" || l.precioUnitario !== null);
  useEffect(() => {
    onCambioDatos?.(hayDatos);
  }, [hayDatos, onCambioDatos]);
  // IVAP o exportación mientras el backend no responde: la previsualización local no las suma y mostraría S/ 0.00 (#117).
  const sinImporteLocal = !delServidor && totales.noContempladas.length > 0;
  // Con tipo de cambio el tope de S/ 700 se mide en soles; sin él no hay cómo comparar otra moneda y el backend exige el documento: aquí se avisa antes.
  const superaTopeSinDocumento = sinDocumento && superaElTope(mostrados.total, moneda, tipoCambioAApi(tipoCambio, moneda, messages));

  // #557: qué «+» se ofrecen y cuáles están activos. Detracción, percepción y retención son de la venta interna (0101) y se excluyen entre sí como antes: activar la
  // detracción apaga las otras dos, y la percepción a la retención; mientras una más fuerte está activa, la otra no se ofrece. La retención y el crédito, solo en factura.
  const ventaInterna = operacion === "0101";
  const hayMasOpciones = redondeoTexto !== "" || descuentoGlobal.valor !== "" || cargoGlobal.valor !== "" || fechaVencimiento !== "";
  const hayReferencias = referencias.ordenCompra !== "" || referencias.guias.length > 0 || referencias.documentos.length > 0 || referencias.observaciones !== "";
  const activo: Record<Extra, boolean> = {
    credito: alCredito,
    detraccion: detraccion.activa,
    percepcion: percepcion.activa,
    retencion: retencion.activa,
    operacion: operacionAbierta || !ventaInterna,
    masOpciones: masAbierto || hayMasOpciones,
    anticipos: anticiposAbierto || anticiposElegidos.length > 0 || esAnticipo,
    referencias: datosAbiertos || hayReferencias,
    correo: correoAbierto || email !== "",
  };
  // #560: sin «Mostrar opciones avanzadas» solo los básicos, salvo un avanzado que ya trae datos (una copia), que no se esconde.
  const { ofrecidos, hayOcultos } = extrasOfrecidos({ tipo, operacion, avanzadas: opcionesAvanzadas, activos: activo });
  const visible = (e: Extra) => ofrecidos.includes(e) && activo[e];
  // Lo que dice cada «+» al pasar el puntero, y el título de su panel.
  const tituloExtra: Record<Extra, string> = {
    credito: t.alCredito,
    detraccion: t.detraccionCheck,
    percepcion: t.percepcionCheck,
    retencion: t.retencionCheck,
    operacion: t.tipoOperacion,
    masOpciones: t.masOpciones,
    anticipos: t.anticipos,
    referencias: t.datosAdicionales,
    correo: t.correo,
  };

  function activar(e: Extra) {
    switch (e) {
      case "credito":
        return setAlCredito(true);
      case "detraccion":
        setDetraccion((d) => ({ ...d, activa: true, cuenta: d.cuenta || cuentaDetracciones || "" }));
        setPercepcion(PERCEPCION_VACIA);
        return setRetencion(RETENCION_VACIA);
      case "percepcion":
        setPercepcion((p) => ({ ...p, activa: true }));
        return setRetencion(RETENCION_VACIA);
      case "retencion":
        return setRetencion((r) => ({ ...r, activa: true }));
      case "operacion":
        return setOperacionAbierta(true);
      case "masOpciones":
        return setMasAbierto(true);
      case "anticipos":
        return setAnticiposAbierto(true);
      case "referencias":
        return setDatosAbiertos(true);
      case "correo":
        return setCorreoAbierto(true);
    }
  }

  /** Apaga el panel y limpia lo suyo: lo quitado no se calcula ni se envía. */
  function quitar(e: Extra) {
    switch (e) {
      case "credito":
        setAlCredito(false);
        return setCredito(CREDITO_VACIO);
      case "detraccion":
        return setDetraccion(DETRACCION_VACIA);
      case "percepcion":
        return setPercepcion(PERCEPCION_VACIA);
      case "retencion":
        return setRetencion(RETENCION_VACIA);
      case "operacion":
        if (!ventaInterna) cambiarOperacion("0101");
        return setOperacionAbierta(false);
      case "masOpciones":
        setRedondeoTexto("");
        setDescuentoGlobal(AJUSTE_VACIO);
        setCargoGlobal(AJUSTE_VACIO);
        setFechaVencimiento("");
        return setMasAbierto(false);
      case "anticipos":
        setEsAnticipo(false);
        setAnticiposElegidos([]);
        return setAnticiposAbierto(false);
      case "referencias":
        setReferencias(REFERENCIAS_VACIAS);
        return setDatosAbiertos(false);
      case "correo":
        setEmail("");
        return setCorreoAbierto(false);
    }
  }

  /** Quitar desde el panel: el botón desaparece con él, así que el foco vuelve a su «+» (que sigue en la fila) en vez de caer al cuerpo de la página. */
  function quitarDesdeElPanel(e: Extra) {
    quitar(e);
    botonesExtra.current[e]?.focus();
  }

  // El total que dice el botón de emitir: el del pie, salvo que no haya líneas completas o que el importe todavía no se conozca (IVAP o exportación sin respuesta del backend).
  const totalConocido = lineasCompletas.length > 0 && !sinImporteLocal;
  const opcionesSerie = seriesDelTipo.map((s) => ({ value: s.serie, label: `${s.serie} · ${s.ultimo_numero > 0 ? interpolar(t.ultimoEmitido, { n: s.ultimo_numero }) : t.sinEmitir}` }));
  const opcionesMoneda = monedas(messages).map((m) => ({ value: m.codigo, label: m.etiqueta }));

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

  /**
   * #320: una línea con los datos de un producto guardado. Si la única línea está vacía se llena; si no, se agrega una. Una exportación lleva todas sus líneas en afectación 40, y un
   * producto de exportación no sirve en una venta interna: se dice por qué en vez de agregarlo.
   */
  function agregarProducto(p: ProductoGuardado) {
    if (p.tipo_afectacion_igv === "40" && !exportacion) {
      setAvisoProducto(interpolar(t.productoExportacion, { descripcion: p.descripcion }));
      return;
    }
    // Una gratuita no se cobra y la exportación sí: forzarla a 40 la convertiría en una línea cobrada.
    if (exportacion && esGratuita(p.tipo_afectacion_igv)) {
      setAvisoProducto(interpolar(t.productoGratuito, { descripcion: p.descripcion, afectacion: p.tipo_afectacion_igv }));
      return;
    }
    const vacia = lineas.length === 1 && lineas[0].descripcion.trim() === "" && lineas[0].precioUnitario === null;
    if (!vacia && lineas.length >= MAX_LINEAS) {
      setAvisoProducto(interpolar(t.maxItems, { max: MAX_LINEAS }));
      return;
    }
    // En una exportación la línea va sin IGV: un producto gravado conserva su precio tal cual, que ya incluía el impuesto.
    const conIgv = exportacion && (p.tipo_afectacion_igv === "10" || p.tipo_afectacion_igv === "17");
    setAvisoProducto(conIgv ? interpolar(t.productoConIgv, { descripcion: p.descripcion, precio: p.precio_unitario }) : null);
    const nueva: Linea = {
      ...LINEA_VACIA,
      descripcion: p.descripcion,
      unidad: p.unidad,
      tipoAfectacionIgv: exportacion ? "40" : p.tipo_afectacion_igv,
      precioUnitario: p.precio_unitario,
      codigo: p.codigo ?? undefined,
      codigoSunat: p.codigo_sunat ?? undefined,
    };
    setLineas((prev) => (vacia ? [nueva] : [...prev, nueva]));
  }

  function actualizar(i: number, cambio: Partial<Linea>) {
    // Los códigos son los del producto guardado que se eligió: si se reescribe la descripción ya es otro ítem y no deben viajar en el XML.
    const sinCodigos: Partial<Linea> = "descripcion" in cambio ? { codigo: undefined, codigoSunat: undefined } : {};
    setLineas((prev) => prev.map((l, n) => (n === i ? { ...l, ...sinCodigos, ...cambio } : l)));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    // `EntradaMonto` redondea en cada pulsación (el valor enviado ya está fijado) pero muestra el texto crudo
    // mientras tiene el foco. Soltarlo acá repinta el valor redondeado; en el camino feliz se navega antes de
    // verlo, así que solo importa si la emisión falla y el formulario sigue en pantalla. Cosmético, no de datos.
    (document.activeElement as HTMLElement | null)?.blur();
    setError(null);
    setLlevaAlPlan(false);
    // #557: los campos del comprador van plegados, así que el navegador no los valida: lo que falta del cliente se dice aquí. Lo escrito en «Cliente» que no llegó a
    // ser un cliente (un nombre sin elegir de la lista, un número incompleto) no se emite como «Clientes varios» sin avisar.
    if (textoCliente.trim() !== "" && !conCliente) {
      setError(t.errores.clienteSinResolver);
      return;
    }
    // Con los campos desplegados los valida el navegador (`required`, `pattern`, `minLength`), como siempre: esto cubre solo los plegados.
    if (!editandoCliente && !sinDocumento && numDoc.trim() === "") {
      setError(t.errores.sinCliente);
      return;
    }
    if (!editandoCliente && (!new RegExp(`^(?:${PATRON_NOMBRE})$`).test(razonSocial) || razonSocial.length > 1500)) {
      setEditandoCliente(true);
      setError(t.errores.nombreDelCliente);
      return;
    }
    if (lineasCompletas.length === 0) {
      setError(t.errores.sinItems);
      return;
    }
    if (superaTopeSinDocumento) {
      setError(interpolar(t.errores.topeSinDocumento, { tope: TOPE_SIN_DOCUMENTO }));
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
      setError(incompatibilidad ?? errorExportacion ?? errorDetraccion ?? errorPercepcion ?? errorAnticipos ?? errorRetencion ?? errorTipoCambio ?? t.errores.corrijaAjustes);
      return;
    }
    if (errorCredito) {
      setError(errorCredito);
      return;
    }
    if (fechaVencimiento && fechaVencimiento < fecha) {
      setError(t.errores.vencimientoAnterior);
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
      setError(interpolar(t.errores.reintentar, { causa: seVencioLaEspera(res) ? t.errores.esperaVencida : t.errores.conexionCortada, nombre }));
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
    if (!sinDocumento && numDoc.trim() !== "" && clienteElegido !== `${tipoDoc}/${numDoc.trim()}`) {
      void apiRequest(`/api/proxy/clientes/documento/${encodeURIComponent(tipoDoc)}/${encodeURIComponent(numDoc.trim())}`, {
        method: "PUT",
        body: { razon_social: razonSocial.trim(), direccion: direccion.trim() || undefined, pais: exportacion && pais ? pais : undefined, email: email.trim() || undefined },
      }).catch(() => undefined);
    }
    onEmitido?.();
    router.push(`/comprobantes/${res.datos.id}`);
    router.refresh();
  }

  if (seriesDelTipo.length === 0) {
    return (
      <div className="flex flex-col gap-3 px-5 py-4">
        {selectorDeTipo ? <div>{selectorDeTipo}</div> : null}
        <Alerta tono="aviso" titulo={interpolar(t.sinSeries.titulo, { nombre })}>
          {interpolar(t.sinSeries.texto, { tipo: TIPO_DE_SERIE[tipo], patron: boleta ? "B###" : "F###" })}
        </Alerta>
        <button type="button" className={cn(ACCION_SECUNDARIA, "self-end")} onClick={() => router.push("/series")}>
          {t.sinSeries.irASeries}
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} onKeyDown={sinEnvioImplicito} className="flex min-h-0 flex-1 flex-col" data-testid="form-nuevo-comprobante">
      {/* El scroll vive solo en la lista de ítems: así la serie, la fecha y el cliente quedan siempre a la vista
          por largo que sea el comprobante. */}
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-5 py-4">
      {/* #557: una sola línea con el tipo, la serie, la fecha y la moneda, como las píldoras de la barra de filtros del design system. Las ayudas largas van en el
          `title` y, para un lector de pantalla, en la descripción del control. */}
      <div className="flex flex-wrap items-start gap-2">
        {selectorDeTipo}
        <Select items={opcionesSerie} value={serie} onValueChange={(v) => setSerie((v as string | null) ?? "")}>
          <SelectTrigger id="nc-serie" className={cn(CONTROL_FILTRO, "w-auto pl-3 tabular-nums")} aria-label={t.serie} aria-describedby="nc-serie-ayuda" title={t.ayudaSerie}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {opcionesSerie.map(({ value, label }) => (
              <SelectItem key={value} value={value} className="tabular-nums">
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <span id="nc-serie-ayuda" className="sr-only">
          {t.ayudaSerie}
        </span>

        <span className="inline-flex" title={interpolar(t.ayudaFecha, { dias: PLAZO_ENVIO[tipo].dias })}>
          <label htmlFor="nc-fecha" className="sr-only">
            {t.fechaEmision}
          </label>
          {/* Los dos extremos los rechaza el backend (futura y fuera del plazo de envío: 3 días la factura, 2108; 7 la boleta, 1079), así que
              el calendario los cierra acá en vez de gastar un viaje para que lo diga SUNAT (#292). */}
          {/* `||` y no `??`: un input de fecha vaciado emite `""`, no `null`, así que `??` dejaba pasar el vacío
              hasta el `@NotNull LocalDate` del backend y la emisión moría con un error de deserialización. */}
          <EntradaFecha id="nc-fecha" enBarra valor={fecha} onCambio={(v) => setFecha(v || hoy)} min={fechaMinimaDeEmision(tipo, hoy)} max={hoy} describidoPor="nc-fecha-ayuda" className="w-40" />
          <span id="nc-fecha-ayuda" className="sr-only">
            {interpolar(t.ayudaFecha, { dias: PLAZO_ENVIO[tipo].dias })}
          </span>
        </span>

        <Select items={opcionesMoneda} value={moneda} onValueChange={(v) => { setMoneda((v as string | null) ?? ""); setTipoCambio(""); }}>
          <SelectTrigger id="nc-moneda" className={cn(CONTROL_FILTRO, "w-auto pl-3")} aria-label={t.moneda}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {opcionesMoneda.map(({ value, label }) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {moneda === "PEN" ? null : (
          <Campo id="nc-tipo-cambio" etiqueta={t.tipoCambio} opcional error={errorTipoCambio ?? undefined} className="gap-1 [&>label]:sr-only">
            <input
              id="nc-tipo-cambio"
              value={tipoCambio}
              onChange={(e) => setTipoCambio(e.target.value)}
              inputMode="decimal"
              placeholder={t.tipoCambio}
              title={interpolar(t.ayudaTipoCambio, { moneda })}
              aria-describedby={errorTipoCambio ? "nc-tipo-cambio-error" : "nc-tipo-cambio-ayuda"}
              aria-invalid={errorTipoCambio !== null}
              className={cn(CONTROL_FILTRO, FOCO_CAMPO, ERROR_CAMPO, "w-32 px-3 font-mono tabular-nums outline-none placeholder:font-sans placeholder:font-normal placeholder:text-texto-terciario")}
            />
            <span id="nc-tipo-cambio-ayuda" className="sr-only">
              {interpolar(t.ayudaTipoCambio, { moneda })}
            </span>
          </Campo>
        )}
        {soloPorResumen(tipo, fecha, hoy) ? (
          <p className={cn(AYUDA_CAMPO, "basis-full")} data-testid="aviso-resumen-diario" role="status">
            {t.avisoResumen}
          </p>
        ) : null}
      </div>

      {/* #557: el cliente en un solo campo. Se escribe el DNI, el RUC o el nombre: sugiere los guardados y un RUC (o, en boleta, un DNI) completo se toma como
          documento y, si es RUC, se consulta el padrón. Elegido o resuelto, una tarjeta dice quién es; sus datos se corrigen en «Editar datos», que despliega los
          campos de siempre. En boleta el campo vacío es «Clientes varios» (sin documento, hasta el tope). */}
      <section className="grid gap-3">
        <h3 className={ETIQUETA_CAMPO}>{etiquetaCliente}</h3>
        {conCliente ? (
          <TarjetaDeCliente
            nombre={razonSocial}
            documento={sinDocumento ? t.sinDocumentoCorto : `${documento.etiqueta} ${numDoc.trim()}`}
            direccion={boleta ? undefined : direccion.trim() || undefined}
            estado={
              conRuc ? (
                <EstadoConsultaRuc
                  consultando={consultandoRuc}
                  resultado={consultaRuc}
                  nombreActual={razonSocial}
                  onReintentar={reintentarConsultaRuc}
                  onUsarNombre={(n) => { setRazonSocial(n); setAuto((a) => ({ ...a, razon: n })); }}
                />
              ) : undefined
            }
            editando={editandoCliente}
            idDatos="nc-datos-cliente"
            onEditar={() => setEditandoCliente((e) => !e)}
            onCambiar={limpiarCliente}
          />
        ) : (
          <div className="grid gap-1">
            <BuscadorDeClientes
              etiqueta={etiquetaCliente}
              placeholder={boleta ? t.placeholderClienteBoleta : exportacion ? t.placeholderClienteExportacion : t.placeholderClienteFactura}
              texto={textoCliente}
              onTexto={escribirCliente}
              onEnter={resolverTextoCliente}
              onBlur={resolverTextoCliente}
              autoFocus={enfocarCliente}
              describidoPor="nc-cliente-ayuda"
              onElegir={elegirClienteGuardado}
            />
            <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
              <p id="nc-cliente-ayuda" className={AYUDA_CAMPO}>
                {boleta ? interpolar(t.ayudaClienteBoleta, { tope: TOPE_SIN_DOCUMENTO }) : exportacion ? t.ayudaClienteExportacion : t.ayudaClienteFactura}
              </p>
              <button type="button" className="text-etiqueta font-medium text-primary hover:underline" aria-expanded={editandoCliente} onClick={editandoCliente ? limpiarCliente : ingresarAMano}>
                {editandoCliente ? t.descartarDatosCliente : t.ingresarAMano}
              </button>
            </div>
          </div>
        )}
        {avisoCliente ? (
          <p role="status" className="text-control text-warning-foreground" data-testid="aviso-cliente-guardado">
            {avisoCliente}
          </p>
        ) : null}
        {superaTopeSinDocumento ? (
          <p data-testid="aviso-sin-documento" role="status" className={cn(AYUDA_CAMPO, "text-warning-foreground")}>
            {interpolar(t.avisoTope, { tope: TOPE_SIN_DOCUMENTO, extra: moneda !== "PEN" && tipoCambioAApi(tipoCambio, moneda, messages) === undefined ? t.avisoTopeSinTipoCambio : "" })}
          </p>
        ) : null}
        {!editandoCliente ? null : usaSelectorDeDocumento ? (
          <div id="nc-datos-cliente" className="grid gap-4 sm:grid-cols-[200px_180px_1fr]">
            <Campo id="nc-tipo-doc" etiqueta={t.documentoComprador}>
              <SelectCampo id="nc-tipo-doc" valor={tipoDoc} onCambio={cambiarTipoDoc} opciones={documentosOfrecidos.map((d) => ({ value: d.codigo, label: d.etiqueta }))} />
            </Campo>
            {sinDocumento ? (
              <p className={cn(AYUDA_CAMPO, "self-center")}>{interpolar(t.sinDocumentoAyuda, { tope: TOPE_SIN_DOCUMENTO })}</p>
            ) : (
              <Campo id="nc-num-doc" etiqueta={t.numeroDocumento}>
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
                  className={cn(CAMPO, "font-mono tabular-nums")}
                />
              </Campo>
            )}
            <Campo id="nc-razon" etiqueta={boleta ? t.nombreComprador : t.razonSocial}>
              <input
                id="nc-razon"
                value={razonSocial}
                onChange={(e) => setRazonSocial(e.target.value)}
                placeholder={t.placeholderComprador}
                required
                // `Receptor` exige de 3 a 1500 caracteres también en boleta, incluso sin documento (regla 2022).
                minLength={3}
                maxLength={1500}
                pattern={PATRON_NOMBRE}
                title={t.tituloNombre}
                className={CAMPO}
              />
            </Campo>
            {exportacion && !boleta ? (
              <div className="sm:col-span-3">
                <Campo id="nc-direccion" etiqueta={t.direccion} opcional>
                  <input id="nc-direccion" value={direccion} onChange={(e) => setDireccion(e.target.value)} placeholder={t.placeholderDireccionExterior} className={CAMPO} />
                </Campo>
              </div>
            ) : null}
          </div>
        ) : (
        <div id="nc-datos-cliente" className="grid gap-4 sm:grid-cols-[180px_1fr]">
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
              title={messages.documentos.formato.ruc}
              className={cn(CAMPO, "font-mono tabular-nums")}
            />
          </Campo>
          <Campo id="nc-razon" etiqueta={t.razonSocial}>
            <input
              id="nc-razon"
              value={razonSocial}
              onChange={(e) => setRazonSocial(e.target.value)}
              placeholder={t.placeholderRazonSocial}
              required
              // `Receptor` exige de 3 a 1500 caracteres (regla 2022).
              minLength={3}
              maxLength={1500}
              pattern={PATRON_NOMBRE}
              title={t.tituloNombre}
              className={CAMPO}
            />
          </Campo>
          <div className="sm:col-span-2">
            <Campo id="nc-direccion" etiqueta={t.direccion} opcional>
              <input id="nc-direccion" value={direccion} onChange={(e) => setDireccion(e.target.value)} placeholder={t.placeholderDireccion} className={CAMPO} />
            </Campo>
          </div>
        </div>
        )}
      </section>

      <section className="flex min-h-[11rem] flex-1 flex-col gap-3">
        <div className="flex items-baseline justify-between gap-3">
          <h3 className={ETIQUETA_CAMPO}>
            {t.items} <span className="font-normal text-muted-foreground">· {lineas.length}</span>
          </h3>
          {/* El número de orden del ítem es de hasta 3 dígitos: pasado el tope el backend responde 422 (2023). */}
          <button
            type="button"
            onClick={() => setLineas((p) => [...p, { ...LINEA_VACIA }])}
            disabled={lineas.length >= MAX_LINEAS}
            title={lineas.length >= MAX_LINEAS ? interpolar(t.maxItemsTitulo, { max: MAX_LINEAS }) : undefined}
            className="inline-flex items-center gap-1 text-control font-medium text-primary hover:underline disabled:cursor-not-allowed disabled:opacity-50 disabled:no-underline"
          >
            <PlusIcon weight="bold" className="size-3.5" />
            {t.agregarItem}
          </button>
        </div>
        <BuscadorDeProductos onElegir={agregarProducto} />
        {avisoProducto ? (
          <p role="status" className="text-control text-warning-foreground" data-testid="aviso-producto-guardado">
            {avisoProducto}
          </p>
        ) : null}

        {/* Tabla compacta: encabezados una sola vez y filas de alto reducido; las etiquetas por fila quedan
            solo para lectores de pantalla. */}
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-border">
          <div className="hidden grid-cols-[minmax(0,1fr)_124px_188px_72px] gap-3 border-b border-border/60 bg-muted/40 px-3 py-2 sm:grid">
            <span className={AYUDA_CAMPO}>{t.descripcion}</span>
            <span className={AYUDA_CAMPO}>{t.cantidad}</span>
            <span className={AYUDA_CAMPO} data-testid="encabezado-precio">{etiquetaDelPrecio(lineas.every((l) => l.tipoAfectacionIgv === lineas[0].tipoAfectacionIgv) ? lineas[0].tipoAfectacionIgv : "", messages)}</span>
            <span className="sr-only">{t.acciones}</span>
          </div>

          <div className="min-h-0 flex-1 divide-y divide-border/60 overflow-y-auto">
          {lineas.map((linea, i) => (
            <div key={i} className="px-3 py-2">
              <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_124px_188px_72px] sm:items-center">
                <Campo id={`nc-desc-${i}`} etiqueta={t.descripcion} className="[&>label]:sr-only">
                  <input
                    id={`nc-desc-${i}`}
                    value={linea.descripcion}
                    onChange={(e) => actualizar(i, { descripcion: e.target.value })}
                    placeholder={t.placeholderDescripcion}
                    maxLength={500}
                    className={CAMPO}
                  />
                </Campo>
                <Campo id={`nc-cant-${i}`} etiqueta={t.cantidad} className="[&>label]:sr-only">
                  {/* `paso="any"`: sin él, el input oculto de base-ui queda en `stepMismatch` con cualquier cantidad no
                      entera y el navegador aborta el submit sin decir nada. Kilos, horas y metros son medio SUNAT. */}
                  <StepperNumerico id={`nc-cant-${i}`} valor={linea.cantidad} onCambio={(v) => actualizar(i, { cantidad: v })} min={0} paso="any" variante="filtro" />
                </Campo>
                <Campo id={`nc-precio-${i}`} etiqueta={etiquetaDelPrecio(linea.tipoAfectacionIgv, messages)} className="[&>label]:sr-only">
                  <EntradaMonto id={`nc-precio-${i}`} valor={linea.precioUnitario} onCambio={(v) => actualizar(i, { precioUnitario: v })} moneda={moneda} variante="filtro" />
                </Campo>
                <div className="flex items-center justify-end gap-0.5">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => setDetalle(detalle === i ? null : i)}
                    title={t.unidadYAfectacion}
                    aria-label={interpolar(t.masOpcionesItem, { n: i + 1 })}
                    aria-expanded={detalle === i}
                    className="text-muted-foreground"
                  >
                    <SlidersHorizontalIcon className="size-4" />
                  </Button>
                  {/* `pointer-events-auto` al deshabilitarse: el `title` («mínimo un ítem») tiene que seguir apareciendo al pasar por encima. */}
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => quitarLinea(i)}
                    disabled={lineas.length === 1}
                    title={lineas.length === 1 ? t.minimoUnItem : t.quitarItem}
                    aria-label={interpolar(t.quitarItemN, { n: i + 1 })}
                    className="text-muted-foreground hover:text-destructive disabled:pointer-events-auto disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-muted-foreground"
                  >
                    <TrashIcon className="size-4" />
                  </Button>
                </div>
              </div>

              {detalle === i ? (
                <div className="mt-3 grid gap-3 rounded-lg bg-muted/40 px-3 py-3 sm:grid-cols-2">
                  <Campo id={`nc-unidad-${i}`} etiqueta={t.unidadMedida} ayuda={t.catalogo03}>
                    {/* Catálogo 03 (cientos de unidades): se escribe para buscar. Vaciar el texto no deja la línea sin unidad: se queda la que tenía. */}
                    <Combobox
                      id={`nc-unidad-${i}`}
                      items={
                        (unidades ?? []).length === 0
                          ? [{ value: linea.unidad, label: linea.unidad }]
                          : (unidades ?? []).map((u) => ({ value: u.codigo, label: `${u.codigo} · ${u.descripcion}` }))
                      }
                      valor={linea.unidad}
                      onCambio={(v) => {
                        if (v) actualizar(i, { unidad: v });
                      }}
                      vacio={messages.comun.sinResultados}
                      etiquetaAbrir={messages.comun.abrirOpciones}
                      className="font-mono"
                    />
                  </Campo>
                  <Campo id={`nc-afect-${i}`} etiqueta={t.afectacionIgv} ayuda={t.catalogo07}>
                    <SelectCampo
                      id={`nc-afect-${i}`}
                      valor={linea.tipoAfectacionIgv}
                      onCambio={(v) => actualizar(i, { tipoAfectacionIgv: v })}
                      opciones={
                        exportacion
                          ? [{ value: "40", label: t.exportacion40 }]
                          : gruposAfectacion(messages).flatMap((g) => g.opciones.map((a) => ({ value: a.codigo, label: `${a.codigo} · ${a.etiqueta}`, grupo: g.grupo })))
                      }
                    />
                  </Campo>
                  {/* #298: ISC (catálogo 08) y bolsas de plástico (ICBPER). El precio que se escribe ya los incluye; el importe de cada tributo lo calcula el backend. */}
                  <fieldset className="grid content-start gap-1.5">
                    <legend className="mb-1 text-control font-medium text-foreground">{t.iscItem}</legend>
                    <Campo id={`nc-isc-sistema-${i}`} etiqueta={t.sistemaIsc} className="[&>label]:sr-only">
                      <SelectCampo
                        id={`nc-isc-sistema-${i}`}
                        valor={linea.isc.sistema}
                        onCambio={(v) => actualizar(i, { isc: { ...ISC_VACIO, sistema: v as IscForm["sistema"] } })}
                        opciones={[
                          { value: "", label: t.sinIsc },
                          { value: "01", label: t.isc01 },
                          { value: "02", label: t.isc02 },
                          { value: "03", label: t.isc03 },
                        ]}
                      />
                    </Campo>
                    {linea.isc.sistema === "02" ? (
                      <Campo id={`nc-isc-monto-${i}`} etiqueta={t.montoIsc} className="[&>label]:sr-only" error={errorDeIsc(linea.isc, messages) ?? undefined}>
                        <input id={`nc-isc-monto-${i}`} value={linea.isc.montoUnitario} onChange={(e) => actualizar(i, { isc: { ...linea.isc, montoUnitario: e.target.value } })} inputMode="decimal" placeholder={t.placeholderMontoIsc} aria-invalid={errorDeIsc(linea.isc, messages) !== null} className={cn(CAMPO, "font-mono tabular-nums")} />
                      </Campo>
                    ) : linea.isc.sistema !== "" ? (
                      <>
                        <Campo id={`nc-isc-tasa-${i}`} etiqueta={t.tasaIsc} className="[&>label]:sr-only" error={linea.isc.sistema === "01" ? (errorDeIsc(linea.isc, messages) ?? undefined) : undefined}>
                          <input id={`nc-isc-tasa-${i}`} value={linea.isc.tasa} onChange={(e) => actualizar(i, { isc: { ...linea.isc, tasa: e.target.value } })} inputMode="decimal" placeholder={t.placeholderTasaIsc} aria-invalid={errorDeIsc(linea.isc, messages) !== null} className={cn(CAMPO, "font-mono tabular-nums")} />
                        </Campo>
                        {linea.isc.sistema === "03" ? (
                          <Campo id={`nc-isc-pvp-${i}`} etiqueta={t.pvp} className="[&>label]:sr-only" error={errorDeIsc(linea.isc, messages) ?? undefined}>
                            <input id={`nc-isc-pvp-${i}`} value={linea.isc.basePvp} onChange={(e) => actualizar(i, { isc: { ...linea.isc, basePvp: e.target.value } })} inputMode="decimal" placeholder={t.placeholderPvp} aria-invalid={errorDeIsc(linea.isc, messages) !== null} className={cn(CAMPO, "font-mono tabular-nums")} />
                          </Campo>
                        ) : null}
                      </>
                    ) : null}
                    <Casilla
                      id={`nc-icbper-${i}`}
                      etiqueta={t.bolsas}
                      checked={linea.icbper}
                      onCheckedChange={(v) => actualizar(i, v ? { icbper: true, unidad: "NIU" } : { icbper: false })}
                      className="mt-1"
                    />
                    <p className={AYUDA_CAMPO}>{t.bolsasAyuda}</p>
                  </fieldset>
                  {/* #310: descuento y cargo de la línea (catálogo 53: 00/01 y 47/48). */}
                  <AjusteEditor id={`nc-desc-linea-${i}`} etiqueta={t.descuentoItem} clase="descuento" valor={linea.descuento} onCambio={(a) => actualizar(i, { descuento: a })} />
                  <AjusteEditor id={`nc-cargo-linea-${i}`} etiqueta={t.cargoItem} clase="cargo" valor={linea.cargo} onCambio={(a) => actualizar(i, { cargo: a })} />
                </div>
              ) : null}
            </div>
          ))}
          </div>
        </div>
      </section>

      {/* #557: lo poco frecuente, a pedido. Reemplaza a la forma de pago y al tipo de operación siempre visibles, a las casillas de detracción, percepción y
          retención, al correo del cliente y a las tres secciones plegables. */}
      <section className="grid gap-3 border-t border-border/60 pt-3">
        <div className="flex flex-wrap items-center gap-2" data-testid="extras">
          <span className={AYUDA_CAMPO}>{t.agregar}</span>
          {ofrecidos.map((e) => (
            <Button
              key={e}
              ref={(b: HTMLButtonElement | null) => {
                botonesExtra.current[e] = b;
              }}
              type="button"
              variant={activo[e] ? "suave" : "outline"}
              size="sm"
              aria-pressed={activo[e]}
              title={tituloExtra[e]}
              onClick={() => (activo[e] ? quitar(e) : activar(e))}
            >
              {activo[e] ? <CheckIcon weight="bold" /> : <PlusIcon weight="bold" />}
              {t.extras[e]}
            </Button>
          ))}
          {/* #560: dónde se activan los avanzados que no se ofrecen. Solo a quien puede cambiar el ajuste (un administrador); fuera del proveedor de rol, a todos. En otra
              pestaña: ir ahí en esta cerraría el modal y perdería lo escrito. */}
          {hayOcultos && sinPermisoDeConfigurar === null ? (
            <Link href={enlaceASeccion("emision")} target="_blank" rel="noopener" className={cn(AYUDA_CAMPO, "ml-auto underline-offset-2 hover:text-foreground hover:underline")} data-testid="extras-ocultos">
              {t.masOpcionesEnEmpresa}
            </Link>
          ) : null}
        </div>

        {/* El aviso de detracción ya no pide marcar una casilla: ofrece abrir su panel. */}
        {ventaInterna && avisoDeDetraccion({ activa: detraccion.activa, moneda, total: mostrados.total, boleta }) ? (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p role="status" className={cn(AYUDA_CAMPO, "text-warning-foreground")} data-testid="aviso-detraccion">
              {t.avisoDetraccion}
            </p>
            <Button type="button" variant="outline" size="sm" onClick={() => activar("detraccion")}>
              <PlusIcon weight="bold" />
              {t.activarDetraccion}
            </Button>
          </div>
        ) : null}

        {/* #309: al crédito se arman las cuotas; la boleta se emite siempre al contado. */}
        {visible("credito") ? (
          <PanelExtra id="credito" titulo={tituloExtra.credito} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.credito })} onQuitar={() => quitarDesdeElPanel("credito")}>
            <CreditoEditor credito={credito} onCambio={setCredito} neto={netoBase} moneda={moneda} emision={fecha} error={credito.cuotas.length === 0 ? null : errorCredito} />
          </PanelExtra>
        ) : null}
        {/* #288: detracción, #297: percepción, #313: retención. Solo en una venta interna; se excluyen entre sí porque cada una fija su tipo de operación. */}
        {visible("detraccion") ? (
          <PanelExtra id="detraccion" titulo={tituloExtra.detraccion} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.detraccion })} onQuitar={() => quitarDesdeElPanel("detraccion")}>
            <DetraccionEditor valor={detraccion} onCambio={setDetraccion} moneda={moneda} montoCalculado={montoDetraccion} cuentaDeLaEmpresa={cuentaDetracciones} />
          </PanelExtra>
        ) : null}
        {visible("percepcion") ? (
          <PanelExtra id="percepcion" titulo={tituloExtra.percepcion} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.percepcion })} onQuitar={() => quitarDesdeElPanel("percepcion")}>
            <PercepcionEditor valor={percepcion} onCambio={setPercepcion} moneda={moneda} alCredito={alCredito && !boleta} montoCalculado={montoPercepcion} />
          </PanelExtra>
        ) : null}
        {visible("retencion") ? (
          <PanelExtra id="retencion" titulo={tituloExtra.retencion} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.retencion })} onQuitar={() => quitarDesdeElPanel("retencion")}>
            <RetencionEditor valor={retencion} onCambio={setRetencion} moneda={moneda} montoCalculado={montoRetencion} />
          </PanelExtra>
        ) : null}
        {/* #311: tipo de operación (catálogo 51): venta interna, exportación y las demás. Quitarlo vuelve a la venta interna (0101). */}
        {visible("operacion") ? (
          <PanelExtra id="operacion" titulo={tituloExtra.operacion} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.operacion })} onQuitar={() => quitarDesdeElPanel("operacion")}>
            <div className="grid items-start gap-4 sm:grid-cols-2">
              <Campo id="nc-operacion" etiqueta={t.tipoOperacion} className="[&>label]:sr-only">
                <SelectCampo
                  id="nc-operacion"
                  valor={operacion}
                  // Volver a 0101 desde el panel no lo cierra bajo el puntero (abierto por una copia de exportación, su bandera seguía apagada).
                  onCambio={(v) => {
                    setOperacionAbierta(true);
                    cambiarOperacion(v);
                  }}
                  opciones={GRUPOS_OPERACION.flatMap((g) =>
                    operaciones(messages)
                      .filter((o) => o.grupo === g && (boleta || !o.soloBoleta))
                      .map((o) => ({ value: o.codigo, label: `${o.codigo} · ${o.etiqueta}`, grupo: messages.documentos.grupoOperacion[g] })),
                  )}
                />
              </Campo>
            </div>
            {exportacion ? (
              <div className="grid items-start gap-4 sm:grid-cols-2" data-testid="datos-exportacion">
                {/* #557: el país del cliente del exterior vive aquí, con lo demás de la exportación: los datos del cliente van plegados en su tarjeta. */}
                <Campo id="nc-pais" etiqueta={t.paisCliente} ayuda={boleta ? t.paisOpcionalBoleta : t.paisObligatorio}>
                  <Combobox
                    id="nc-pais"
                    items={paises.map((p) => ({ value: p.codigo, label: p.nombre }))}
                    valor={pais}
                    onCambio={(v) => setPais(v ?? "")}
                    placeholder={t.seleccione}
                    vacio={messages.comun.sinResultados}
                    etiquetaAbrir={messages.comun.abrirOpciones}
                  />
                </Campo>
                <Campo id="nc-incoterm" etiqueta={t.incoterm} opcional ayuda={t.ayudaIncoterm}>
                  <SelectCampo id="nc-incoterm" valor={incoterm} onCambio={setIncoterm} opciones={[{ value: "", label: t.sinIncoterm }, ...INCOTERMS.map((i) => ({ value: i, label: i }))]} />
                </Campo>
                {EXIGE_PAIS_DE_USO.has(operacion) ? (
                  <Campo id="nc-pais-uso" etiqueta={t.paisUso} ayuda={t.ayudaPaisUso}>
                    <Combobox
                      id="nc-pais-uso"
                      items={paises.filter((p) => p.codigo !== "PE").map((p) => ({ value: p.codigo, label: p.nombre }))}
                      valor={paisDeUso}
                      onCambio={(v) => setPaisDeUso(v ?? "")}
                      placeholder={t.seleccione}
                      vacio={messages.comun.sinResultados}
                      etiquetaAbrir={messages.comun.abrirOpciones}
                    />
                  </Campo>
                ) : null}
              </div>
            ) : null}
            {errorExportacion ? (
              <p role="alert" className={cn(AYUDA_CAMPO, "text-destructive")} data-testid="error-exportacion">
                {errorExportacion}
              </p>
            ) : null}
          </PanelExtra>
        ) : null}
        {/* #358, #310: vencimiento, redondeo, descuento y cargo globales (02/03 y 46 recargo al consumo, 49, 50). */}
        {visible("masOpciones") ? (
          <PanelExtra id="masOpciones" titulo={tituloExtra.masOpciones} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.masOpciones })} onQuitar={() => quitarDesdeElPanel("masOpciones")}>
            <div className="grid gap-4 sm:grid-cols-3">
              <Campo id="nc-vencimiento" etiqueta={t.vencimiento} ayuda={t.ayudaVencimiento} opcional>
                <EntradaFecha id="nc-vencimiento" valor={fechaVencimiento} onCambio={setFechaVencimiento} min={fecha} variante="filtro" />
              </Campo>
              <Campo id="nc-redondeo" etiqueta={t.redondeo} ayuda={redondeo.error ?? t.ayudaRedondeo}>
                <input
                  id="nc-redondeo"
                  value={redondeoTexto}
                  onChange={(e) => setRedondeoTexto(e.target.value)}
                  inputMode="decimal"
                  placeholder="0.00"
                  aria-invalid={redondeo.error !== null}
                  className={cn(CAMPO, "font-mono tabular-nums")}
                />
              </Campo>
              <AjusteEditor id="nc-desc-global" etiqueta={t.descuentoGlobal} clase="descuento" valor={descuentoGlobal} onCambio={setDescuentoGlobal} />
              <AjusteEditor id="nc-cargo-global" etiqueta={t.cargoGlobal} clase="cargo" valor={cargoGlobal} onCambio={setCargoGlobal} ofreceRecargoConsumo />
            </div>
          </PanelExtra>
        ) : null}
        {/* #312: anticipos. Una factura de anticipo es común; la final descuenta los del mismo cliente. */}
        {visible("anticipos") ? (
          <PanelExtra id="anticipos" titulo={tituloExtra.anticipos} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.anticipos })} onQuitar={() => quitarDesdeElPanel("anticipos")}>
            <Casilla
              id="nc-es-anticipo"
              etiqueta={interpolar(t.esAnticipo, { nombre })}
              checked={esAnticipo}
              onCheckedChange={(v) => {
                setEsAnticipo(v);
                if (v) {
                  setAnticiposElegidos([]);
                  setLineas((prev) => prev.map((l, n) => (n === 0 && l.descripcion.trim() === "" ? { ...l, descripcion: t.anticipoPrefijo } : l)));
                }
              }}
            />
            {esAnticipo ? (
              <p className={AYUDA_CAMPO} data-testid="anticipo-ayuda">
                {interpolar(t.anticipoAyuda, { nombre })}
              </p>
            ) : (
              <div className="grid gap-2">
                <div className={ETIQUETA_CAMPO}>{t.descontarAnticipos}</div>
                <AnticiposEditor disponibles={disponibles} elegidos={anticiposVigentes} onCambio={setAnticiposElegidos} moneda={moneda} hayCliente={documentoDelCliente !== ""} />
              </div>
            )}
          </PanelExtra>
        ) : null}
        {/* #300: orden de compra, guías, documentos relacionados y observaciones. */}
        {visible("referencias") ? (
          <PanelExtra id="referencias" titulo={tituloExtra.referencias} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.referencias })} onQuitar={() => quitarDesdeElPanel("referencias")}>
            <ReferenciasEditor valor={referencias} onCambio={setReferencias} boleta={boleta} />
          </PanelExtra>
        ) : null}
        {/* #325: el correo del cliente viaja con el comprobante; con el envío automático, le llegan el PDF y el XML cuando SUNAT lo acepta. */}
        {visible("correo") ? (
          <PanelExtra id="correo" titulo={tituloExtra.correo} etiquetaQuitar={interpolar(t.quitarExtra, { extra: t.extras.correo })} onQuitar={() => quitarDesdeElPanel("correo")}>
            <Campo id="nc-email" etiqueta={t.correo} opcional ayuda={t.ayudaCorreo} className="[&>label]:sr-only">
              <input
                id="nc-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder={t.placeholderCorreo}
                // Lo que admite el backend (`CorreoDelCliente`): hasta 150 caracteres.
                maxLength={150}
                autoComplete="off"
                className={CAMPO}
              />
            </Campo>
          </PanelExtra>
        ) : null}
      </section>

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
                    {t.verPlan}
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
              { etiqueta: t.totales.gravado, monto: mostrados.gravado, siempre: true },
              { etiqueta: t.totales.exonerado, monto: mostrados.exonerado, siempre: false },
              { etiqueta: t.totales.inafecto, monto: mostrados.inafecto, siempre: false },
              { etiqueta: t.totales.gratuito, monto: mostrados.gratuito, siempre: false },
              // Mientras la empresa carga no se sabe si es 18 % o la reducida del padrón: se muestra sin tasa en
              // vez de afirmar una que puede cambiarle los importes bajo los ojos un instante después.
              { etiqueta: tasaIgv === null ? t.totales.igv : interpolar(t.totales.igvTasa, { tasa: tasaIgv }), monto: mostrados.igv, siempre: true, igv: true },
              // Solo los conoce el backend (#405): aparecen cuando respondió.
              { etiqueta: t.redondeo, monto: delServidor?.redondeo ?? 0, siempre: false },
            ])
              // Sin importe local (IVAP o exportación mientras el backend no responde) no hay filas que mostrar: todas dirían 0.00.
              // El IGV no se muestra en 0.00 cuando no hay operaciones gravadas (una factura toda exonerada no tiene «IGV (18 %)»).
              .filter((f) => f.monto !== 0 || (f.siempre && ("igv" in f ? lineasCompletas.length === 0 || mostrados.gravado !== 0 || totales.noContempladas.length > 0 : true)))
              .map((f) => (
                <Fragment key={f.etiqueta}>
                  <dt className="after:content-[':']">{f.etiqueta}</dt>
                  <dd className="tabular-nums">{formatearMonto(moneda, f.monto, locale)}</dd>
                </Fragment>
              ))}
          </dl>
          {/* `role="status"` (región viva): sin él, un usuario con lector de pantalla cargaba tres ítems, dejaba
              uno sin precio y emitía dos líneas creyendo que iban tres. El botón de emitir lo referencia con
              `aria-describedby` para que se lea justo antes de confirmar. */}
          {lineasIncompletas > 0 ? (
            <span id="nc-aviso-incompletos" role="status" className={cn(AYUDA_CAMPO, "text-warning-foreground")}>
              {lineasIncompletas === 1 ? t.incompletoUno : interpolar(t.incompletosVarios, { n: lineasIncompletas })}
            </span>
          ) : null}
          {conDetraccion && montoDetraccion !== null ? (
            <span className={AYUDA_CAMPO} data-testid="detraccion-pie">
              {interpolar(t.detraccionPie, { monto: formatearMonto("PEN", montoDetraccion, locale), neto: formatearMonto(moneda, mostrados.total - (moneda === "PEN" ? montoDetraccion : 0), locale) })}
            </span>
          ) : null}
          {moneda !== "PEN" && calculo.estado === "listo" && calculo.totalEnSoles !== null ? (
            <span className={AYUDA_CAMPO} data-testid="total-en-soles">
              {interpolar(t.totalEnSoles, { monto: formatearMonto("PEN", calculo.totalEnSoles, locale), tipoCambio: tipoCambio.trim() })}
            </span>
          ) : null}
          {conRetencion && montoRetencion !== null ? (
            <span className={AYUDA_CAMPO} data-testid="retencion-pie">
              {interpolar(t.retencionPie, { monto: formatearMonto(moneda, montoRetencion, locale), neto: formatearMonto(moneda, mostrados.total - montoRetencion, locale) })}
            </span>
          ) : null}
          {conPercepcion && montoPercepcion !== null ? (
            <span className={AYUDA_CAMPO} data-testid="percepcion-pie">
              {interpolar(t.percepcionPie, { monto: formatearMonto("PEN", montoPercepcion, locale), total: formatearMonto("PEN", mostrados.total + montoPercepcion, locale) })}
            </span>
          ) : null}
          <div className="flex items-baseline gap-2">
            <span className={cn(ETIQUETA_CAMPO, "text-muted-foreground")}>{t.totalAPagar}</span>
            <span className="font-mono text-titulo font-semibold tabular-nums" data-testid="total-a-pagar">
              {sinImporteLocal ? "—" : formatearMonto(moneda, mostrados.total, locale)}
            </span>
            {sinImporteLocal ? (
              <span className={AYUDA_CAMPO} data-testid="importe-por-calcular">
                {t.loCalculaKhipu}
              </span>
            ) : null}
          </div>
        </div>
        <div className="flex items-center gap-2">
        {onCancelar ? (
          <button type="button" onClick={onCancelar} className={BOTON_SECUNDARIO}>
            {messages.comun.cancelar}
          </button>
        ) : null}
          <BotonAsync
            type="submit"
            pendiente={enviando}
            className={BOTON_PRIMARIO}
            textoPendiente={t.emitiendo}
            aria-describedby={lineasIncompletas > 0 ? "nc-aviso-incompletos" : undefined}
          >
            {/* #557: dice qué emite y cuánto, con el total del pie. */}
            {totalConocido ? interpolar(t.emitirConTotal, { nombre, total: formatearMonto(moneda, mostrados.total, locale) }) : interpolar(t.emitir, { nombre })}
          </BotonAsync>
        </div>
      </div>
    </form>
  );
}
