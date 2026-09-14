import Link from "next/link";
import { notFound } from "next/navigation";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { ReenviarButton } from "@/components/comprobantes/reenviar-button";
import { buttonVariants } from "@/components/ui/button";
import { type EstadoDocumento, obtenerFactura } from "@/lib/api/facturas";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";

function Total({ label, valor, destacado = false }: { label: string; valor: number; destacado?: boolean }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={cn("font-mono", destacado && "text-base font-medium text-foreground")}>
        {Number(valor).toFixed(2)}
      </dd>
    </div>
  );
}

const ESTADOS_CON_ALERTA: Partial<Record<EstadoDocumento, "error" | "aviso">> = {
  RECHAZADO: "error",
  INVALIDO: "error",
  ERROR_ENVIO: "error",
  ACEPTADO_CON_OBS: "aviso",
};

export default async function ComprobanteDetallePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { access, empresaId } = await getServerSession();
  if (!access || !empresaId) notFound();

  const comprobante = await obtenerFactura(access, empresaId, id).catch(() => null);
  if (!comprobante) notFound();

  const alerta = ESTADOS_CON_ALERTA[comprobante.estado_documento];

  return (
    <div className="max-w-2xl">
      <Link href="/comprobantes" className="text-sm text-muted-foreground hover:text-foreground">
        ← Comprobantes
      </Link>
      <div className="mt-2 flex items-center justify-between gap-4">
        <h1 className="font-heading text-2xl">
          {comprobante.serie}-{String(comprobante.numero).padStart(8, "0")}
        </h1>
        <EstadoBadge estado={comprobante.estado_documento} />
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        Emitido el {comprobante.fecha_emision} · {comprobante.moneda}
      </p>

      <div className="mt-8 grid gap-6 rounded-xl bg-card p-6 ring-1 ring-foreground/10">
        <div>
          <h2 className="text-sm font-medium text-muted-foreground">Totales</h2>
          <dl className="mt-2 grid grid-cols-2 gap-y-2 text-sm sm:grid-cols-4">
            <Total label="Gravado" valor={comprobante.totales.gravado} />
            <Total label="Exonerado" valor={comprobante.totales.exonerado} />
            <Total label="IGV" valor={comprobante.totales.igv} />
            <Total label="Total" valor={comprobante.totales.total} destacado />
          </dl>
        </div>

        {comprobante.cdr ? (
          <div
            className={cn(
              "rounded-lg p-3",
              alerta === "error" && "bg-destructive/5 ring-1 ring-destructive/25",
              alerta === "aviso" && "bg-warning ring-1 ring-warning-foreground/15",
            )}
          >
            <h2
              className={cn(
                "text-sm font-medium text-muted-foreground",
                alerta === "error" && "text-destructive",
                alerta === "aviso" && "text-warning-foreground",
              )}
            >
              CDR de SUNAT
            </h2>
            <p className={cn("mt-1 text-sm", alerta === "error" && "text-destructive")}>
              {comprobante.cdr.codigo} — {comprobante.cdr.descripcion}
            </p>
            {comprobante.cdr.observaciones.length > 0 ? (
              <ul className="mt-1 list-inside list-disc text-sm text-muted-foreground">
                {comprobante.cdr.observaciones.map((o) => (
                  <li key={o}>{o}</li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}

        {comprobante.ultimo_error ? (
          <div className="rounded-lg bg-destructive/5 p-3 ring-1 ring-destructive/25">
            <h2 className="text-sm font-medium text-destructive">Último intento ({comprobante.intentos})</h2>
            <p className="mt-1 text-sm text-destructive">{comprobante.ultimo_error}</p>
          </div>
        ) : null}

        <div className="flex flex-wrap gap-3">
          <a href={`/api/proxy/facturas/${comprobante.id}/xml`} className={buttonVariants({ variant: "outline", size: "sm" })}>
            Descargar XML
          </a>
          {comprobante.cdr ? (
            <a href={`/api/proxy/facturas/${comprobante.id}/cdr`} className={buttonVariants({ variant: "outline", size: "sm" })}>
              Descargar CDR
            </a>
          ) : null}
          {comprobante.estado_documento === "ERROR_ENVIO" ? <ReenviarButton id={comprobante.id} /> : null}
        </div>
      </div>
    </div>
  );
}
