import { Building2, Code2, KeyRound, LayoutDashboard, RefreshCw, ShieldCheck, Users } from "lucide-react";
import Link from "next/link";
import { ComoFunciona } from "@/components/landing/como-funciona";
import { ComprobantePreview } from "@/components/landing/comprobante-preview";
import { Faq } from "@/components/landing/faq";
import { Planes } from "@/components/landing/planes";
import { SiteHeader } from "@/components/landing/site-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { contactoUrl, registroAbierto } from "@/lib/acceso";
import { messages } from "@/lib/messages";

const CONFIANZA = ["Firma XML-DSig (RSA-SHA256)", "UBL 2.1 sobre el estándar SUNAT", "Reintentos automáticos", "API REST documentada"];

const BENEFICIOS = [
  {
    icono: ShieldCheck,
    titulo: "Cero rechazos por descuadre",
    descripcion:
      "Calculamos y validamos los totales en el servidor antes de firmar: no dependemos de que tu sistema arme bien el XML.",
  },
  {
    icono: RefreshCw,
    titulo: "Sigue facturando aunque SUNAT se caiga",
    descripcion:
      "Si SUNAT no responde, reintentamos automáticamente con espera creciente. Ningún comprobante se pierde ni se numera dos veces.",
  },
  {
    icono: LayoutDashboard,
    titulo: "Portal para ti, API para tu equipo",
    descripcion:
      "Emite y consulta desde el portal, o integra la misma API a tu sistema — documentada, con referencia interactiva y prueba en vivo.",
  },
  {
    icono: KeyRound,
    titulo: "Certificado y series en minutos",
    descripcion:
      "Sube tu certificado digital y tus credenciales SOL una sola vez, y configura tus series desde el panel — sin tickets de soporte.",
  },
];

const AUDIENCIA = [
  {
    icono: Building2,
    titulo: "PYMES y comercios",
    descripcion: "Emite, controla tus series y lleva el historial de tu empresa desde un panel simple.",
  },
  {
    icono: Users,
    titulo: "Contadores y estudios",
    descripcion: "Administra varias empresas (RUC) desde una sola cuenta y descarga XML y CDR cuando los necesites.",
  },
  {
    icono: Code2,
    titulo: "Integradores y SaaS",
    descripcion: "Suma la emisión a tu producto con una API REST documentada, sandbox y referencia interactiva.",
  },
];

const COMPROBANTES = [
  { codigo: "01", nombre: "Factura electrónica", disponible: true },
  { codigo: "03", nombre: "Boleta de venta", disponible: false },
  { codigo: "07", nombre: "Nota de crédito", disponible: true },
  { codigo: "08", nombre: "Nota de débito", disponible: true },
  { codigo: "RC", nombre: "Resumen diario de boletas", disponible: false },
  { codigo: "RA", nombre: "Comunicación de baja", disponible: true },
  { codigo: "09", nombre: "Guía de remisión", disponible: false },
  { codigo: "—", nombre: "Retención / percepción", disponible: false },
];

// Se renderiza en cada request, no en el build: `REGISTRO_ABIERTO` y `CONTACTO_URL` son variables del entorno de despliegue
// (Railway las inyecta en runtime) y prerenderizar dejaría el flag congelado con lo que hubiera al construir la imagen.
export const dynamic = "force-dynamic";

export default function Home() {
  // Beta por invitación mientras el autoservicio (registro → onboarding → certificado → SOL) no esté certificado.
  // El texto que se muestra NO dice eso: al visitante se le cuenta cómo entra, no en qué anda nuestra certificación.
  const abierto = registroAbierto();
  const contacto = contactoUrl();
  return (
    <div>
      <SiteHeader />

      <section className="mx-auto grid max-w-6xl items-center gap-12 overflow-x-clip px-6 py-20 md:grid-cols-[1.05fr_1fr] md:py-28">
        {/* Una sola entrada orquestada, escalonada, y solo si el visitante no pidió menos movimiento. */}
        <div className="motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-3 motion-safe:duration-700">
          <p className="mb-5 inline-flex items-center gap-2 rounded-full border border-acento-borde bg-acento-suave px-3 py-1 text-[12px] font-medium text-foreground/80">
            <span className="size-1.5 rounded-full bg-acento" aria-hidden />
            Facturación electrónica para Perú
          </p>
          <h1 className="font-heading text-[2.6rem] leading-[1.05] font-semibold tracking-[-0.02em] text-balance md:text-6xl">
            Comprobantes que SUNAT acepta a la primera.
          </h1>
          <p className="mt-6 max-w-[38ch] text-lg leading-relaxed text-muted-foreground">
            Calculamos los totales en el servidor antes de firmar, así que los rechazos por descuadre no existen.
            Empieza gratis, para siempre, con tu primer RUC.
          </p>
          <div className="mt-8 flex flex-wrap gap-3 motion-safe:animate-in motion-safe:fade-in motion-safe:duration-700 motion-safe:delay-150 motion-safe:fill-mode-backwards">
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
            {abierto ? "Sin tarjeta · Plan gratis permanente, no un trial." : "Beta por invitación: habilitamos las cuentas una por una y acompañamos cada alta."}
          </p>
        </div>
        <div className="relative motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-4 motion-safe:duration-1000 motion-safe:delay-100 motion-safe:fill-mode-backwards">
          <div
            aria-hidden
            className="absolute -inset-x-10 -inset-y-12 -z-10 bg-[radial-gradient(closest-side,var(--primary),transparent)] opacity-[0.09]"
          />
          <ComprobantePreview />
        </div>
      </section>

      <section className="border-y border-border bg-card">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-y-3 px-6 py-7 text-center text-[13px] tracking-[0.01em] text-muted-foreground sm:divide-x sm:divide-border">
          {CONFIANZA.map((item) => (
            <span key={item} className="px-6 first:pl-0 last:pr-0">
              {item}
            </span>
          ))}
        </div>
      </section>

      <ComoFunciona />

      <section className="bg-card">
        <div className="mx-auto max-w-6xl px-6 py-24">
          <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-acento-borde bg-acento-suave px-3 py-1 text-[12px] font-medium text-foreground/80">
            <span className="size-1.5 rounded-full bg-acento" aria-hidden />
            Por qué khipu
          </p>
          <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">Menos trámite, más control</h2>
          <p className="mt-2 max-w-2xl text-muted-foreground">
            Lo que cambia cuando el cálculo, la firma y el reintento no son tu problema.
          </p>
          <div className="mt-10 grid gap-5 sm:grid-cols-2">
            {BENEFICIOS.map((b) => (
              <div key={b.titulo} className="group rounded-xl border border-border bg-background p-6 transition-all duration-300 hover:-translate-y-1 hover:border-acento-borde hover:shadow-[0_1px_2px_rgba(16,35,31,0.04),0_12px_28px_-18px_rgba(16,35,31,0.35)]">
                <span className="mb-4 flex size-9 items-center justify-center rounded-lg bg-acento-suave text-acento ring-1 ring-acento-borde" aria-hidden>
                  <b.icono className="size-[18px]" />
                </span>
                <h3 className="font-heading text-lg font-semibold">{b.titulo}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{b.descripcion}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-24">
        <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">Hecho para tu tipo de negocio</h2>
        <p className="mt-2 max-w-2xl text-muted-foreground">La misma plataforma, según de qué lado la mires.</p>
        <div className="mt-10 grid gap-5 md:grid-cols-3">
          {AUDIENCIA.map((a) => (
            <div key={a.titulo} className="group rounded-xl border border-border bg-card p-6 transition-all duration-300 hover:-translate-y-1 hover:border-acento-borde hover:shadow-[0_1px_2px_rgba(16,35,31,0.04),0_12px_28px_-18px_rgba(16,35,31,0.35)]">
              <span className="mb-4 flex size-9 items-center justify-center rounded-lg bg-acento-suave text-acento ring-1 ring-acento-borde" aria-hidden>
                <a.icono className="size-[18px]" />
              </span>
              <h3 className="font-heading text-lg font-semibold">{a.titulo}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{a.descripcion}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="bg-card">
        <div className="mx-auto max-w-6xl px-6 py-24">
          <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">Comprobantes electrónicos</h2>
          <p className="mt-2 max-w-2xl text-muted-foreground">
            Factura, notas de crédito y de débito, y comunicación de baja, todas emitiendo hoy. El resto del catálogo
            de SUNAT está en camino, y lo marcamos tal cual para que sepas con qué contar.
          </p>
          <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {COMPROBANTES.map((c) => (
              <div
                key={c.codigo}
                className={`flex items-center gap-3 rounded-lg border bg-background p-4 transition-colors ${c.disponible ? "border-accent-border" : "border-border"}`}
              >
                <span
                  className={`flex size-9 shrink-0 items-center justify-center rounded-md font-mono text-xs ${c.disponible ? "bg-accent text-accent-foreground" : "bg-muted text-muted-foreground"}`}
                >
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

      <Planes abierto={abierto} contacto={contacto} />

      <Faq />

      <section className="bg-foreground text-background">
        <div className="mx-auto max-w-6xl px-6 py-20 text-center">
          <h2 className="font-heading text-3xl">{abierto ? "Emite tu primera factura hoy" : "Pide acceso a la beta"}</h2>
          <p className="mx-auto mt-2 max-w-md text-background/70">
            {abierto
              ? "Crea tu cuenta, configura tu empresa y verifica que SUNAT acepta tus comprobantes — sin pagar un sol."
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
