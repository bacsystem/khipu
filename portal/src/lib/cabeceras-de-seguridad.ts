/**
 * Cabeceras de seguridad de todas las respuestas del portal (#602). Las aplica `next.config.ts` (`headers()`); viven aquí para poder
 * probarlas sin levantar Next.
 *
 * - Enmarcado: solo el propio portal puede meter una página suya en un iframe (`SAMEORIGIN` / `frame-ancestors 'self'`). La vista
 *   previa del PDF de la personalización es un iframe del mismo origen; un sitio ajeno no puede montar el login ni el backoffice
 *   debajo de un botón engañoso (clickjacking). La CSP lleva **solo** `frame-ancestors`: restringir scripts y estilos rompería el
 *   script de tema en `<head>` y la referencia de `/developers` sin una revisión aparte.
 * - HSTS solo en producción: en desarrollo el portal corre por HTTP (y los navegadores ignoran HSTS por HTTP, pero en `localhost` con
 *   HTTPS dejaría el nombre forzado a HTTPS para otros proyectos). Sin `includeSubDomains` ni `preload`: no se sabe si todos los
 *   subdominios del dominio sirven HTTPS, y `preload` no se deshace en meses.
 */
export function cabecerasDeSeguridad(produccion: boolean): { key: string; value: string }[] {
  const cabeceras = [
    { key: "X-Frame-Options", value: "SAMEORIGIN" },
    { key: "Content-Security-Policy", value: "frame-ancestors 'self'" },
    { key: "X-Content-Type-Options", value: "nosniff" },
    { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
    { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  ];
  if (produccion) cabeceras.push({ key: "Strict-Transport-Security", value: "max-age=31536000" });
  return cabeceras;
}
