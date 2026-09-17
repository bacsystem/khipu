"use client";

import { BookOpenIcon, DatabaseIcon, FingerprintIcon, LockIcon, ShieldCheckIcon } from "lucide-react";
import Link from "next/link";
import {
  type CampoReferencia,
  NotaReferencia,
  ReferenciaTecnicaDialog,
  ReglaReferencia,
  SeccionReferencia,
  TablaCampos,
} from "@/components/ui/referencia-tecnica";

const CAMPOS: CampoReferencia[] = [
  { campo: "id", tipo: "uuid", descripcion: "Identificador de la llave (el que usa el endpoint de revocación)", clave: true },
  { campo: "tenant_id", tipo: "uuid", descripcion: "Empresa emisora dueña de la llave" },
  { campo: "key_hash", tipo: "varchar(64)", descripcion: "SHA-256 de la llave + pepper del servidor; nunca sale por la API" },
  { campo: "prefijo", tipo: "varchar(10)", descripcion: "Primeros 10 caracteres, para reconocerla en listados" },
  { campo: "activa", tipo: "boolean", descripcion: "false una vez revocada; la API responde 401 con esa llave" },
  { campo: "created_at", tipo: "timestamptz", descripcion: "Momento de creación" },
  { campo: "revoked_at", tipo: "timestamptz", descripcion: "Momento de revocación, nulo mientras esté activa" },
];

const REGLAS: Array<{ titulo: string; detalle: string; codigo: string }> = [
  { titulo: "Header de autenticación", detalle: "Una llave por request, sin esquema Bearer", codigo: "X-Api-Key" },
  { titulo: "Formato de la llave", detalle: "Prefijo fk_ + 40 caracteres aleatorios (base64url)", codigo: "fk_…" },
  { titulo: "Alcance", detalle: "Cada llave pertenece a una sola empresa (RUC); no hace falta X-Empresa", codigo: "tenant" },
  { titulo: "Gestión solo desde el portal", detalle: "Crear, listar y revocar llaves exige sesión; con X-Api-Key responde 403", codigo: "sesión" },
  { titulo: "Revocación", detalle: "Irreversible: la llave deja de autenticar al instante, sin período de gracia", codigo: "DELETE" },
];

/** Modelo de datos de la entidad 'api_key' y reglas de autenticación, como referencia en un modal. */
export function ReferenciaApiKeysDialog({ className }: { className?: string }) {
  return (
    <ReferenciaTecnicaDialog
      titulo="Referencia técnica de API keys"
      descripcion={<>Modelo de datos de la entidad &lsquo;api_key&rsquo; y reglas de autenticación</>}
      className={className}
      pie={
        <Link href="/developers" target="_blank" className="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline">
          <BookOpenIcon className="size-3.5" />
          Documentación completa de la API
        </Link>
      }
    >
      <SeccionReferencia icono={DatabaseIcon} titulo={<>Entidad &lsquo;api_key&rsquo;</>} subtitulo="tabla api_key · varias llaves por empresa">
        <TablaCampos
          campos={CAMPOS}
          nota={
            <span className="inline-flex items-center gap-1">
              <LockIcon className="size-3" /> key_hash único · la llave en claro no se almacena
            </span>
          }
        />
      </SeccionReferencia>

      <SeccionReferencia icono={ShieldCheckIcon} titulo="Reglas de autenticación" subtitulo="rutas /v1/facturas, /v1/series y /v1/empresa">
        <div className="grid grid-cols-1 gap-2.5 p-4">
          {REGLAS.map((r) => (
            <ReglaReferencia key={r.titulo} insignia={<FingerprintIcon className="size-4" />} titulo={r.titulo} detalle={r.detalle} valor={r.codigo} />
          ))}
        </div>
        <NotaReferencia>
          El portal no usa API keys: entra con tu sesión (JWT) y el header <strong className="font-medium text-foreground">X-Empresa</strong>. Las llaves
          son para sistemas externos que emiten por integración; una llave comprometida se revoca aquí y se reemplaza por una nueva.
        </NotaReferencia>
      </SeccionReferencia>
    </ReferenciaTecnicaDialog>
  );
}
