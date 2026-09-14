const PASOS = [
  { titulo: "Valida los datos", detalle: "reglas de negocio" },
  { titulo: "Genera el XML UBL", detalle: "UBL 2.1" },
  { titulo: "Envía a SUNAT", detalle: ".zip" },
  { titulo: "Recibe respuesta", detalle: null },
  { titulo: "Entrega los archivos", detalle: "XML · CDR · PDF" },
];

const BOX_H = 56;
const GAP = 18;
const UNIT = BOX_H + GAP;
const ICON_CX = 28;
const ICON_R = 16;
const SVG_W = 320;
const SVG_H = PASOS.length * BOX_H + (PASOS.length - 1) * GAP;

const ICON_PATHS: Record<number, string[]> = {
  0: [
    "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
    "m9 12 2 2 4-4",
  ],
  1: [
    "M4 12.15V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.706.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2h-3.35",
    "M14 2v5a1 1 0 0 0 1 1h5",
    "m5 16-3 3 3 3",
    "m9 22 3-3-3-3",
  ],
  2: ["M12 3v12", "m17 8-5-5-5 5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"],
  3: ["m16 9-5.5 5.5L8 12"],
  4: [
    "M12 22V12",
    "m16 17 2 2 4-4",
    "M21 11.127V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.729l7 4a2 2 0 0 0 2 .001l1.32-.753",
    "M3.29 7 12 12l8.71-5",
    "m7.5 4.27 8.997 5.148",
  ],
};

export function ComprobantePreview() {
  return (
    <div className="relative mx-auto w-full max-w-sm">
      <div
        aria-hidden
        className="h-3 w-full rounded-t-xl bg-[radial-gradient(circle_at_center,var(--sidebar)_2.5px,transparent_2.6px)] [background-size:14px_14px] [background-position:7px_0]"
      />
      <div className="rounded-b-xl bg-sidebar px-6 py-6 text-sidebar-foreground shadow-xl">
        <p className="text-xs text-sidebar-foreground/60">Procesamiento automático con la API</p>

        <svg
          role="img"
          aria-label="Flujo: valida los datos, genera el XML UBL, envía a SUNAT, recibe respuesta, entrega los archivos"
          viewBox={`0 0 ${SVG_W} ${SVG_H}`}
          className="mt-5 w-full"
        >
          {PASOS.slice(0, -1).map((_, i) => {
            const y1 = i * UNIT + BOX_H / 2 + ICON_R;
            const y2 = (i + 1) * UNIT + BOX_H / 2 - ICON_R;
            return (
              <g key={`linea-${i}`}>
                <line x1={ICON_CX} y1={y1} x2={ICON_CX} y2={y2 - 5} stroke="var(--sidebar-border)" strokeWidth={1} />
                <path d={`M${ICON_CX - 3.5} ${y2 - 5} L${ICON_CX + 3.5} ${y2 - 5} L${ICON_CX} ${y2} Z`} fill="var(--sidebar-border)" />
              </g>
            );
          })}

          {PASOS.map((paso, i) => {
            const boxY = i * UNIT;
            const cy = boxY + BOX_H / 2;
            const destacado = paso.detalle === null;
            return (
              <g key={paso.titulo}>
                <rect x={0} y={boxY} width={SVG_W} height={BOX_H} rx={10} fill="var(--sidebar-accent)" />
                <circle cx={ICON_CX} cy={cy} r={ICON_R} fill={destacado ? "var(--success)" : "rgba(192,138,46,0.16)"} />
                <g
                  transform={`translate(${ICON_CX - 9} ${cy - 9})`}
                  fill="none"
                  stroke={destacado ? "var(--success-foreground)" : "var(--sidebar-primary)"}
                  strokeWidth={2}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <svg width={18} height={18} viewBox="0 0 24 24">
                    {i === 3 ? <circle cx={12} cy={12} r={10} /> : null}
                    {ICON_PATHS[i].map((d) => (
                      <path key={d} d={d} />
                    ))}
                  </svg>
                </g>
                <text x={56} y={cy} dominantBaseline="middle" className="font-sans text-[13px]" fill="var(--sidebar-foreground)">
                  {paso.titulo}
                </text>
                {destacado ? (
                  <g>
                    <rect x={SVG_W - 88} y={cy - 11} width={82} height={22} rx={11} fill="var(--success)" />
                    <text
                      x={SVG_W - 47}
                      y={cy}
                      dominantBaseline="middle"
                      textAnchor="middle"
                      className="font-sans text-[11px] font-medium"
                      fill="var(--success-foreground)"
                    >
                      Aceptado
                    </text>
                  </g>
                ) : (
                  <text
                    x={SVG_W - 6}
                    y={cy}
                    dominantBaseline="middle"
                    textAnchor="end"
                    className="font-mono text-[10.5px]"
                    fill="var(--sidebar-foreground)"
                    opacity={0.5}
                  >
                    {paso.detalle}
                  </text>
                )}
              </g>
            );
          })}
        </svg>

        <div className="mt-5 flex items-center justify-between border-t border-sidebar-border pt-4 font-mono text-sm">
          <span>F001-00001024</span>
          <span className="text-sidebar-foreground/60">S/ 2,360.00</span>
        </div>
      </div>
    </div>
  );
}
