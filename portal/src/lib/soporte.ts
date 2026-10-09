/**
 * A dónde escribir para pedir ayuda (#250). Los tickets viven en un servicio externo (#200): khipu solo enlaza a él. Las dos
 * variables son opcionales; sin ellas el portal no muestra ningún «¿Necesitas ayuda?».
 */
export type Soporte = { url: string | null; email: string | null };

export const SIN_SOPORTE: Soporte = { url: null, email: null };

// Sin `?`, `&` ni espacios: el valor va dentro de un `mailto:` y no debe poder agregarle destinatarios ni cabeceras.
const CORREO = /^[^\s@?&]+@[^\s@?&]+\.[^\s@?&]+$/;

function valor(v: string | undefined): string | null {
  const limpio = v?.trim();
  return limpio ? limpio : null;
}

/** Lee y valida `SUPPORT_URL` y `SUPPORT_EMAIL`. Un valor mal formado lanza un error que nombra la variable: mejor que mostrar un enlace roto. */
export function leerSoporte(env: Record<string, string | undefined> = process.env): Soporte {
  const url = valor(env.SUPPORT_URL);
  const email = valor(env.SUPPORT_EMAIL);
  if (url !== null) {
    let protocolo: string | null = null;
    try {
      protocolo = new URL(url).protocol;
    } catch {
      // queda null y cae en el error de abajo
    }
    if (protocolo !== "https:") throw new Error(`SUPPORT_URL tiene que ser un enlace https (p. ej. https://ayuda.tu-dominio.pe); llegó «${url}»`);
  }
  if (email !== null && !CORREO.test(email)) throw new Error(`SUPPORT_EMAIL no es un correo válido (p. ej. soporte@tu-dominio.pe); llegó «${email}»`);
  return { url, email };
}

/** El `mailto:` de soporte, con el asunto ya puesto si se da uno. */
export function correoDeSoporte(email: string, asunto?: string): string {
  return asunto ? `mailto:${email}?subject=${encodeURIComponent(asunto)}` : `mailto:${email}`;
}
