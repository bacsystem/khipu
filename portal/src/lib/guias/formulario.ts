import type { Mensajes } from "@/i18n/mensajes";
import type { CuerpoDeGuia } from "@/lib/api/guias";

/**
 * El formulario de «Nueva guía» (#39): el estado de los campos como texto, tal como se escribe, y el paso a lo que espera `POST /v1/guias-remision`. Va aparte del
 * componente para poder probarlo sin pantalla. El backend sigue siendo quien valida las reglas de SUNAT; acá solo se evita el viaje por lo que falta o no tiene sentido.
 *
 * La letra de la serie decide la guía, igual que en la API: `T###` remitente (09) y `V###` transportista (31). Cada una manda solo sus datos.
 */

export const MAX_VEHICULOS = 3;
export const MAX_CONDUCTORES = 3;
export const MAX_DOCUMENTOS = 50;
export const MAX_OBSERVACIONES = 250;

export type ModoDePunto = "establecimiento" | "direccion";
export type PuntoForm = { modo: ModoDePunto; establecimiento: string; ubigeo: string; direccion: string };
export type ParteForm = { tipo_doc: string; num_doc: string; nombre: string };
export type ConductorForm = { tipo_doc: string; num_doc: string; nombres: string; apellidos: string; licencia: string };
export type ItemForm = { codigo: string; descripcion: string; unidad: string; cantidad: string; codigo_sunat: string };
export type DocumentoForm = { tipo: string; numero: string; ruc_emisor: string };

export type GuiaForm = {
  serie: string;
  fecha_emision: string;
  destinatario: ParteForm;
  motivo: string;
  descripcion_motivo: string;
  modalidad: "01" | "02";
  transportista: { ruc: string; razon_social: string; registro_mtc: string };
  remitente: ParteForm;
  registro_mtc: string;
  fecha_inicio: string;
  peso_bruto: string;
  unidad_peso: "KGM" | "TNE";
  partida: PuntoForm;
  llegada: PuntoForm;
  vehiculos: string[];
  conductores: ConductorForm[];
  indicadores: string[];
  items: ItemForm[];
  documentos: DocumentoForm[];
  observaciones: string;
  enviar_automatico: boolean;
};

export const INDICADORES = [
  "vehiculo_m1_o_l",
  "retorno_vehiculo_envase_vacio",
  "retorno_vehiculo_vacio",
  "transbordo_programado",
  "vehiculos_conductores_transportista",
] as const;

/** Los indicadores de cada tipo (`IndicadorDeTraslado.admitidos`): la transportista no elige modalidad, así que no tiene los dos que dependen de ella. */
export const INDICADORES_DE_TRANSPORTISTA = ["retorno_vehiculo_envase_vacio", "retorno_vehiculo_vacio", "transbordo_programado"] as const;

export const esDeTransportista = (serie: string) => serie.toUpperCase().startsWith("V");

export const parteVacia = (): ParteForm => ({ tipo_doc: "6", num_doc: "", nombre: "" });
export const conductorVacio = (): ConductorForm => ({ tipo_doc: "1", num_doc: "", nombres: "", apellidos: "", licencia: "" });
export const itemVacio = (): ItemForm => ({ codigo: "", descripcion: "", unidad: "NIU", cantidad: "1", codigo_sunat: "" });
export const documentoVacio = (): DocumentoForm => ({ tipo: "01", numero: "", ruc_emisor: "" });
export const puntoVacio = (modo: ModoDePunto = "establecimiento"): PuntoForm => ({ modo, establecimiento: "0000", ubigeo: "", direccion: "" });

export function guiaVacia(serie: string, hoy: string): GuiaForm {
  return {
    serie,
    fecha_emision: hoy,
    destinatario: parteVacia(),
    motivo: "",
    descripcion_motivo: "",
    modalidad: "02",
    transportista: { ruc: "", razon_social: "", registro_mtc: "" },
    remitente: parteVacia(),
    registro_mtc: "",
    fecha_inicio: hoy,
    peso_bruto: "",
    unidad_peso: "KGM",
    partida: puntoVacio(),
    llegada: puntoVacio("direccion"),
    vehiculos: [""],
    conductores: [conductorVacio()],
    indicadores: [],
    items: [itemVacio()],
    documentos: [],
    observaciones: "",
    // Apagado de entrada: el envío viene apagado en el servidor y la guía firmada se envía después desde su ficha.
    enviar_automatico: false,
  };
}

/** Un número escrito a mano: acepta la coma decimal. `NaN` si no es un número. */
export function aNumero(texto: string): number {
  const limpio = texto.trim().replace(",", ".");
  return limpio === "" ? NaN : Number(limpio);
}

/**
 * Los documentos que ya estaban escritos, con el tipo cambiado al primero aplicable cuando el que tenían no se ofrece en la guía nueva (por ejemplo «constancia de depósito
 * IVAP», solo remitente, al pasar a una serie V). Con `aplicables` vacío (el catálogo no se pudo leer) no se toca nada.
 */
export function conTiposAplicables(documentos: DocumentoForm[], aplicables: string[]): DocumentoForm[] {
  if (aplicables.length === 0) return documentos;
  return documentos.map((d) => (aplicables.includes(d.tipo) ? d : { ...d, tipo: aplicables[0] }));
}

/** ¿Los vehículos y conductores del formulario van en el pedido? La transportista siempre; la remitente en transporte privado sin el indicador M1/L, o en público con el de vehículos del transportista. */
export function llevaVehiculos(f: GuiaForm): boolean {
  if (esDeTransportista(f.serie)) return true;
  if (f.modalidad === "02") return !f.indicadores.includes("vehiculo_m1_o_l");
  return f.indicadores.includes("vehiculos_conductores_transportista");
}

const completa = (p: ParteForm) => p.tipo_doc.trim() !== "" && p.num_doc.trim() !== "" && p.nombre.trim() !== "";
const conductorCompleto = (c: ConductorForm) => [c.tipo_doc, c.num_doc, c.nombres, c.apellidos, c.licencia].every((v) => v.trim() !== "");
const conductorEnBlanco = (c: ConductorForm) => [c.num_doc, c.nombres, c.apellidos, c.licencia].every((v) => v.trim() === "");

/** La transportista no admite un establecimiento (la pantalla solo le ofrece direcciones): aunque el estado conserve ese modo de una guía remitente, se valida como dirección. */
function puntoCompleto(p: PuntoForm, transportista: boolean): boolean {
  return p.modo === "establecimiento" && !transportista ? p.establecimiento.trim() !== "" : /^\d{6}$/.test(p.ubigeo) && p.direccion.trim().length >= 3;
}

export type ClaveDeError = keyof Mensajes["guias"]["form"]["errores"];

/** La primera cosa que falta o no tiene sentido, o `null` si el formulario se puede mandar. */
export function validarGuia(f: GuiaForm): ClaveDeError | null {
  const transportista = esDeTransportista(f.serie);
  if (!/^[TV][A-Z0-9]{3}$/.test(f.serie)) return "sinSerie";
  if (!completa(f.destinatario)) return "sinDestinatario";
  if (transportista) {
    if (!completa(f.remitente)) return "sinRemitente";
    if (f.registro_mtc.trim() === "") return "sinMtc";
  } else {
    if (f.motivo.trim() === "") return "sinMotivo";
    if (f.modalidad === "01" && (f.transportista.ruc.trim() === "" || f.transportista.razon_social.trim() === "")) return "sinTransportista";
  }
  if (f.fecha_inicio.trim() === "") return "sinFechaInicio";
  if (!(aNumero(f.peso_bruto) > 0)) return "pesoInvalido";
  if (!puntoCompleto(f.partida, transportista)) return "sinPuntoPartida";
  if (!puntoCompleto(f.llegada, transportista)) return "sinPuntoLlegada";
  if (llevaVehiculos(f)) {
    if (f.vehiculos[0]?.trim() === "" || f.vehiculos.length === 0) return "sinVehiculo";
    if (f.conductores.length === 0 || !conductorCompleto(f.conductores[0])) return "sinConductor";
  }
  const items = f.items.filter((i) => i.descripcion.trim() !== "" || i.codigo.trim() !== "" || i.codigo_sunat.trim() !== "");
  if (items.length === 0 || items.some((i) => i.descripcion.trim() === "" || i.unidad.trim() === "")) return "sinBienes";
  if (items.some((i) => !(aNumero(i.cantidad) > 0))) return "cantidadInvalida";
  return null;
}

const sinVacios = <T extends Record<string, string>>(o: T): Partial<T> => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v.trim()]).filter(([, v]) => v !== "")) as Partial<T>;

function puntoAApi(p: PuntoForm, transportista: boolean): CuerpoDeGuia["traslado"]["partida"] {
  // El código de establecimiento solo vale en una guía remitente: la transportista siempre lleva ubigeo y dirección.
  if (p.modo === "establecimiento" && !transportista) return { establecimiento: p.establecimiento.trim() };
  return { ubigeo: p.ubigeo.trim(), direccion: p.direccion.trim() };
}

/** Lo que se manda a la API. Solo se llama con un formulario que `validarGuia` aceptó. */
export function cuerpoDeGuia(f: GuiaForm): CuerpoDeGuia {
  const transportista = esDeTransportista(f.serie);
  const indicadores = f.indicadores.filter((i) => (transportista ? (INDICADORES_DE_TRANSPORTISTA as readonly string[]).includes(i) : true));
  const conVehiculos = llevaVehiculos(f);

  const cuerpo: CuerpoDeGuia = {
    serie: f.serie.toUpperCase(),
    fecha_emision: f.fecha_emision,
    destinatario: { tipo_doc: f.destinatario.tipo_doc.trim(), num_doc: f.destinatario.num_doc.trim(), nombre: f.destinatario.nombre.trim() },
    traslado: {
      fecha_inicio: f.fecha_inicio,
      peso_bruto: aNumero(f.peso_bruto),
      unidad_peso: f.unidad_peso,
      partida: puntoAApi(f.partida, transportista),
      llegada: puntoAApi(f.llegada, transportista),
      ...(conVehiculos
        ? {
            vehiculos: f.vehiculos.map((v) => v.trim().toUpperCase()).filter((v) => v !== "").map((placa) => ({ placa })),
            conductores: f.conductores.filter((c) => !conductorEnBlanco(c)).map((c) => ({ tipo_doc: c.tipo_doc.trim(), num_doc: c.num_doc.trim(), nombres: c.nombres.trim(), apellidos: c.apellidos.trim(), licencia: c.licencia.trim() })),
          }
        : {}),
      ...(indicadores.length > 0 ? { indicadores } : {}),
    },
    items: f.items
      .filter((i) => i.descripcion.trim() !== "" || i.codigo.trim() !== "" || i.codigo_sunat.trim() !== "")
      .map((i) => ({ ...sinVacios({ codigo: i.codigo, codigo_sunat: i.codigo_sunat }), descripcion: i.descripcion.trim(), unidad: i.unidad.trim().toUpperCase(), cantidad: aNumero(i.cantidad) })),
    enviar_automatico: f.enviar_automatico,
  };

  if (transportista) {
    cuerpo.remitente = { tipo_doc: f.remitente.tipo_doc.trim(), num_doc: f.remitente.num_doc.trim(), nombre: f.remitente.nombre.trim() };
    cuerpo.registro_mtc = f.registro_mtc.trim();
  } else {
    cuerpo.motivo = f.motivo.trim();
    cuerpo.modalidad = f.modalidad;
    if (f.descripcion_motivo.trim() !== "") cuerpo.descripcion_motivo = f.descripcion_motivo.trim();
    if (f.modalidad === "01") {
      cuerpo.transportista = {
        ruc: f.transportista.ruc.trim(),
        razon_social: f.transportista.razon_social.trim(),
        ...(f.transportista.registro_mtc.trim() !== "" ? { registro_mtc: f.transportista.registro_mtc.trim() } : {}),
      };
    }
  }

  const documentos = f.documentos.filter((d) => d.numero.trim() !== "");
  if (documentos.length > 0) {
    cuerpo.documentos_relacionados = documentos.map((d) => ({ tipo: d.tipo, numero: d.numero.trim(), ...sinVacios({ ruc_emisor: d.ruc_emisor }) }));
  }
  if (f.observaciones.trim() !== "") cuerpo.observaciones = f.observaciones.trim();
  return cuerpo;
}
