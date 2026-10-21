import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

// #25: textos por locale con next-intl, sin rutas por idioma (el locale sale de una cookie, ver src/i18n/config.ts).
const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

// Hosts (IP o nombre) desde los que se permite entrar al server de dev además de localhost, separados por coma.
// Sin esto Next 15 avisa y Next 16 bloquea los recursos /_next/* cuando se accede por la IP de la red local.
const devOrigins = (process.env.PORTAL_DEV_ORIGINS ?? "")
  .split(",")
  .map((h) => h.trim())
  .filter(Boolean);

const nextConfig: NextConfig = {
  output: "standalone",
  // Permite levantar un segundo dev server (p. ej. con mocks en otro puerto) sin pisar el .next del principal.
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  // `next build` lintea con la API de ESLint, que no lee la línea base eslint-suppressions.json (solo la CLI la aplica): fallaría
  // en los literales ya registrados ahí. El lint con su línea base lo corre la CLI en el script `build` del package.json
  // (`eslint && next build`), así que `npm run build` y el Dockerfile siguen fallando con un literal nuevo.
  eslint: { ignoreDuringBuilds: true },
  ...(devOrigins.length > 0 ? { allowedDevOrigins: devOrigins } : {}),
};

export default withNextIntl(nextConfig);
