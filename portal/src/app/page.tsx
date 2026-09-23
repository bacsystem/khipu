import { Building2, Code2, Users } from "lucide-react";
import Link from "next/link";
import { ComoFunciona } from "@/components/landing/como-funciona";
import { ComprobantePreview } from "@/components/landing/comprobante-preview";
import { Faq } from "@/components/landing/faq";
import { SiteHeader } from "@/components/landing/site-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { contactoUrl, registroAbierto } from "@/lib/acceso";
import { messages } from "@/lib/messages";

const CONFIANZA = ["Firma XML-DSig (RSA-SHA256)", "UBL 2.1 sobre el estándar SUNAT", "Reintentos automáticos", "API REST documentada"];

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
    icono: Building2,
    titulo: "PYMES y comercios",
    descripcion: "Emití, controlá tus series y llevá el historial de tu empresa desde un panel simple.",
  },
  {
    icono: Users,
    titulo: "Contadores y estudios",
    descripcion: "Administrá varias empresas (RUC) desde una sola cuenta y descargá XML y CDR cuando los necesites.",
  },
  {
    icono: Code2,
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

// Se renderiza en cada request, no en el build: `REGISTRO_ABIERTO` y `CONTACTO_URL` son variables del entorno de despliegue
// (Railway las inyecta en runtime) y prerenderizar dejaría el flag congelado con lo que hubiera al construir la imagen.
export const dynamic = "force-dynamic";

export default function Home() {
  // Beta por invitación mientras el autoservicio (registro → onboarding → certificado → SOL) no esté certificado.
  const abierto = registroAbierto();
  const contacto = contactoUrl();
  return (
    <div>
      <SiteHeader />

      <section className="mx-auto grid max-w-6xl items-center gap-12 overflow-x-clip px-6 py-20 md:grid-cols-2 md:py-28">
        <div>
          <h1 className="font-heading text-4xl leading-[1.08] tracking-tight text-balance md:text-5xl">
            Comprobantes que SUNAT acepta a la primera.
          </h1>
          <p className="mt-5 max-w-md text-lg text-muted-foreground">
            Calculamos los totales en el servidor antes de firmar, así que los rechazos por descuadre no existen.
            Empezá gratis, para siempre, con tu primer RUC.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            {abierto ? (
              <Button render={<Link href="/registro" />} nativeButton={false} className="h-11 px-6 text-base">
                Crear cuenta gratis
              </Button>
            ) : contacto ? (
              <Button render={<a href={contacto} />} nativeButton={false} className="h-11 px-6 text-base">
                Solicitar acceso
              </Button>
            ) : null}
            <Button
              render={<Link href="/developers" />}
              nativeButton={false}
              variant={abierto || contacto ? "outline" : undefined}
              className="h-11 px-6 text-base"
            >
              Ver documentación de la API
            </Button>
          </div>
          <p className="mt-4 text-sm text-muted-foreground">
            {abierto ? "Sin tarjeta · Plan gratis permanente, no un trial." : "Beta por invitación: las cuentas se habilitan una por una mientras cerramos la certificación."}
          </p>
        </div>
        <div className="relative">
          <div
            aria-hidden
            className="absolute -inset-x-10 -inset-y-12 -z-10 bg-[radial-gradient(closest-side,var(--primary),transparent)] opacity-[0.07]"
          />
          <ComprobantePreview />
        </div>
      </section>

      <section className="border-y border-border bg-card">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-y-3 px-6 py-6 text-center text-sm text-muted-foreground sm:divide-x sm:divide-border">
          {CONFIANZA.map((item) => (
            <span key={item} className="px-6 first:pl-0 last:pr-0">
              {item}
            </span>
          ))}
        </div>
      </section>

      <ComoFunciona />

      <section className="bg-card">
        <div className="mx-auto max-w-6xl px-6 py-20">
          <h2 className="font-heading text-3xl">Menos trámite, más control</h2>
          <div className="mt-8 grid gap-x-10 gap-y-8 border-t border-border pt-8 sm:grid-cols-2">
            {BENEFICIOS.map((b) => (
              <div key={b.titulo}>
                <h3 className="font-heading text-lg">{b.titulo}</h3>
                <p className="mt-2 max-w-sm text-sm text-muted-foreground">{b.descripcion}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-20">
        <h2 className="font-heading text-3xl">Hecho para tu tipo de negocio</h2>
        <div className="mt-10 grid gap-x-10 gap-y-8 md:grid-cols-3 md:divide-x md:divide-border">
          {AUDIENCIA.map((a) => (
            <div key={a.titulo} className="md:px-8 md:first:pl-0 md:last:pr-0">
              <a.icono className="size-5 text-primary" />
              <h3 className="font-heading mt-3 text-lg">{a.titulo}</h3>
              <p className="mt-2 text-sm text-muted-foreground">{a.descripcion}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="bg-card">
        <div className="mx-auto max-w-6xl px-6 py-20">
          <h2 className="font-heading text-3xl">Comprobantes electrónicos</h2>
          <p className="mt-2 max-w-2xl text-muted-foreground">
            Empezamos por factura electrónica. El resto del catálogo SUNAT está en camino — lo marcamos tal cual
            para que sepas con qué contar hoy.
          </p>
          <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {COMPROBANTES.map((c) => (
              <div key={c.codigo} className="flex items-center gap-3 rounded-lg border border-border bg-background p-4">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-muted font-mono text-xs text-muted-foreground">
                  {c.codigo}
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{c.nombre}</p>
                  {c.disponible ? (
                    <Badge className="mt-1 border-transparent bg-success text-success-foreground">Disponible</Badge>
                  ) : (
                    <Badge variant="outline" className="mt-1">
                      En construcción
                    </Badge>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section id="precios" className="mx-auto max-w-6xl px-6 py-20">
        <h2 className="font-heading text-3xl">Planes claros, gratis para empezar</h2>
        <p className="mt-2 max-w-2xl text-muted-foreground">
          El plan Gratis no vence — no es un trial. Subís de plan cuando tu volumen lo pida.
        </p>
        <div className="mt-10 grid gap-6 md:grid-cols-4">
          {PLANES.map((p) => (
            <Card
              key={p.nombre}
              className={p.destacado ? "p-6 ring-2 ring-primary md:-translate-y-3 md:shadow-lg" : "p-6"}
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
      </section>

      <Faq />

      <section className="bg-foreground text-background">
        <div className="mx-auto max-w-6xl px-6 py-20 text-center">
          <h2 className="font-heading text-3xl">{abierto ? "Emití tu primera factura hoy" : "Pedí acceso a la beta"}</h2>
          <p className="mx-auto mt-2 max-w-md text-background/70">
            {abierto
              ? "Creá tu cuenta, configurá tu empresa y verificá que SUNAT acepta tus comprobantes — sin pagar un sol."
              : "Habilitamos cuentas de a una para acompañar la configuración del certificado y las credenciales SOL."}
          </p>
          {abierto ? (
            <Button
              render={<Link href="/registro" />}
              nativeButton={false}
              className="mt-6 h-11 bg-background px-6 text-base text-foreground hover:bg-background/85"
            >
              Crear cuenta gratis
            </Button>
          ) : contacto ? (
            <Button
              render={<a href={contacto} />}
              nativeButton={false}
              className="mt-6 h-11 bg-background px-6 text-base text-foreground hover:bg-background/85"
            >
              Solicitar acceso
            </Button>
          ) : null}
        </div>
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
