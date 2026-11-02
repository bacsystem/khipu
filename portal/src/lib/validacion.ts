import { z } from "zod";
import type { Mensajes } from "@/i18n/mensajes";

/**
 * Los esquemas de los campos comunes con sus mensajes en el idioma de `messages` (#25). En un formulario:
 * `const v = useMemo(() => esquemas(messages), [messages])`.
 */
export function esquemas(messages: Mensajes) {
  const t = messages.validacion;
  return {
    email: z.string().min(1, t.correoRequerido).email(t.correoInvalido),
    password: z.string().min(8, t.passwordCorta).regex(/[A-Za-z]/, t.passwordSinLetra).regex(/\d/, t.passwordSinNumero),
    /**
     * Celular de contacto en Perú: 9 dígitos que empiezan con 9. Admite escribirlo con +51/51 y espacios o guiones —solo
     * los espacios/guiones se quitan aquí; el prefijo +51/51, si viene, se envía tal cual— el dominio
     * (Cuenta.normalizarTelefono) es quien lo deja en su forma canónica de 9 dígitos.
     */
    telefono: z
      .string()
      .min(1, t.celularRequerido)
      .transform((v) => v.replace(/[\s-]/g, ""))
      .refine((v) => /^(\+?51)?9\d{8}$/.test(v), t.celularInvalido),
    /** Razón social tal como la exige SUNAT para el emisor (4338): hasta 1500 y sin tabuladores ni saltos de línea. */
    razonSocial: z
      .string()
      .transform((v) => v.trim())
      .refine((v) => v.length > 0, t.razonSocialRequerida)
      .refine((v) => v.length <= 1500, t.razonSocialLarga)
      .refine((v) => !/[\u0000-\u001F\u007F]/.test(v), t.razonSocialConSaltos),
    ruc: z
      .string()
      .transform((v) => v.replace(/\D/g, ""))
      .refine((v) => v.length === 11, t.rucLongitud)
      .refine(rucValido, t.rucInvalido),
  };
}

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
