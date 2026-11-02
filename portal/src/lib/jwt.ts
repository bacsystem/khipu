/**
 * Si el token es de una sesión de soporte (#184): trae el claim `imp` con el administrador que la abrió. Sin verificar la firma, como `accesoExpirado`: solo sirve
 * para decidir que el middleware deje pasar una sesión que no tiene refresh. Si el claim es falso, el backend rechaza el token y nada se abre.
 */
export function esSesionDeSoporte(token: string): boolean {
  try {
    const payload = token.split(".")[1] ?? "";
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const relleno = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    const { imp } = JSON.parse(atob(relleno)) as { imp?: unknown };
    return typeof imp === "string" && imp.length > 0;
  } catch {
    return false;
  }
}

/** Decodifica el payload de un JWT sin verificar la firma: solo para decidir si conviene refrescar antes de usarlo. */
export function accesoExpirado(token: string, margenMs = 5_000): boolean {
  try {
    const payload = token.split(".")[1] ?? "";
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const relleno = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    const { exp } = JSON.parse(atob(relleno)) as { exp?: number };
    return typeof exp !== "number" || exp * 1000 <= Date.now() + margenMs;
  } catch {
    return true;
  }
}
