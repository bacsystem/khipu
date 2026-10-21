import type { Metadata } from "next";
import { Bricolage_Grotesque, Inter, JetBrains_Mono } from "next/font/google";
import { connection } from "next/server";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getTranslations } from "next-intl/server";
import { Providers } from "@/components/providers";
import { SoporteProvider } from "@/components/soporte/ayuda-de-soporte";
import { soporteParaMostrar } from "@/lib/soporte";
import "./globals.css";

const sans = Inter({
  variable: "--font-sans",
  subsets: ["latin"],
});

// Los títulos llevan su propia voz: Inter en todo dejaba el portal con cara de plantilla. Bricolage tiene carácter sin
// perder seriedad, que es lo que pide un producto de facturación.
const heading = Bricolage_Grotesque({
  variable: "--font-titulo",
  subsets: ["latin"],
  weight: ["500", "600", "700"],
});

const mono = JetBrains_Mono({
  variable: "--font-mono",
  weight: ["400", "500"],
  subsets: ["latin"],
});

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
    <html lang={locale} className={`${sans.variable} ${heading.variable} ${mono.variable}`} suppressHydrationWarning>
      <body className="antialiased">
        {/* #25: sin props hereda locale, zona horaria y mensajes de src/i18n/request.ts; los Client Components los leen de acá. */}
        <NextIntlClientProvider>
          <Providers>
            <SoporteProvider soporte={soporte}>{children}</SoporteProvider>
          </Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
