import { ProductosTable } from "@/components/productos/productos-table";
import { getServerSession } from "@/lib/session-server";
import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";
import { SinEmpresa } from "@/components/compartidos/sin-empresa";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.nav.items.productos} · ${messages.app.nombre}` };
}

export default async function ProductosPage() {
  const { access, empresaId } = await getServerSession();
  const t = (await getMessages()).productos.pagina;

  if (!access || !empresaId) {
    return (
      <SinEmpresa texto={t.sinEmpresa} accion={t.configurarEmpresa} />
    );
  }

  return (
    <div className={CONTENEDOR_PAGINA}>
      <ProductosTable />
    </div>
  );
}
