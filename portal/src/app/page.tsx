import { Button } from "@/components/ui/button";

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-8">
      <h1 className="text-2xl font-semibold">Portal de facturación electrónica</h1>
      <p className="text-muted-foreground">
        Autoservicio en construcción (etapa 1).
      </p>
      <Button render={<a href="/login" />} nativeButton={false}>
        Iniciar sesión
      </Button>
    </div>
  );
}
