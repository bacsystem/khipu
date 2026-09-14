import { CrearApiKey } from "@/components/api-keys/crear-api-key";

export default function ApiKeysPage() {
  return (
    <div>
      <h1 className="font-heading text-2xl">API keys</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Llaves para integrar tus sistemas con la API de facturación.
      </p>

      <div className="mt-6 max-w-2xl rounded-lg border border-border p-6">
        <CrearApiKey />
      </div>
    </div>
  );
}
