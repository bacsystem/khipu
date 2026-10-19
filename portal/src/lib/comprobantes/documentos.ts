import { rucValido } from "@/lib/validacion";

/**
 * Los documentos de identidad del comprador (catálogo 06) con su formato, tal como los valida el dominio. Los usa el formulario de «Nuevo comprobante» y el de clientes guardados
 * (#319): lo que se guarda como cliente tiene que poder usarse al emitir.
 */

/**
 * Documentos del comprador de una boleta (catálogo 06), con su formato: lo que exige `Receptor.exigirValidoParaBoleta` (4207, 2017, 4208). «-» es el comprador
 * sin identificar, que SUNAT admite solo hasta S/ 700 en soles.
 */
export const DOCUMENTOS_BOLETA = [
  { codigo: "1", etiqueta: "DNI", patron: "[0-9]{8}", max: 8, ejemplo: "12345678", titulo: "DNI de 8 dígitos" },
  { codigo: "4", etiqueta: "Carné de extranjería", patron: "\\S{1,15}", max: 15, ejemplo: "001234567", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "7", etiqueta: "Pasaporte", patron: "\\S{1,15}", max: 15, ejemplo: "AB1234567", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "6", etiqueta: "RUC", patron: "[0-9]{11}", max: 11, ejemplo: "20123456786", titulo: "RUC de 11 dígitos" },
  // #299: el resto del catálogo 06 que acepta el dominio (`Receptor.DOCUMENTOS_BOLETA`), todos con la regla 4208: hasta 15 caracteres sin espacios.
  { codigo: "0", etiqueta: "Doc. tributario no domiciliado sin RUC", patron: "\\S{1,15}", max: 15, ejemplo: "US123456789", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "A", etiqueta: "Cédula diplomática de identidad", patron: "\\S{1,15}", max: 15, ejemplo: "CD123456", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "B", etiqueta: "Doc. de identidad del país de residencia (no domiciliado)", patron: "\\S{1,15}", max: 15, ejemplo: "AB1234567", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "C", etiqueta: "TIN — doc. tributario de persona natural no domiciliada", patron: "\\S{1,15}", max: 15, ejemplo: "123456789", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "D", etiqueta: "IN — doc. tributario de persona jurídica no domiciliada", patron: "\\S{1,15}", max: 15, ejemplo: "123456789", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "E", etiqueta: "Tarjeta Andina de Migración (TAM)", patron: "\\S{1,15}", max: 15, ejemplo: "TAM123456", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "F", etiqueta: "Permiso Temporal de Permanencia (PTP)", patron: "\\S{1,15}", max: 15, ejemplo: "PTP123456", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "G", etiqueta: "Salvoconducto", patron: "\\S{1,15}", max: 15, ejemplo: "SV123456", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "H", etiqueta: "Carné de permiso temporal de permanencia (CPP)", patron: "\\S{1,15}", max: 15, ejemplo: "CPP123456", titulo: "Hasta 15 caracteres sin espacios" },
  { codigo: "-", etiqueta: "Sin documento (hasta S/ 700)", patron: "", max: 0, ejemplo: "", titulo: "" },
] as const;

/** Los que se pueden guardar como cliente: todos menos «sin documento», que no identifica a nadie. */
export const DOCUMENTOS_DE_CLIENTE = DOCUMENTOS_BOLETA.filter((d) => d.codigo !== "-");

/** El mensaje si el número no cumple el formato de su tipo (las reglas 4207, 2017 y 4208 del dominio); `null` si está bien. */
export function errorDeDocumento(tipoDoc: string, numDoc: string): string | null {
  const doc = DOCUMENTOS_DE_CLIENTE.find((d) => d.codigo === tipoDoc);
  if (!doc) return "Elige el tipo de documento";
  const num = numDoc.trim();
  if (!new RegExp(`^${doc.patron}$`).test(num)) return doc.titulo;
  if (tipoDoc === "6" && !rucValido(num)) return "El RUC no es válido: revisa los dígitos (SUNAT valida el dígito verificador)";
  return null;
}
