/**
 * Autoservicio abierto o por invitación. En producción el registro arranca **cerrado**: el landing y la documentación son
 * públicos, pero crear una cuenta arrastra onboarding, certificado PKCS#12 y credenciales SOL, así que hasta que esos flujos
 * estén certificados (ver `qa/`) las cuentas se dan de alta a mano. En desarrollo y en los e2e está abierto para no tener que
 * declarar la variable en cada entorno.
 *
 * `REGISTRO_ABIERTO` manda siempre que esté definida (`"true"` / `"false"`); sin ella, el criterio es el entorno.
 */
export function registroAbierto(env: NodeJS.ProcessEnv = process.env): boolean {
  if (env.REGISTRO_ABIERTO) return env.REGISTRO_ABIERTO === "true";
  return env.NODE_ENV !== "production";
}

/**
 * A dónde manda el landing cuando el registro está cerrado: un formulario externo o un `mailto:`. Sin ella no se ofrece
 * ningún enlace (mejor que un botón que no lleva a ninguna parte).
 */
export function contactoUrl(env: NodeJS.ProcessEnv = process.env): string | null {
  const url = env.CONTACTO_URL?.trim();
  return url ? url : null;
}
