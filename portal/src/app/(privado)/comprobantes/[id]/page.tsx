import Link from "next/link";
import { notFound } from "next/navigation";
import { EstadoBadge } from "@/components/comprobantes/estado-badge";
import { ReenviarButton } from "@/components/comprobantes/reenviar-button";
import { buttonVariants } from "@/components/ui/button";
import { obtenerFactura } from "@/lib/api/facturas";
import { getServerSession } from "@/lib/session-server";

function Total({ label, valor }: { label: string; valor: number }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-mono">{Number(valor).toFixed(2)}</dd>
    </div>
  );
}

export default async function ComprobanteDetallePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { access, empresaId } = await getServerSession();
  if (!access || !empresaId) notFound();

  const comprobante = await obtenerFactura(access, empresaId, id).catch(() => null);
  if (!comprobante) notFound();

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

      <div className="mt-8 grid gap-6 rounded-lg border border-border p-6">
        <div>
          <h2 className="text-sm font-medium text-muted-foreground">Totales</h2>
          <dl className="mt-2 grid grid-cols-2 gap-y-2 text-sm sm:grid-cols-4">
            <Total label="Gravado" valor={comprobante.totales.gravado} />
            <Total label="Exonerado" valor={comprobante.totales.exonerado} />
            <Total label="IGV" valor={comprobante.totales.igv} />
            <Total label="Total" valor={comprobante.totales.total} />
          </dl>
        </div>

        {comprobante.cdr ? (
          <div>
            <h2 className="text-sm font-medium text-muted-foreground">CDR de SUNAT</h2>
            <p className="mt-1 text-sm">
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
          <div>
            <h2 className="text-sm font-medium text-muted-foreground">Último intento ({comprobante.intentos})</h2>
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
