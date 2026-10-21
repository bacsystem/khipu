import type { Mensajes } from "@/i18n/mensajes";
import { rucValido } from "@/lib/validacion";
import es from "@/messages/es.json";

/**
 * Los documentos de identidad del comprador (catálogo 06) con su formato, tal como los valida el dominio. Los usa el formulario de «Nuevo comprobante» y el de clientes guardados
 * (#319): lo que se guarda como cliente tiene que poder usarse al emitir.
 */

/**
 * Documentos del comprador de una boleta (catálogo 06), con su formato: lo que exige `Receptor.exigirValidoParaBoleta` (4207, 2017, 4208). «-» es el comprador
 * sin identificar, que SUNAT admite solo hasta S/ 700 en soles.
 */
const FORMATOS = [
  { codigo: "1", patron: "[0-9]{8}", max: 8, ejemplo: "12345678", formato: "dni" },
  { codigo: "4", patron: "\\S{1,15}", max: 15, ejemplo: "001234567", formato: "hasta15" },
  { codigo: "7", patron: "\\S{1,15}", max: 15, ejemplo: "AB1234567", formato: "hasta15" },
  { codigo: "6", patron: "[0-9]{11}", max: 11, ejemplo: "20123456786", formato: "ruc" },
  // #299: el resto del catálogo 06 que acepta el dominio (`Receptor.DOCUMENTOS_BOLETA`), todos con la regla 4208: hasta 15 caracteres sin espacios.
  { codigo: "0", patron: "\\S{1,15}", max: 15, ejemplo: "US123456789", formato: "hasta15" },
  { codigo: "A", patron: "\\S{1,15}", max: 15, ejemplo: "CD123456", formato: "hasta15" },
  { codigo: "B", patron: "\\S{1,15}", max: 15, ejemplo: "AB1234567", formato: "hasta15" },
  { codigo: "C", patron: "\\S{1,15}", max: 15, ejemplo: "123456789", formato: "hasta15" },
  { codigo: "D", patron: "\\S{1,15}", max: 15, ejemplo: "123456789", formato: "hasta15" },
  { codigo: "E", patron: "\\S{1,15}", max: 15, ejemplo: "TAM123456", formato: "hasta15" },
  { codigo: "F", patron: "\\S{1,15}", max: 15, ejemplo: "PTP123456", formato: "hasta15" },
  { codigo: "G", patron: "\\S{1,15}", max: 15, ejemplo: "SV123456", formato: "hasta15" },
  { codigo: "H", patron: "\\S{1,15}", max: 15, ejemplo: "CPP123456", formato: "hasta15" },
  { codigo: "-", patron: "", max: 0, ejemplo: "", formato: null },
] as const;

/** Los documentos con su nombre (`etiqueta`) y la regla de formato dicha (`titulo`) en el idioma de `messages` (#25). */
export function documentosBoleta(messages: Mensajes = es) {
  const t = messages.documentos;
  return FORMATOS.map(({ formato, ...d }) => ({ ...d, etiqueta: t.identidadBoleta[d.codigo], titulo: formato ? t.formato[formato] : "" }));
}

export const DOCUMENTOS_BOLETA = documentosBoleta();

/** Los que se pueden guardar como cliente: todos menos «sin documento», que no identifica a nadie. */
export const documentosDeCliente = (messages: Mensajes = es) => documentosBoleta(messages).filter((d) => d.codigo !== "-");
export const DOCUMENTOS_DE_CLIENTE = documentosDeCliente();

/** El mensaje si el número no cumple el formato de su tipo (las reglas 4207, 2017 y 4208 del dominio); `null` si está bien. */
export function errorDeDocumento(tipoDoc: string, numDoc: string, messages: Mensajes = es): string | null {
  const doc = documentosDeCliente(messages).find((d) => d.codigo === tipoDoc);
  if (!doc) return messages.documentos.formato.elegirTipo;
  const num = numDoc.trim();
  if (!new RegExp(`^${doc.patron}$`).test(num)) return doc.titulo;
  if (tipoDoc === "6" && !rucValido(num)) return messages.validacion.rucInvalido;
  return null;
}
