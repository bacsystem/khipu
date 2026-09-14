"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";

export function ReenviarButton({ id }: { id: string }) {
  const router = useRouter();
  const [enviando, setEnviando] = useState(false);

  async function reenviar() {
    setEnviando(true);
    await fetch(`/api/proxy/facturas/${id}/enviar`, { method: "POST" });
    setEnviando(false);
    router.refresh();
  }

  return (
    <Button size="sm" disabled={enviando} onClick={reenviar}>
      {enviando ? "Reenviando…" : "Reenviar"}
    </Button>
  );
}
