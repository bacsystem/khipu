import type { Metadata } from "next";
import { Roboto, Roboto_Mono } from "next/font/google";
import { Providers } from "@/components/providers";
import "./globals.css";

const sans = Roboto({
  variable: "--font-sans",
  weight: ["400", "500", "700"],
  subsets: ["latin"],
});

const mono = Roboto_Mono({
  variable: "--font-mono",
  weight: ["400", "500"],
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Portal Facturación Electrónica",
  description: "Autoservicio de facturación electrónica SUNAT",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="es" className={`${sans.variable} ${mono.variable}`}>
      <body className="antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
