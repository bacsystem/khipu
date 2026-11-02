import Link from "next/link";
import { ProductosTable } from "@/components/productos/productos-table";
import { getServerSession } from "@/lib/session-server";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.nav.items.productos} · ${messages.app.nombre}` };
}

export default async function ProductosPage() {
  const { access, empresaId } = await getServerSession();
  const t = (await getMessages()).productos.pagina;

  if (!access || !empresaId) {
    return (
      <p className="text-cuerpo text-muted-foreground">
        {t.sinEmpresa}{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          {t.configurarEmpresa}
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
