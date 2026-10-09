import type { Metadata } from "next";
import { Bricolage_Grotesque, Inter, JetBrains_Mono } from "next/font/google";
import { connection } from "next/server";
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

export const metadata: Metadata = {
  title: "Portal Facturación Electrónica",
  description: "Autoservicio de facturación electrónica SUNAT",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // #250: el soporte se lee al servir, no al compilar; si no, una imagen construida una vez llevaría el valor del build a todos los entornos.
  await connection();
  const soporte = soporteParaMostrar();
  return (
    <html lang="es" className={`${sans.variable} ${heading.variable} ${mono.variable}`} suppressHydrationWarning>
      <body className="antialiased">
        <Providers>
          <SoporteProvider soporte={soporte}>{children}</SoporteProvider>
        </Providers>
      </body>
    </html>
  );
}
