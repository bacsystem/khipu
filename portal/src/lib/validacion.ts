import { z } from "zod";

export const emailSchema = z.string().min(1, "Ingresa tu correo").email("Correo inválido");

export const passwordSchema = z
  .string()
  .min(8, "Mínimo 8 caracteres")
  .regex(/[A-Za-z]/, "Debe incluir una letra")
  .regex(/\d/, "Debe incluir un número");

/**
 * Celular de contacto en Perú: 9 dígitos que empiezan con 9. Admite escribirlo con +51/51 y espacios o guiones —solo
 * los espacios/guiones se quitan aquí; el prefijo +51/51, si viene, se envía tal cual— el dominio
 * (Cuenta.normalizarTelefono) es quien lo deja en su forma canónica de 9 dígitos.
 */
export const telefonoSchema = z
  .string()
  .min(1, "Ingresa tu celular")
  .transform((v) => v.replace(/[\s-]/g, ""))
  .refine((v) => /^(\+?51)?9\d{8}$/.test(v), "Celular inválido: 9 dígitos, empieza con 9 (ej. 987654321)");

/** Solo dígitos: RUC, códigos de establecimiento, correlativos. */
export const soloDigitos = (v: string) => v.replace(/\D/g, "");

/**
 * Lo que puede aparecer en un celular tal como lo escribe la gente: dígitos y un `+` inicial (los espacios y guiones los
 * quita `telefonoSchema`). Nada de letras.
 */
export const soloTelefono = (v: string) => {
  const limpio = v.replace(/[^\d+]/g, "");
  return limpio.startsWith("+") ? "+" + limpio.slice(1).replace(/\+/g, "") : limpio.replace(/\+/g, "");
};

/** Serie en mayúsculas y sin espacios: `F001`, `B001`. */
export const codigoSerie = (v: string) => v.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 4);

/** Mensaje de la serie que no corresponde al tipo (SUNAT 1001); el mismo en el onboarding y en el alta asistida del backoffice. */
export const MENSAJE_SERIE = "La serie de una factura empieza con F y la de una boleta con B, más 3 caracteres (p. ej. F001)";

/**
 * 1001: la serie de una factura es `F` + 3 alfanuméricos y la de una boleta, `B` + 3. Pedir «4 caracteres» no basta: el mock lo
 * aceptaba y el alta terminaba en verde con series que el backend rechaza. Compara en mayúsculas y sin espacios alrededor, como
 * la deja `codigoSerie`/el formulario.
 */
export function serieCoincideConTipo(tipo: string, serie: string): boolean {
  const prefijo = tipo === "01" ? "F" : tipo === "03" ? "B" : null;
  return prefijo !== null && new RegExp(`^${prefijo}[A-Z0-9]{3}$`).test(serie.toUpperCase().trim());
}

/** Módulo 11 de SUNAT (mismos pesos que `Ruc` en el dominio): evita un viaje al servidor por un dígito mal tecleado. */
export function rucValido(ruc: string): boolean {
  if (!/^(10|15|16|17|20)\d{9}$/.test(ruc)) return false;
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  const suma = pesos.reduce((acc, p, i) => acc + Number(ruc[i]) * p, 0);
  const resto = 11 - (suma % 11);
  return Number(ruc[10]) === (resto === 10 ? 0 : resto === 11 ? 1 : resto);
}

/** Razón social tal como la exige SUNAT para el emisor (4338): hasta 1500 y sin tabuladores ni saltos de línea. */
export const razonSocialSchema = z
  .string()
  .transform((v) => v.trim())
  .refine((v) => v.length > 0, "Ingresa la razón social")
  .refine((v) => v.length <= 1500, "La razón social admite hasta 1500 caracteres")
  .refine((v) => !/[\u0000-\u001F\u007F]/.test(v), "La razón social no admite tabuladores ni saltos de línea (SUNAT 4338)");

export const rucSchema = z
  .string()
  .transform((v) => v.replace(/\D/g, ""))
  .refine((v) => v.length === 11, "El RUC tiene 11 dígitos")
  .refine(rucValido, "El RUC no es válido: revisa los dígitos (SUNAT valida el dígito verificador)");
