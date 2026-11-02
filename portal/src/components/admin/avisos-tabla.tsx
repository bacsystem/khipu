"use client";

import { TrayIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Locale } from "@/i18n/config";
import type { Mensajes } from "@/i18n/mensajes";
import { AvisarAlCliente } from "@/components/admin/avisar-al-cliente";
import { Etiqueta, type Tono } from "@/components/admin/etiquetas";
import { CONTENEDOR_TABLA, FilaVacia, PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
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

function deCertificado(c: CertificadoEnRiesgo, messages: Mensajes, locale: Locale): Fila {
  const t = messages.admin.avisos;
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
        <span className="text-control text-muted-foreground">{certificadoEnPalabras(c, messages, locale)}</span>
      </div>
    ),
    ultimoAviso: c.ultimo_aviso,
    avisarDesde: c.avisar_desde,
    puedeAvisar: c.puede_avisar,
  };
}

function deSol(s: SolFallando, messages: Mensajes, locale: Locale): Fila {
  const t = messages.admin.avisos;
  return {
    empresaId: s.empresa_id,
    ruc: s.ruc,
    razonSocial: s.razon_social,
    cuenta: s.cuenta,
    tipo: "CREDENCIALES_SOL",
    motivo: "CREDENCIALES_SOL_INVALIDAS",
    situacion: (
      <div className="grid justify-items-start gap-1">
        <Etiqueta tono="error">{atascadosEnPalabras(s.comprobantes_afectados, messages)}</Etiqueta>
        <span className="text-control text-muted-foreground">{t.sol.ultimoFallo.replace("{fecha}", formatearFechaHora(s.ultimo_fallo, locale))}</span>
        <span className="font-mono text-etiqueta break-words text-muted-foreground" data-testid="avisos-sunat">
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
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.admin.avisos;
  const router = useRouter();
  const [resultado, setResultado] = useState<string | null>(null);

  const filas: Fila[] = params.vista === "CERTIFICADOS" ? (certificados?.filas ?? []).map((c) => deCertificado(c, messages, locale)) : (sol?.filas ?? []).map((s) => deSol(s, messages, locale));
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

      <p className="text-control text-muted-foreground">{t.ayuda[params.vista]}</p>

      {resultado ? (
        <p data-testid="avisos-resultado" role="status" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-control">
          {resultado}
        </p>
      ) : null}

      <div className={CONTENEDOR_TABLA}>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.empresa}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.situacion}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.ultimoAviso}</TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.accion}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {filas.map((f) => (
              <TableRow key={`${f.empresaId}-${f.motivo}`} data-testid="avisos-fila" data-empresa={f.ruc} data-motivo={f.motivo} className="align-top">
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <Link href={hrefDetalleEmpresa(f.empresaId)} className="font-medium text-foreground hover:text-primary hover:underline">
                      {f.razonSocial}
                    </Link>
                    <span className="font-mono text-etiqueta text-muted-foreground">{f.ruc}</span>
                    {f.cuenta ? (
                      <span className="text-etiqueta text-muted-foreground">
                        {f.cuenta.nombre} · {f.cuenta.email}
                      </span>
                    ) : null}
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">{f.situacion}</TableCell>
                <TableCell className="px-3 py-2 text-control text-muted-foreground" data-testid="avisos-ultimo">
                  {ultimoAvisoEnPalabras(f.ultimoAviso, messages, locale)}
                </TableCell>
                <TableCell className="py-2 pr-4 pl-3">
                  {!f.cuenta ? (
                    <span data-testid="avisos-sin-cuenta" className="text-etiqueta text-muted-foreground">
                      {t.sinCuenta}
                    </span>
                  ) : f.puedeAvisar ? (
                    <AvisarAlCliente empresaId={f.empresaId} razonSocial={f.razonSocial} correo={f.cuenta.email} tipo={f.tipo} motivo={f.motivo} alResultado={setResultado} />
                  ) : (
                    <span data-testid="avisos-espera" className="text-etiqueta text-muted-foreground">
                      {f.avisarDesde ? t.puedeRepetirDesde.replace("{fecha}", formatearFechaHora(f.avisarDesde, locale)) : null}
                    </span>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {filas.length === 0 ? (
              <FilaVacia columnas={4} icon={<TrayIcon className="size-6" />}>
                <span data-testid="avisos-vacio">{t.vacio[params.vista]}</span>
              </FilaVacia>
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
