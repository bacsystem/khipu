import Link from "next/link";
import { ComprobantesTable } from "@/components/comprobantes/comprobantes-table";
import { listarFacturas } from "@/lib/api/facturas";
import { getServerSession } from "@/lib/session-server";

export default async function ComprobantesPage({
  searchParams,
}: {
  searchParams: Promise<{ pagina?: string }>;
}) {
  const { pagina } = await searchParams;
  const { access, empresaId } = await getServerSession();
  const paginaNum = Number(pagina ?? 1) || 1;

  if (!access || !empresaId) {
    return (
      <div>
        <h1 className="font-heading text-2xl">Comprobantes</h1>
        <p className="mt-4 text-sm text-muted-foreground">
          Primero registra una empresa para ver tus comprobantes.{" "}
          <Link href="/onboarding" className="text-primary hover:underline">
            Configurar empresa
          </Link>
        </p>
      </div>
    );
  }

  const comprobantes = await listarFacturas(access, empresaId, { pagina: paginaNum });

  return (
    <div>
      <h1 className="font-heading text-2xl">Comprobantes</h1>
      <p className="mt-1 text-sm text-muted-foreground">Facturas, boletas y notas emitidas.</p>
      <div className="mt-6">
        <ComprobantesTable inicial={comprobantes} pagina={paginaNum} />
      </div>
    </div>
  );
}
