import { ArrowClockwiseIcon, BuildingsIcon, CodeIcon, KeyIcon, ShieldCheckIcon, SquaresFourIcon, UsersIcon } from "@phosphor-icons/react/dist/ssr";
import Link from "next/link";
import { ComoFunciona } from "@/components/landing/como-funciona";
import { ComprobantePreview } from "@/components/landing/comprobante-preview";
import { Faq } from "@/components/landing/faq";
import { Planes } from "@/components/landing/planes";
import { tarjetasDePlanes } from "@/components/landing/planes-datos";
import { SiteHeader } from "@/components/landing/site-header";
import { Badge } from "khipu-design-system/ui/badge";
import { Button } from "khipu-design-system/ui/button";
import { contactoUrl, registroAbierto } from "@/lib/acceso";
import { obtenerPlanesPublicados } from "@/lib/api/planes-publicados";
import { getMessages } from "next-intl/server";
import { TextosDeCliente } from "@/i18n/textos-de-cliente";
import { TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";

// Los textos están en `landing.*` de messages; acá queda solo el icono (y el código SUNAT) de cada tarjeta.
const BENEFICIOS = [
  { icono: ShieldCheckIcon, clave: "descuadre" },
  { icono: ArrowClockwiseIcon, clave: "caida" },
  { icono: SquaresFourIcon, clave: "portal" },
  { icono: KeyIcon, clave: "configuracion" },
] as const;

const AUDIENCIA = [
  { icono: BuildingsIcon, clave: "pymes" },
  { icono: UsersIcon, clave: "contadores" },
  { icono: CodeIcon, clave: "integradores" },
] as const;

const COMPROBANTES = [
  { codigo: "01", clave: "factura", disponible: true },
  { codigo: "03", clave: "boleta", disponible: true },
  { codigo: "07", clave: "notaCredito", disponible: true },
  { codigo: "08", clave: "notaDebito", disponible: true },
  // Las boletas se envían una por una (#20): el resumen diario se usa para anularlas.
  { codigo: "RC", clave: "resumenDiario", disponible: true },
  { codigo: "RA", clave: "comunicacionBaja", disponible: true },
  { codigo: "09", clave: "guiaRemision", disponible: false },
  { codigo: "—", clave: "retencionPercepcion", disponible: false },
] as const;

// Se renderiza en cada request, no en el build: `REGISTRO_ABIERTO` y `CONTACTO_URL` son variables del entorno de despliegue
// (Railway las inyecta en runtime) y prerenderizar dejaría el flag congelado con lo que hubiera al construir la imagen.
export const dynamic = "force-dynamic";

export default async function Home() {
  // H20: los precios que publica el backoffice; si el backend no responde, la lista de respaldo.
  const messages = await getMessages();
  const t = messages.landing;
  const planes = tarjetasDePlanes(await obtenerPlanesPublicados(), messages);
  // Beta por invitación mientras el autoservicio (registro → onboarding → certificado → SOL) no esté certificado.
  // El texto que se muestra NO dice eso: al visitante se le cuenta cómo entra, no en qué anda nuestra certificación.
  const abierto = registroAbierto();
  const contacto = contactoUrl();
  return (
    <TextosDeCliente segmento="landing">
      <div>
        <SiteHeader />

        <section className="mx-auto grid max-w-6xl items-center gap-12 overflow-x-clip px-6 py-20 md:grid-cols-[1.05fr_1fr] md:py-28">
          {/* Una sola entrada orquestada, escalonada, y solo si el visitante no pidió menos movimiento. */}
          <div className="motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-3 motion-safe:duration-700">
            <p className="mb-5 inline-flex items-center gap-2 rounded-full border border-acento-borde bg-acento-suave px-3 py-1 text-control font-medium text-foreground/80">
              <span className="size-1.5 rounded-full bg-acento" aria-hidden />
              {t.hero.etiqueta}
            </p>
            <h1 className="font-heading text-[2.6rem] leading-[1.05] font-semibold tracking-[-0.02em] text-balance md:text-6xl">
              {t.hero.titulo}
            </h1>
            <p className="mt-6 max-w-[38ch] text-lg leading-relaxed text-muted-foreground">{t.hero.descripcion}</p>
            <div className="mt-8 flex flex-wrap gap-3 motion-safe:animate-in motion-safe:fade-in motion-safe:duration-700 motion-safe:delay-150 motion-safe:fill-mode-backwards">
              {abierto ? (
                <Button render={<Link href="/registro" />} nativeButton={false} className="h-11 px-6 text-base">
                  {t.acciones.crearCuentaGratis}
                </Button>
              ) : contacto ? (
                <Button render={<a href={contacto} />} nativeButton={false} className="h-11 px-6 text-base">
                  {t.acciones.solicitarAcceso}
                </Button>
              ) : null}
              <Button
                render={<Link href="/developers" />}
                nativeButton={false}
                variant={abierto || contacto ? "outline" : undefined}
                className="h-11 px-6 text-base"
              >
                {t.acciones.verDocumentacion}
              </Button>
            </div>
            <p className="mt-4 text-sm text-muted-foreground">{abierto ? t.hero.notaAbierto : t.hero.notaCerrado}</p>
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
          <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-y-3 px-6 py-7 text-center text-control tracking-[0.01em] text-muted-foreground sm:divide-x sm:divide-border">
            {t.confianza.map((item) => (
              <span key={item} className="px-6 first:pl-0 last:pr-0">
                {item}
              </span>
            ))}
          </div>
        </section>

        <ComoFunciona />

        <section className="bg-card">
          <div className="mx-auto max-w-6xl px-6 py-24">
            <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-acento-borde bg-acento-suave px-3 py-1 text-control font-medium text-foreground/80">
              <span className="size-1.5 rounded-full bg-acento" aria-hidden />
              {t.beneficios.etiqueta}
            </p>
            <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">{t.beneficios.titulo}</h2>
            <p className="mt-2 max-w-2xl text-muted-foreground">{t.beneficios.descripcion}</p>
            <div className="mt-10 grid gap-5 sm:grid-cols-2">
              {BENEFICIOS.map((b) => (
                <div key={b.clave} className="group rounded-xl border border-border bg-background p-6 transition-all duration-300 hover:-translate-y-1 hover:border-acento-borde hover:shadow-[0_1px_2px_rgba(16,35,31,0.04),0_12px_28px_-18px_rgba(16,35,31,0.35)]">
                  <span className="mb-4 flex size-9 items-center justify-center rounded-lg bg-acento-suave text-acento-texto dark:text-acento ring-1 ring-acento-borde" aria-hidden>
                    <b.icono className="size-[18px]" />
                  </span>
                  <h3 className="font-heading text-lg font-semibold">{t.beneficios[b.clave].titulo}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{t.beneficios[b.clave].descripcion}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 py-24">
          <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">{t.audiencia.titulo}</h2>
          <p className="mt-2 max-w-2xl text-muted-foreground">{t.audiencia.descripcion}</p>
          <div className="mt-10 grid gap-5 md:grid-cols-3">
            {AUDIENCIA.map((a) => (
              <div key={a.clave} className={cn(TARJETA, "group p-6 transition-all duration-300 hover:-translate-y-1 hover:border-acento-borde hover:shadow-[0_1px_2px_rgba(16,35,31,0.04),0_12px_28px_-18px_rgba(16,35,31,0.35)]")}>
                <span className="mb-4 flex size-9 items-center justify-center rounded-lg bg-acento-suave text-acento-texto dark:text-acento ring-1 ring-acento-borde" aria-hidden>
                  <a.icono className="size-[18px]" />
                </span>
                <h3 className="font-heading text-lg font-semibold">{t.audiencia[a.clave].titulo}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{t.audiencia[a.clave].descripcion}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="bg-card">
          <div className="mx-auto max-w-6xl px-6 py-24">
            <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">{t.comprobantes.titulo}</h2>
            <p className="mt-2 max-w-2xl text-muted-foreground">{t.comprobantes.descripcion}</p>
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
                    <p className="truncate text-sm font-medium">{t.comprobantes.tipos[c.clave]}</p>
                    {c.disponible ? (
                      <Badge className="mt-1 border-transparent bg-success text-success-foreground">{t.comprobantes.disponible}</Badge>
                    ) : (
                      <Badge variant="outline" className="mt-1">
                        {t.comprobantes.enConstruccion}
                      </Badge>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <Planes planes={planes} abierto={abierto} contacto={contacto} />

        <Faq />

        <section className="bg-foreground text-background">
          <div className="mx-auto max-w-6xl px-6 py-20 text-center">
            <h2 className="font-heading text-3xl">{abierto ? t.cierre.tituloAbierto : t.cierre.tituloCerrado}</h2>
            <p className="mx-auto mt-2 max-w-md text-background/70">
              {abierto ? t.cierre.descripcionAbierto : t.cierre.descripcionCerrado}
            </p>
            {abierto ? (
              <Button
                render={<Link href="/registro" />}
                nativeButton={false}
                className="mt-6 h-11 bg-background px-6 text-base text-foreground hover:bg-background/85"
              >
                {t.acciones.crearCuentaGratis}
              </Button>
            ) : contacto ? (
              <Button
                render={<a href={contacto} />}
                nativeButton={false}
                className="mt-6 h-11 bg-background px-6 text-base text-foreground hover:bg-background/85"
              >
                {t.acciones.solicitarAcceso}
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
                {t.acciones.desarrolladores}
              </Link>
              <Link href="/login" className="hover:text-foreground">
                {t.acciones.iniciarSesion}
              </Link>
              {/* #342: los textos legales, públicos y al alcance de quien todavía no tiene cuenta. */}
              <Link href="/terminos" className="hover:text-foreground">
                {messages.legal.terminos}
              </Link>
              <Link href="/privacidad" className="hover:text-foreground">
                {messages.legal.privacidad}
              </Link>
            </div>
          </div>
        </footer>
      </div>
    </TextosDeCliente>
  );
}
