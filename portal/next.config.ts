import type { NextConfig } from "next";

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
  ...(devOrigins.length > 0 ? { allowedDevOrigins: devOrigins } : {}),
};

export default nextConfig;
