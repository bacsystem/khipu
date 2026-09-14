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
