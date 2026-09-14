import { CheckCircle2, CircleDashed, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { ComprobantePreview } from "@/components/landing/comprobante-preview";
import { SiteHeader } from "@/components/landing/site-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { messages } from "@/lib/messages";

const CONFIANZA = [
  "Firma XML-DSig (RSA-SHA256)",
  "UBL 2.1 sobre el estándar SUNAT",
  "Reintentos automáticos si SUNAT no responde",
  "API REST con referencia interactiva",
];

const BENEFICIOS = [
  {
    titulo: "Cero rechazos por descuadre",
    descripcion:
      "Calculamos y validamos los totales en el servidor antes de firmar: no dependemos de que tu sistema arme bien el XML.",
  },
  {
    titulo: "Sigue facturando aunque SUNAT se caiga",
    descripcion:
      "Si SUNAT no responde, reintentamos automáticamente con espera creciente. Ningún comprobante se pierde ni se numera dos veces.",
  },
  {
    titulo: "Portal para vos, API para tu equipo",
    descripcion:
      "Emití y consultá desde el portal, o integrá la misma API a tu sistema — documentada, con referencia interactiva y prueba en vivo.",
  },
  {
    titulo: "Certificado y series en minutos",
    descripcion:
      "Subí tu certificado digital y tus credenciales SOL una sola vez, y configurá tus series desde el panel — sin tickets de soporte.",
  },
];

const AUDIENCIA = [
  {
    titulo: "PYMES y comercios",
    descripcion: "Emití, controlá tus series y llevá el historial de tu empresa desde un panel simple.",
  },
  {
    titulo: "Contadores y estudios",
    descripcion: "Administrá varias empresas (RUC) desde una sola cuenta y descargá XML y CDR cuando los necesites.",
  },
  {
    titulo: "Integradores y SaaS",
    descripcion: "Sumá la emisión a tu producto con una API REST documentada, sandbox y referencia interactiva.",
  },
];

const COMPROBANTES = [
  { codigo: "01", nombre: "Factura electrónica", disponible: true },
  { codigo: "03", nombre: "Boleta de venta", disponible: false },
  { codigo: "07", nombre: "Nota de crédito", disponible: false },
  { codigo: "08", nombre: "Nota de débito", disponible: false },
  { codigo: "RC", nombre: "Resumen diario de boletas", disponible: false },
  { codigo: "RA", nombre: "Comunicación de baja", disponible: false },
  { codigo: "09", nombre: "Guía de remisión", disponible: false },
  { codigo: "—", nombre: "Retención / percepción", disponible: false },
];

const PLANES = [
  { nombre: "Gratis", precio: "S/ 0", periodo: "para siempre", docs: "30 documentos/mes", rucs: "1 RUC", destacado: false },
  { nombre: "Emprende", precio: "S/ 29", periodo: "/mes", docs: "300 documentos/mes", rucs: "1 RUC", destacado: false },
  { nombre: "Negocio", precio: "S/ 69", periodo: "/mes", docs: "1 500 documentos/mes", rucs: "3 RUC", destacado: true },
  { nombre: "Pro", precio: "S/ 129", periodo: "/mes", docs: "Documentos ilimitados", rucs: "10 RUC", destacado: false },
];

export default function Home() {
  return (
    <div>
      <SiteHeader />

      <section className="mx-auto grid max-w-6xl items-center gap-12 px-6 py-20 md:grid-cols-2 md:py-28">
        <div>
          <h1 className="font-heading text-4xl leading-tight text-balance md:text-5xl">
            Comprobantes que SUNAT acepta a la primera.
          </h1>
          <p className="mt-5 max-w-md text-lg text-muted-foreground">
            Calculamos los totales en el servidor antes de firmar, así que los rechazos por descuadre no existen.
            Empezá gratis, para siempre, con tu primer RUC.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Button render={<Link href="/registro" />} nativeButton={false} className="h-11 px-6 text-base">
              Crear cuenta gratis
            </Button>
            <Button
              render={<Link href="/developers" />}
              nativeButton={false}
              variant="outline"
              className="h-11 px-6 text-base"
            >
              Ver documentación de la API
            </Button>
          </div>
          <p className="mt-4 text-sm text-muted-foreground">Sin tarjeta · Plan gratis permanente, no un trial.</p>
        </div>
        <ComprobantePreview />
      </section>

      <section className="border-y border-border bg-card">
        <div className="mx-auto grid max-w-6xl grid-cols-2 gap-6 px-6 py-8 text-sm text-muted-foreground md:grid-cols-4">
          {CONFIANZA.map((item) => (
            <div key={item} className="flex items-start gap-2">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-primary" />
              <span>{item}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-20">
        <h2 className="font-heading text-3xl">Menos trámite, más control</h2>
        <div className="mt-10 grid gap-6 sm:grid-cols-2">
          {BENEFICIOS.map((b) => (
            <Card key={b.titulo} className="p-6">
              <h3 className="font-heading text-lg">{b.titulo}</h3>
              <p className="mt-2 text-sm text-muted-foreground">{b.descripcion}</p>
            </Card>
          ))}
        </div>
      </section>

      <section className="bg-card">
        <div className="mx-auto max-w-6xl px-6 py-20">
          <h2 className="font-heading text-3xl">Hecho para tu tipo de negocio</h2>
          <div className="mt-10 grid gap-6 md:grid-cols-3">
            {AUDIENCIA.map((a) => (
              <div key={a.titulo}>
                <h3 className="font-heading text-lg">{a.titulo}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{a.descripcion}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-20">
        <h2 className="font-heading text-3xl">Comprobantes electrónicos</h2>
        <p className="mt-2 max-w-2xl text-muted-foreground">
          Empezamos por factura electrónica. El resto del catálogo SUNAT está en camino — lo marcamos tal cual para
          que sepas con qué contar hoy.
        </p>
        <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {COMPROBANTES.map((c) => (
            <div key={c.codigo} className="flex items-center gap-3 rounded-lg border border-border bg-card p-4">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-muted font-mono text-xs text-muted-foreground">
                {c.codigo}
              </span>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{c.nombre}</p>
                {c.disponible ? (
                  <Badge className="mt-1 border-transparent bg-success text-success-foreground">
                    <CheckCircle2 className="size-3" /> Disponible
                  </Badge>
                ) : (
                  <Badge variant="outline" className="mt-1">
                    <CircleDashed className="size-3" /> En construcción
                  </Badge>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section id="precios" className="bg-card">
        <div className="mx-auto max-w-6xl px-6 py-20">
          <h2 className="font-heading text-3xl">Planes claros, gratis para empezar</h2>
          <p className="mt-2 max-w-2xl text-muted-foreground">
            El plan Gratis no vence — no es un trial. Subís de plan cuando tu volumen lo pida.
          </p>
          <div className="mt-10 grid gap-6 md:grid-cols-4">
            {PLANES.map((p) => (
              <Card
                key={p.nombre}
                className={p.destacado ? "border-2 border-primary p-6" : "p-6"}
              >
                {p.destacado ? (
                  <Badge className="w-fit border-transparent bg-brand text-brand-foreground">Más usado</Badge>
                ) : null}
                <h3 className="mt-2 font-heading text-lg">{p.nombre}</h3>
                <p className="mt-1">
                  <span className="font-heading text-3xl">{p.precio}</span>{" "}
                  <span className="text-sm text-muted-foreground">{p.periodo}</span>
                </p>
                <ul className="mt-4 grid gap-1.5 text-sm text-muted-foreground">
                  <li>{p.docs}</li>
                  <li>{p.rucs}</li>
                </ul>
              </Card>
            ))}
          </div>
          <p className="mt-6 text-sm text-muted-foreground">
            ¿Necesitás on-premise o un plan a medida?{" "}
            <a href="mailto:hola@factura.pe" className="text-primary hover:underline">
              Escribinos
            </a>
            .
          </p>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-20 text-center">
        <h2 className="font-heading text-3xl">Emití tu primera factura hoy</h2>
        <p className="mx-auto mt-2 max-w-md text-muted-foreground">
          Creá tu cuenta, configurá tu empresa y verificá que SUNAT acepta tus comprobantes — sin pagar un sol.
        </p>
        <Button render={<Link href="/registro" />} nativeButton={false} className="mt-6 h-11 px-6 text-base">
          Crear cuenta gratis
        </Button>
      </section>

      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-6 py-8 text-sm text-muted-foreground sm:flex-row">
          <span>
            {messages.app.nombre} · {new Date().getFullYear()}
          </span>
          <div className="flex gap-4">
            <Link href="/developers" className="hover:text-foreground">
              Desarrolladores
            </Link>
            <Link href="/login" className="hover:text-foreground">
              Iniciar sesión
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
