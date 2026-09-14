export function PaginaEnConstruccion({ titulo, descripcion }: { titulo: string; descripcion: string }) {
  return (
    <div>
      <h1 className="font-heading text-2xl">{titulo}</h1>
      <p className="mt-1 text-sm text-muted-foreground">{descripcion}</p>
      <div className="mt-8 rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
        Esta sección está en construcción.
      </div>
    </div>
  );
}
