"use client";

import { CheckIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useMessages } from "next-intl";
import { useState } from "react";
import { Badge } from "khipu-design-system/ui/badge";
import { Card } from "khipu-design-system/ui/card";
import { interpolar } from "@/i18n/mensajes";
import { cn } from "@/lib/utils";
import type { TarjetaDePlan } from "./planes-datos";

/**
 * Planes. Cada tarjeta dice para quién es, qué incluye y cómo se empieza, porque un precio sin eso obliga a adivinar.
 *
 * El selector mensual/anual no es adorno: el pago anual son diez mensualidades, o sea dos meses libres, y mostrarlo
 * como un total al año evita la cuenta mental. Todo lo que se lista existe hoy —límites, RUC, usuarios, API keys,
 * entornos, personalización del PDF y soporte—; lo que está en el roadmap no se vende acá. Los planes llegan armados de la página (H20): precio y límites son
 * los que publica el backoffice, así que un plan a medida no sale y un cambio de precio se ve sin tocar este archivo.
 */

/** Pagando al año se pagan diez meses. */
const MESES_QUE_SE_PAGAN = 10;

function soles(n: number) {
  return `S/ ${n.toLocaleString("es-PE", { maximumFractionDigits: 0 })}`;
}

export function Planes({ planes, abierto, contacto }: { planes: TarjetaDePlan[]; abierto: boolean; contacto: string | null }) {
  const messages = useMessages();
  const t = messages.landing.planes;
  const [anual, setAnual] = useState(false);
  const destinoCta = abierto ? "/registro" : contacto;

  return (
    <section id="precios" className="mx-auto max-w-6xl px-6 py-24">
      <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-acento-borde bg-acento-suave px-3 py-1 text-control font-medium text-foreground/80">
        <span className="size-1.5 rounded-full bg-acento" aria-hidden />
        {messages.landing.acciones.precios}
      </p>
      <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
        <div>
          <h2 className="font-heading text-3xl font-semibold tracking-[-0.015em] text-balance md:text-4xl">{t.titulo}</h2>
          <p className="mt-2 max-w-2xl text-muted-foreground">{t.descripcion}</p>
        </div>

        {/* Segmentado: el ahorro anual se ve antes de elegir, no después. */}
        <div className="inline-flex shrink-0 rounded-full border border-border bg-card p-1" role="group" aria-label={t.periodo}>
          {[
            { id: "mensual", etiqueta: t.mensual, valor: false },
            { id: "anual", etiqueta: t.anual, valor: true },
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
              {op.valor ? <span className={cn("ml-1.5 text-etiqueta", anual ? "text-primary-foreground/80" : "text-acento")}>{t.ahorroAnual}</span> : null}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-10 grid items-start gap-6 md:grid-cols-2 lg:grid-cols-4">
        {planes.map((p) => {
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
                  <p className="mt-0.5 text-control text-muted-foreground">{p.para}</p>
                </div>
                {p.destacado ? <Badge className="shrink-0 border-transparent bg-acento text-[#1b1206]">{t.masUsado}</Badge> : null}
              </div>

              <p className="mt-5 flex items-baseline gap-1.5">
                <span className="font-heading text-[2rem] leading-none font-semibold tracking-[-0.02em] tabular-nums">
                  {p.mensual === 0 ? "S/ 0" : soles(porMes)}
                </span>
                <span className="text-sm text-muted-foreground">{p.mensual === 0 ? t.paraSiempre : t.porMes}</span>
              </p>
              {/* Altura reservada: sin esto, las tarjetas saltan al cambiar de periodo. */}
              <p className="mt-1 min-h-[1.25rem] text-control text-acento">
                {anual && p.mensual > 0 ? interpolar(t.alAno, { monto: soles(alAno) }) : ""}
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
                  {abierto ? (p.mensual === 0 ? t.empezarGratis : interpolar(t.elegir, { plan: p.nombre })) : t.pedirAcceso}
                </Link>
              ) : null}

              <ul className="mt-5 grid gap-2 text-sm text-muted-foreground">
                {p.incluye.map((f) => (
                  <li key={f} className="flex gap-2">
                    <CheckIcon weight="bold" className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
            </Card>
          );
        })}
      </div>

      <p className="mt-8 text-sm text-muted-foreground">
        {t.nota}{" "}
        {contacto ? (
          <>
            {t.aMedida}{" "}
            <a href={contacto} className="text-primary hover:underline">
              {t.escribenos}
            </a>
            .
          </>
        ) : null}
      </p>
    </section>
  );
}
