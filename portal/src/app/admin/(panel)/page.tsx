export const metadata = { title: "Inicio · Backoffice" };

export default function AdminInicioPage() {
  return (
    <div className="grid gap-1">
      <h1 className="font-heading text-2xl">Backoffice</h1>
      <p className="text-sm text-muted-foreground">
        El resto del panel (clientes, planes, operación) llega en los siguientes issues de la épica #11.
      </p>
    </div>
  );
}
