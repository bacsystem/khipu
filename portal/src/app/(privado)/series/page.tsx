import { NuevaSerieForm } from "@/components/series/nueva-serie-form";
import { Badge } from "@/components/ui/badge";
import { listarSeries } from "@/lib/api/series";
import { getServerSession } from "@/lib/session-server";

const NOMBRES_TIPO: Record<string, string> = {
  "01": "Factura",
  "03": "Boleta",
  "07": "Nota de crédito",
  "08": "Nota de débito",
};

export default async function SeriesPage() {
  const { access, empresaId } = await getServerSession();

  if (!access || !empresaId) {
    return (
      <div>
        <h1 className="font-heading text-2xl">Series</h1>
        <p className="mt-4 text-sm text-muted-foreground">Configura primero una empresa para crear series.</p>
      </div>
    );
  }

  const series = await listarSeries(access, empresaId);

  return (
    <div>
      <h1 className="font-heading text-2xl">Series</h1>
      <p className="mt-1 text-sm text-muted-foreground">Series de facturación para tus comprobantes.</p>

      <div className="mt-6 overflow-x-auto rounded-lg border border-border">
        <table className="w-full text-sm">
          <thead className="bg-muted text-left text-muted-foreground">
            <tr>
              <th className="px-4 py-2 font-medium">Tipo</th>
              <th className="px-4 py-2 font-medium">Serie</th>
              <th className="px-4 py-2 font-medium">Último número</th>
              <th className="px-4 py-2 font-medium">Estado</th>
            </tr>
          </thead>
          <tbody>
            {series.map((s) => (
              <tr key={`${s.tipo}-${s.serie}`} className="border-t border-border">
                <td className="px-4 py-2">{NOMBRES_TIPO[s.tipo] ?? s.tipo}</td>
                <td className="px-4 py-2 font-mono">{s.serie}</td>
                <td className="px-4 py-2 font-mono">{s.ultimo_numero}</td>
                <td className="px-4 py-2">
                  <Badge variant={s.activa ? "default" : "outline"}>{s.activa ? "Activa" : "Inactiva"}</Badge>
                </td>
              </tr>
            ))}
            {series.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-muted-foreground">
                  Todavía no tienes series.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <div className="mt-8 max-w-2xl rounded-lg border border-border p-6">
        <h2 className="text-sm font-medium text-muted-foreground">Nueva serie</h2>
        <div className="mt-4">
          <NuevaSerieForm />
        </div>
      </div>
    </div>
  );
}
