import { z } from "zod";

export const emailSchema = z.string().min(1, "Ingresa tu correo").email("Correo inválido");

export const passwordSchema = z
  .string()
  .min(8, "Mínimo 8 caracteres")
  .regex(/[A-Za-z]/, "Debe incluir una letra")
  .regex(/\d/, "Debe incluir un número");
