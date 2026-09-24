"use client";

import { CheckIcon } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";

/**
 * Planes. Cada tarjeta dice para quién es, qué incluye y cómo se empieza, porque un precio sin eso obliga a adivinar.
 *
 * El selector mensual/anual no es adorno: el pago anual son diez mensualidades, o sea dos meses libres, y mostrarlo
 * como un total al año evita la cuenta mental. Todo lo que se lista existe hoy —límites, RUC, usuarios, API keys,
 * entornos, personalización del PDF y soporte—; lo que está en el roadmap no se vende acá.
 */
const PLANES = [
  {
    nombre: "Gratis",
    para: "Para probar la API",
    mensual: 0,
    docs: "30 documentos/mes",
    rucs: "1 RUC · 1 usuario",
    incluye: [
      "Factura, boleta, nota de crédito y de débito",
      "Portal web y API REST",
      "Entorno de pruebas de SUNAT",
      "1 API key · PDF A4",
      "XML y CDR guardados 1 año",
    ],
    destacado: false,
  },
  {
    nombre: "Emprende",
    para: "Bodegas, freelancers y tiendas",
    mensual: 29,
    docs: "300 documentos/mes",
    rucs: "1 RUC · 1 usuario",
    incluye: ["Todo lo de Gratis", "Producción además de pruebas", "2 API keys", "XML y CDR guardados 5 años", "Soporte por correo (48 h)"],
    destacado: false,
  },
  {
    nombre: "Negocio",
    para: "PYMES y estudios contables",
    mensual: 69,
    docs: "1 500 documentos/mes",
    rucs: "3 RUC · 3 usuarios",
    incluye: ["Todo lo de Emprende", "PDF con tu logo y tu color", "5 API keys", "Soporte por correo y WhatsApp (24 h)"],
    destacado: true,
  },
  {
    nombre: "Pro",
    para: "SaaS, ISV y alto volumen",
    mensual: 129,
    docs: "Documentos ilimitados",
    rucs: "10 RUC · usuarios ilimitados",
    incluye: ["Todo lo de Negocio", "API keys ilimitadas", "Soporte prioritario (8 h hábiles)"],
    destacado: false,
  },
];

/** Pagando al año se pagan diez meses. */
const MESES_QUE_SE_PAGAN = 10;

function soles(n: number) {
  return `S/ ${n.toLocaleString("es-PE", { maximumFractionDigits: 0 })}`;
}

export function Planes({ abierto, contacto }: { abierto: boolean; contacto: string | null }) {
  const [anual, setAnual] = useState(false);
  const destinoCta = abierto ? "/registro" : contacto;

  return (
    <section id="precios" className="mx-auto max-w-6xl px-6 py-24">
      <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-acento-borde bg-acento-suave px-3 py-1 text-[12px] font-medium text-foreground/80">
        <span className="size-1.5 rounded-full bg-acento" aria-hidden />
        Precios
      </p>
      <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
        <div>
          <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">
            Planes claros, gratis para empezar
          </h2>
          <p className="mt-2 max-w-2xl text-muted-foreground">
            El plan Gratis no vence — no es un trial. Subes de plan cuando tu volumen lo pida.
          </p>
        </div>

        {/* Segmentado: el ahorro anual se ve antes de elegir, no después. */}
        <div className="inline-flex shrink-0 rounded-full border border-border bg-card p-1" role="group" aria-label="Periodo de facturación">
          {[
            { id: "mensual", etiqueta: "Mensual", valor: false },
            { id: "anual", etiqueta: "Anual", valor: true },
          ].map((op) => (
            <button
              key={op.id}
              type="button"
              onClick={() => setAnual(op.valor)}
              aria-pressed={anual === op.valor}
              className={cn(
                "rounded-full px-4 py-1.5 text-sm font-medium transition-colors",
                anual === op.valor ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {op.etiqueta}
              {op.valor ? <span className={cn("ml-1.5 text-[11px]", anual ? "text-primary-foreground/80" : "text-acento")}>−2 meses</span> : null}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-10 grid items-start gap-6 md:grid-cols-2 lg:grid-cols-4">
        {PLANES.map((p) => {
          const alAno = p.mensual * MESES_QUE_SE_PAGAN;
          const porMes = anual && p.mensual > 0 ? Math.round((alAno / 12) * 100) / 100 : p.mensual;
          return (
            <Card
              key={p.nombre}
              className={cn(
                "flex h-full flex-col p-6 transition-all duration-300",
                p.destacado
                  ? "border-acento-borde shadow-[0_1px_2px_rgba(16,35,31,0.04),0_12px_32px_-12px_rgba(217,140,43,0.35)] lg:-translate-y-3"
                  : "hover:-translate-y-1 hover:shadow-[0_1px_2px_rgba(16,35,31,0.04),0_12px_28px_-18px_rgba(16,35,31,0.35)]",
              )}
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h3 className="font-heading text-lg font-semibold">{p.nombre}</h3>
                  <p className="mt-0.5 text-[13px] text-muted-foreground">{p.para}</p>
                </div>
                {p.destacado ? <Badge className="shrink-0 border-transparent bg-acento text-[#1b1206]">Más usado</Badge> : null}
              </div>

              <p className="mt-5 flex items-baseline gap-1.5">
                <span className="font-heading text-[2rem] leading-none font-semibold tracking-[-0.02em] tabular-nums">
                  {p.mensual === 0 ? "S/ 0" : soles(porMes)}
                </span>
                <span className="text-sm text-muted-foreground">{p.mensual === 0 ? "para siempre" : "/mes"}</span>
              </p>
              {/* Altura reservada: sin esto, las tarjetas saltan al cambiar de periodo. */}
              <p className="mt-1 min-h-[1.25rem] text-[12px] text-acento">
                {anual && p.mensual > 0 ? `${soles(alAno)} al año · 2 meses libres` : ""}
              </p>

              <div className="mt-4 rounded-lg bg-muted/60 px-3 py-2.5 text-sm">
                <p className="font-medium text-foreground">{p.docs}</p>
                <p className="text-muted-foreground">{p.rucs}</p>
              </div>

              {destinoCta ? (
                <Link
                  href={destinoCta}
                  className={cn(
                    "mt-5 inline-flex h-10 items-center justify-center rounded-lg px-4 text-sm font-medium transition-colors",
                    p.destacado
                      ? "bg-primary text-primary-foreground hover:bg-primary/90"
                      : "border border-border bg-card text-foreground hover:bg-muted",
                  )}
                >
                  {abierto ? (p.mensual === 0 ? "Empezar gratis" : `Elegir ${p.nombre}`) : "Pedir acceso"}
                </Link>
              ) : null}

              <ul className="mt-5 grid gap-2 text-sm text-muted-foreground">
                {p.incluye.map((f) => (
                  <li key={f} className="flex gap-2">
                    <CheckIcon className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
            </Card>
          );
        })}
      </div>

      <p className="mt-8 text-sm text-muted-foreground">
        Los documentos se cuentan cuando SUNAT los acepta: los rechazos y los reintentos no consumen tu plan.{" "}
        {contacto ? (
          <>
            ¿Necesitas on-premise o un plan a medida?{" "}
            <a href={contacto} className="text-primary hover:underline">
              Escríbenos
            </a>
            .
          </>
        ) : null}
      </p>
    </section>
  );
}
