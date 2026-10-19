import { WebhookIcon } from "lucide-react";
import Link from "next/link";
import type { Webhook } from "@/lib/api/webhooks";
import { NuevoWebhookDialog } from "./nuevo-webhook-dialog";
import { WebhookCard } from "./webhook-card";

/**
 * Los webhooks de la empresa (#19): en vez de consultar cada comprobante, khipu avisa con un POST firmado a la URL del cliente cuando cambia de estado. `webhooks` es nulo si
 * no se pudieron leer: la página de llaves sigue funcionando y la sección lo dice.
 */
export function WebhooksSection({ webhooks }: { webhooks: Webhook[] | null }) {
  return (
    <section aria-labelledby="webhooks-titulo" className="grid min-w-0 grid-cols-1 gap-4" data-testid="webhooks">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
            <WebhookIcon className="size-4" />
          </div>
          <div className="min-w-0">
            <h2 id="webhooks-titulo" className="text-[13px] font-semibold text-foreground">
              Webhooks
            </h2>
            <p className="text-[12px] text-muted-foreground">
              Recibe un aviso firmado en tu servidor cuando un comprobante cambia de estado, sin consultar la API.{" "}
              <Link href="/developers/guia#webhooks" className="text-primary hover:underline">
                Cómo verificar la firma
              </Link>
            </p>
          </div>
        </div>
        {webhooks ? <NuevoWebhookDialog /> : null}
      </div>

      {webhooks === null ? (
        <p role="alert" className="rounded-xl border border-border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          No se pudieron leer los webhooks. Recarga la página para intentarlo de nuevo.
        </p>
      ) : webhooks.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-xl border border-border bg-card px-4 py-10 text-center text-muted-foreground">
          <WebhookIcon className="size-6" />
          <p className="text-sm">Todavía no tienes webhooks. Registra la URL de tu sistema con «Nuevo webhook».</p>
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
