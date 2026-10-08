"use client";

import { InboxIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AvisarAlCliente } from "@/components/admin/avisar-al-cliente";
import { Etiqueta, type Tono } from "@/components/admin/etiquetas";
import { PieTabla } from "@/components/ui/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { hrefDetalleEmpresa } from "@/lib/api/admin-empresa-detalle";
import {
  hrefAvisos,
  VISTAS_DE_AVISOS,
  type CertificadoEnRiesgo,
  type CuentaDelAviso,
  type MotivoDeAviso,
  type PaginaDeAvisos,
  type ParamsAvisos,
  type SolFallando,
  type TipoDeAviso,
  type UltimoAviso,
} from "@/lib/api/admin-avisos";
import { atascadosEnPalabras, certificadoEnPalabras, ultimoAvisoEnPalabras } from "@/lib/avisos-formato";
import { CABECERA_TABLA, SEGMENTADO, SEGMENTO } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.avisos;

/** Lo que las dos listas tienen en común, para dibujarlas con la misma tabla. `situacion` es lo propio de cada una. */
type Fila = {
  empresaId: string;
  ruc: string;
  razonSocial: string;
  cuenta?: CuentaDelAviso;
  tipo: TipoDeAviso;
  motivo: MotivoDeAviso;
  situacion: React.ReactNode;
  ultimoAviso?: UltimoAviso;
  avisarDesde?: string;
  puedeAvisar: boolean;
};

const TONO_DEL_CERTIFICADO: Record<CertificadoEnRiesgo["motivo"], Tono> = { CERTIFICADO_VENCIDO: "error", CERTIFICADO_POR_VENCER: "aviso" };

function deCertificado(c: CertificadoEnRiesgo): Fila {
  return {
    empresaId: c.empresa_id,
    ruc: c.ruc,
    razonSocial: c.razon_social,
    cuenta: c.cuenta,
    tipo: "CERTIFICADO",
    motivo: c.motivo,
    situacion: (
      <div className="grid justify-items-start gap-1">
        <Etiqueta tono={TONO_DEL_CERTIFICADO[c.motivo]}>{t.motivos[c.motivo]}</Etiqueta>
        <span className="text-[12px] text-muted-foreground">{certificadoEnPalabras(c)}</span>
      </div>
    ),
    ultimoAviso: c.ultimo_aviso,
    avisarDesde: c.avisar_desde,
    puedeAvisar: c.puede_avisar,
  };
}

function deSol(s: SolFallando): Fila {
  return {
    empresaId: s.empresa_id,
    ruc: s.ruc,
    razonSocial: s.razon_social,
    cuenta: s.cuenta,
    tipo: "CREDENCIALES_SOL",
    motivo: "CREDENCIALES_SOL_INVALIDAS",
    situacion: (
      <div className="grid justify-items-start gap-1">
        <Etiqueta tono="error">{atascadosEnPalabras(s.comprobantes_afectados)}</Etiqueta>
        <span className="text-[12px] text-muted-foreground">{t.sol.ultimoFallo.replace("{fecha}", formatearFechaHora(s.ultimo_fallo))}</span>
        <span className="font-mono text-[11px] break-words text-muted-foreground" data-testid="avisos-sunat">
          {t.sol.dijoSunat.replace("{error}", s.ultimo_error)}
        </span>
      </div>
    ),
    ultimoAviso: s.ultimo_aviso,
    avisarDesde: s.avisar_desde,
    puedeAvisar: s.puede_avisar,
  };
}

/**
 * Las listas de avisos del backoffice (#197), paginadas en el servidor: cambiar de lista o de página solo cambia la URL y el Server Component de la página vuelve a renderizar.
 * Cada fila dice qué pasa, cuándo se avisó por última vez y qué se puede hacer ahora: avisar (hay a quién y no se avisó lo mismo en la última semana), esperar (y desde cuándo)
 * o nada (no hay cuenta). Lo que pasó con el último aviso queda arriba y sobrevive a la recarga que sigue.
 */
export function AvisosTabla({ params, certificados, sol }: { params: ParamsAvisos; certificados: PaginaDeAvisos<CertificadoEnRiesgo> | null; sol: PaginaDeAvisos<SolFallando> | null }) {
  const router = useRouter();
  const [resultado, setResultado] = useState<string | null>(null);

  const filas: Fila[] = params.vista === "CERTIFICADOS" ? (certificados?.filas ?? []).map(deCertificado) : (sol?.filas ?? []).map(deSol);
  const total = params.vista === "CERTIFICADOS" ? (certificados?.total ?? 0) : (sol?.total ?? 0);
  const ultimaPagina = Math.max(1, Math.ceil(total / params.porPagina));
  const primero = total === 0 ? 0 : (params.pagina - 1) * params.porPagina + 1;
  const ultimo = (params.pagina - 1) * params.porPagina + filas.length;

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <nav aria-label={t.vistas} className={SEGMENTADO}>
        {VISTAS_DE_AVISOS.map((v) => (
          <Link
            key={v}
            href={hrefAvisos({ ...params, vista: v, pagina: 1 })}
            aria-current={params.vista === v ? "page" : undefined}
            className={SEGMENTO}
          >
            {t.filtros[v]}
          </Link>
        ))}
      </nav>

      <p className="text-[12px] text-muted-foreground">{t.ayuda[params.vista]}</p>

      {resultado ? (
        <p data-testid="avisos-resultado" role="status" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[13px]">
          {resultado}
        </p>
      ) : null}

      <div className="overflow-x-auto rounded-xl border border-border/90 bg-card shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.empresa}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.situacion}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.ultimoAviso}</TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.accion}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {filas.map((f) => (
              <TableRow key={`${f.empresaId}-${f.motivo}`} data-testid="avisos-fila" data-empresa={f.ruc} data-motivo={f.motivo} className="border-b border-border/60 align-top hover:bg-muted/80">
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <Link href={hrefDetalleEmpresa(f.empresaId)} className="font-medium text-foreground hover:text-primary hover:underline">
                      {f.razonSocial}
                    </Link>
                    <span className="font-mono text-[11px] text-muted-foreground">{f.ruc}</span>
                    {f.cuenta ? (
                      <span className="text-[11px] text-muted-foreground">
                        {f.cuenta.nombre} · {f.cuenta.email}
                      </span>
                    ) : null}
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">{f.situacion}</TableCell>
                <TableCell className="px-3 py-2 text-[12px] text-muted-foreground" data-testid="avisos-ultimo">
                  {ultimoAvisoEnPalabras(f.ultimoAviso)}
                </TableCell>
                <TableCell className="py-2 pr-4 pl-3">
                  {!f.cuenta ? (
                    <span data-testid="avisos-sin-cuenta" className="text-[11px] text-muted-foreground">
                      {t.sinCuenta}
                    </span>
                  ) : f.puedeAvisar ? (
                    <AvisarAlCliente empresaId={f.empresaId} razonSocial={f.razonSocial} correo={f.cuenta.email} tipo={f.tipo} motivo={f.motivo} alResultado={setResultado} />
                  ) : (
                    <span data-testid="avisos-espera" className="text-[11px] text-muted-foreground">
                      {f.avisarDesde ? t.puedeRepetirDesde.replace("{fecha}", formatearFechaHora(f.avisarDesde)) : null}
                    </span>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {filas.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={4} className="py-14 text-center">
                  <div data-testid="avisos-vacio" className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">{t.vacio[params.vista]}</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>

        <PieTabla
          desde={primero}
          hasta={ultimo}
          total={total}
          unidad="empresas"
          porPagina={params.porPagina}
          onPorPagina={(n) => router.push(hrefAvisos({ ...params, pagina: 1, porPagina: n }))}
          pagina={params.pagina}
          ultimaPagina={ultimaPagina}
          onPagina={(p) => router.push(hrefAvisos({ ...params, pagina: p }))}
          hrefPagina={(p) => hrefAvisos({ ...params, pagina: p })}
        />
      </div>
    </div>
  );
}
