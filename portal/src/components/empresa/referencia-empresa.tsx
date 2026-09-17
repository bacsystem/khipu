"use client";

import { DatabaseIcon, LockIcon, ShieldCheckIcon } from "lucide-react";
import { type CampoReferencia, ReferenciaTecnicaDialog, SeccionReferencia, TablaCampos } from "@/components/ui/referencia-tecnica";

const CAMPOS: CampoReferencia[] = [
  { campo: "id", tipo: "uuid", descripcion: "Identificador del tenant; viaja en la cabecera X-Empresa", clave: true },
  { campo: "ruc", tipo: "varchar(11)", descripcion: "Único en la plataforma; debe coincidir con el OU del certificado" },
  { campo: "razon_social", tipo: "varchar", descripcion: "Nombre legal tal como figura en la ficha RUC" },
  { campo: "entorno", tipo: "BETA | PRODUCCION", descripcion: "Endpoint SUNAT al que se envían los comprobantes" },
  { campo: "sol_usuario / sol_clave", tipo: "cifrado", descripcion: "Usuario secundario y clave SOL; se envían como RUC+usuario en el UsernameToken" },
  { campo: "certificado_pkcs12 / clave", tipo: "cifrado", descripcion: "Archivo .p12 y su contraseña, para firmar XML-DSig" },
  { campo: "certificado_vigencia_hasta", tipo: "date", descripcion: "notAfter del certificado, en hora de Lima" },
];

const REGLAS = [
  {
    titulo: "RUC en el certificado",
    texto:
      "Al cargar el .p12 se abre con la contraseña, se exige clave privada y se verifica que el RUC de la empresa figure en el campo OU del sujeto. Si no, se rechaza (CERTIFICADO_INVALIDO).",
  },
  {
    titulo: "Usuario SOL secundario",
    texto:
      "SUNAT exige un usuario secundario con permiso de emisión. El portal guarda solo el usuario; al enviar, el sistema antepone el RUC (RUC+USUARIO) en el UsernameToken del SOAP.",
  },
  {
    titulo: "Cifrado en reposo",
    texto:
      "Credenciales SOL, PKCS#12 y su contraseña se guardan cifrados con la clave maestra del servicio (MASTER_KEY). Nunca vuelven en claro por la API: por eso el portal no puede mostrarlos, solo reemplazarlos.",
  },
  {
    titulo: "Entorno",
    texto: "BETA apunta a la homologación de SUNAT (sin validez tributaria); Producción, al servicio real. Cada empresa fija el suyo al registrarse.",
  },
];

export function ReferenciaEmpresaDialog({ className }: { className?: string }) {
  return (
    <ReferenciaTecnicaDialog
      titulo="Referencia técnica de empresa"
      descripcion="Modelo de datos del tenant y reglas de validación de certificado y credenciales SOL"
      className={className}
    >
      <SeccionReferencia icono={DatabaseIcon} titulo={<>Entidad &lsquo;tenant&rsquo; (empresa)</>} subtitulo="una fila por razón social · pertenece a una cuenta">
        <TablaCampos
          campos={CAMPOS}
          nota={
            <span className="inline-flex items-center gap-1">
              <LockIcon className="size-3" /> Secretos cifrados con MASTER_KEY; nunca se exponen por la API
            </span>
          }
        />
      </SeccionReferencia>

      <SeccionReferencia icono={ShieldCheckIcon} titulo="Reglas de validación" subtitulo="lo que el sistema comprueba al guardar">
        <div className="grid grid-cols-1 gap-2.5 p-4">
          {REGLAS.map((r) => (
            <div key={r.titulo} className="rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5">
              <p className="text-[13px] font-medium text-foreground">{r.titulo}</p>
              <p className="mt-0.5 text-[12px] leading-relaxed text-muted-foreground">{r.texto}</p>
            </div>
          ))}
        </div>
      </SeccionReferencia>
    </ReferenciaTecnicaDialog>
  );
}
