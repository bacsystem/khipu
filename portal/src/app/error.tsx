"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import { messages } from "@/lib/messages";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
      <h1 className="font-heading text-2xl">Algo salió mal</h1>
      <p className="max-w-sm text-sm text-muted-foreground">{messages.errores.generico}</p>
      <div className="flex gap-3">
        <Button onClick={reset}>Reintentar</Button>
        <Button render={<Link href="/" />} nativeButton={false} variant="outline">
          Ir al inicio
        </Button>
      </div>
    </div>
  );
}
