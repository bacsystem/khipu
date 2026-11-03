import { WebhooksLogoIcon } from "@phosphor-icons/react/dist/ssr";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import Link from "next/link";
import { useMessages } from "next-intl";
import type { Webhook } from "@/lib/api/webhooks";
import { NuevoWebhookDialog } from "./nuevo-webhook-dialog";
import { WebhookCard } from "./webhook-card";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { EstadoVacio } from "khipu-design-system/feedback/estado-vacio";

/**
 * Los webhooks de la empresa (#19): en vez de consultar cada comprobante, khipu avisa con un POST firmado a la URL del cliente cuando cambia de estado. `webhooks` es nulo si
 * no se pudieron leer: la página de llaves sigue funcionando y la sección lo dice.
 */
export function WebhooksSection({ webhooks }: { webhooks: Webhook[] | null }) {
  const t = useMessages().webhooks.seccion;
  return (
    <section aria-labelledby="webhooks-titulo" className="grid min-w-0 grid-cols-1 gap-4" data-testid="webhooks">
      <div className="grid gap-1">
        <CabeceraSeccion
          icon={WebhooksLogoIcon}
          titulo={<span id="webhooks-titulo">{t.titulo}</span>}
          conBorde={false}
          className="p-0"
          derecha={webhooks ? <NuevoWebhookDialog /> : null}
        />
        <p className="text-control text-muted-foreground">
          {t.descripcion}{" "}
          <Link href="/developers/guia#webhooks" className="text-primary hover:underline">
            {t.comoVerificar}
          </Link>
        </p>
      </div>

      {webhooks === null ? (
        <Alerta tono="error">{t.errorLectura}</Alerta>
      ) : webhooks.length === 0 ? (
        <EstadoVacio icon={WebhooksLogoIcon} titulo={t.vacia} />
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
