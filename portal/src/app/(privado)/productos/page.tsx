import Link from "next/link";
import { ProductosTable } from "@/components/productos/productos-table";
import { getServerSession } from "@/lib/session-server";
import { messages } from "@/lib/messages";

export const metadata = { title: `Productos · ${messages.app.nombre}` };

export default async function ProductosPage() {
  const { access, empresaId } = await getServerSession();

  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        Configura primero una empresa para guardar productos.{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          Configurar empresa
        </Link>
      </p>
    );
  }

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <ProductosTable />
    </div>
  );
}
