"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Providers as ProvidersDelDiseno } from "khipu-design-system/providers";
import { useState } from "react";

export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => new QueryClient());
  return (
    <QueryClientProvider client={client}>
      {/* El del design system trae el tema (attribute="class" pone `.dark` en <html>), el diseño, la apariencia (paleta y tono oscuro), el movimiento, los
          iconos, los avisos (toast) y los tooltips. */}
      <ProvidersDelDiseno>{children}</ProvidersDelDiseno>
    </QueryClientProvider>
  );
}
