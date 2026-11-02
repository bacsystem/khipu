import type { Metadata } from "next";
import { DM_Sans, Figtree, Inter, JetBrains_Mono, Manrope, Plus_Jakarta_Sans } from "next/font/google";
import { ScriptDiseno } from "khipu-design-system/script-diseno";
import { connection } from "next/server";
import { getLocale, getTranslations } from "next-intl/server";
import { TextosDeCliente } from "@/i18n/textos-de-cliente";
import { Providers } from "@/components/providers";
import { SoporteProvider } from "@/components/soporte/ayuda-de-soporte";
import { soporteParaMostrar } from "@/lib/soporte";
import "./globals.css";

// Inter para la interfaz y JetBrains Mono para datos (correlativos, ids, código), como el design system (v0.5.0). La tipografía es preferencia
// del usuario (FUENTES del DS): Inter es --font-sans; las demás exponen --fuente-<valor> y html[data-fuente] las pone en --font-sans. Solo
// Inter se precarga.
const sans = Inter({ variable: "--font-sans", subsets: ["latin"] });
const jakarta = Plus_Jakarta_Sans({ variable: "--fuente-jakarta", subsets: ["latin"], preload: false });
const manrope = Manrope({ variable: "--fuente-manrope", subsets: ["latin"], preload: false });
const dmSans = DM_Sans({ variable: "--fuente-dmsans", subsets: ["latin"], preload: false });
const figtree = Figtree({ variable: "--fuente-figtree", subsets: ["latin"], preload: false });
const mono = JetBrains_Mono({ variable: "--font-mono", weight: ["400", "500", "600"], subsets: ["latin"] });
const fuentes = [sans, jakarta, manrope, dmSans, figtree, mono].map((f) => f.variable).join(" ");

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("app.metadatos");
  return { title: t("titulo"), description: t("descripcion") };
}

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // #250: el soporte se lee al servir, no al compilar; si no, una imagen construida una vez llevaría el valor del build a todos los entornos.
  await connection();
  const soporte = soporteParaMostrar();
  const locale = await getLocale();
  return (
    <html lang={locale} className={fuentes} suppressHydrationWarning>
      <head>
        {/* Aplica el diseño, la paleta, el tono oscuro y la tipografía guardados antes del primer pintado: sin esto la página parpadea al cargar. */}
        <ScriptDiseno />
      </head>
      <body className="antialiased">
        {/* #25: locale y zona horaria de src/i18n/request.ts, y de los mensajes solo la base que usan los Client Components de las
            páginas públicas; (privado), /admin, /developers y la portada amplían la lista en su layout (src/i18n/textos-de-cliente.tsx). */}
        <TextosDeCliente segmento="base">
          <Providers>
            <SoporteProvider soporte={soporte}>{children}</SoporteProvider>
          </Providers>
        </TextosDeCliente>
      </body>
    </html>
  );
}
