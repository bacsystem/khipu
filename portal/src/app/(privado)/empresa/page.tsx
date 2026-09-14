import { CertificadoForm } from "@/components/empresa/certificado-form";
import { CredencialesSolForm } from "@/components/empresa/credenciales-sol-form";
import { NuevaEmpresaForm } from "@/components/empresa/nueva-empresa-form";
import { Badge } from "@/components/ui/badge";
import { obtenerEmpresaActual } from "@/lib/api/empresas";
import { getServerSession } from "@/lib/session-server";

export default async function EmpresaPage() {
  const { access, empresaId } = await getServerSession();

  if (!access || !empresaId) {
    return (
      <div>
        <h1 className="font-heading text-2xl">Empresa</h1>
        <p className="mt-4 text-sm text-muted-foreground">Aún no tienes una empresa configurada.</p>
        <div className="mt-6 max-w-2xl rounded-lg border border-border p-6">
          <h2 className="text-sm font-medium text-muted-foreground">Agregar empresa</h2>
          <div className="mt-3">
            <NuevaEmpresaForm />
          </div>
        </div>
      </div>
    );
  }

  const empresa = await obtenerEmpresaActual(access, empresaId);

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between gap-4">
        <h1 className="font-heading text-2xl">Empresa</h1>
        {empresa.entorno === "PRODUCCION" ? (
          <Badge className="border-transparent bg-warning text-warning-foreground">Producción</Badge>
        ) : (
          <Badge variant="outline">Beta</Badge>
        )}
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        {empresa.razon_social} · RUC {empresa.ruc}
      </p>

      <div className="mt-8 grid gap-6">
        <section className="rounded-lg border border-border p-6">
          <h2 className="text-sm font-medium text-muted-foreground">Certificado digital</h2>
          <p className="mt-1 text-sm">
            {empresa.certificado_vigencia_hasta
              ? `Vigente hasta ${empresa.certificado_vigencia_hasta}`
              : "Sin certificado cargado."}
          </p>
          <div className="mt-4">
            <CertificadoForm />
          </div>
        </section>

        <section className="rounded-lg border border-border p-6">
          <h2 className="text-sm font-medium text-muted-foreground">Credenciales SOL</h2>
          <p className="mt-1 text-sm">
            {empresa.tiene_credenciales_sol ? "Configuradas." : "Aún no configuradas."}
          </p>
          <div className="mt-4">
            <CredencialesSolForm />
          </div>
        </section>

        <section className="rounded-lg border border-border p-6">
          <h2 className="text-sm font-medium text-muted-foreground">Agregar otra empresa</h2>
          <div className="mt-4">
            <NuevaEmpresaForm />
          </div>
        </section>
      </div>
    </div>
  );
}
