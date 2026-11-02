import { WebhooksLogoIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import Link from "next/link";
import { useMessages } from "next-intl";
import type { Webhook } from "@/lib/api/webhooks";
import { NuevoWebhookDialog } from "./nuevo-webhook-dialog";
import { WebhookCard } from "./webhook-card";
import { TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/**
 * Los webhooks de la empresa (#19): en vez de consultar cada comprobante, khipu avisa con un POST firmado a la URL del cliente cuando cambia de estado. `webhooks` es nulo si
 * no se pudieron leer: la página de llaves sigue funcionando y la sección lo dice.
 */
export function WebhooksSection({ webhooks }: { webhooks: Webhook[] | null }) {
  const t = useMessages().webhooks.seccion;
  return (
    <section aria-labelledby="webhooks-titulo" className="grid min-w-0 grid-cols-1 gap-4" data-testid="webhooks">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
            <WebhooksLogoIcon weight={PESO_ICONO} className="size-4" />
          </div>
          <div className="min-w-0">
            <h2 id="webhooks-titulo" className="text-cuerpo font-semibold text-foreground">
              {t.titulo}
            </h2>
            <p className="text-control text-muted-foreground">
              {t.descripcion}{" "}
              <Link href="/developers/guia#webhooks" className="text-primary hover:underline">
                {t.comoVerificar}
              </Link>
            </p>
          </div>
        </div>
        {webhooks ? <NuevoWebhookDialog /> : null}
      </div>

      {webhooks === null ? (
        <p role="alert" className={cn(TARJETA, "px-4 py-6 text-center text-cuerpo text-muted-foreground")}>
          {t.errorLectura}
        </p>
      ) : webhooks.length === 0 ? (
        <div className={cn(TARJETA, "flex flex-col items-center gap-2 px-4 py-10 text-center text-muted-foreground")}>
          <WebhooksLogoIcon weight={PESO_ICONO} className="size-6" />
          <p className="text-cuerpo">{t.vacia}</p>
        </div>
      ) : (
        <div className="grid gap-3">
          {webhooks.map((w) => (
            <WebhookCard key={w.id} webhook={w} />
          ))}
        </div>
      )}
    </section>
  );
}
