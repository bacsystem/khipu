import Link from "next/link";
import { ClientesTable } from "@/components/clientes/clientes-table";
import { getServerSession } from "@/lib/session-server";
import { messages } from "@/lib/messages";

export const metadata = { title: `Clientes · ${messages.app.nombre}` };

export default async function ClientesPage() {
  const { access, empresaId } = await getServerSession();

  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        Configura primero una empresa para guardar clientes.{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          Configurar empresa
        </Link>
      </p>
    );
  }

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <ClientesTable />
    </div>
  );
}
