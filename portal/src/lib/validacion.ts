import { z } from "zod";

export const emailSchema = z.string().min(1, "Ingresa tu correo").email("Correo inválido");

export const passwordSchema = z
  .string()
  .min(8, "Mínimo 8 caracteres")
  .regex(/[A-Za-z]/, "Debe incluir una letra")
  .regex(/\d/, "Debe incluir un número");

/**
 * Celular de contacto en Perú: 9 dígitos que empiezan con 9. Admite escribirlo con +51/51 y espacios o guiones —se
 * quitan antes de validar, igual que en el dominio (Cuenta.normalizarTelefono)— así que lo que llega al backend ya
 * viene limpio.
 */
export const telefonoSchema = z
  .string()
  .min(1, "Ingresa tu celular")
  .transform((v) => v.replace(/[\s-]/g, ""))
  .refine((v) => /^(\+?51)?9\d{8}$/.test(v), "Celular inválido: 9 dígitos, empieza con 9 (ej. 987654321)");
