<#ftl output_format="XHTML" strip_whitespace=true>
<#-- Bloques de la representación impresa comunes a factura, boleta y notas. El modelo trae c (Comprobante), t (Tenant),
     tot (Totales), fechaEmision/fechaVencimiento (dd/MM/yyyy), montoEnLetras, qr (data URI PNG), statics (catálogos) y el diseño de la
     empresa: p (PersonalizacionPdf), plantilla (clase CSS: clasico, moderno, sutil, corporativo, gris) y logo (data URI, opcional). -->
<#assign cat = statics["pe.factura.domain.catalogo.CatalogoSunat"]>
<#function m n><#return n?string("#,##0.00")></#function>
<#function desc catalogo codigo><#local d = cat.descripcion(catalogo, codigo)><#return d.isPresent()?then(d.get(), codigo)></#function>

<#-- Página completa: cabecera con emisor y recuadro RUC/título/número, y luego el contenido anidado. -->
<#macro pagina titulo>
<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${titulo} ${c.serie()}-${c.numero()}</title>
  <style type="text/css">
    @page { size: A4; margin: 14mm 14mm 16mm 14mm; }
    body { font-family: Helvetica, Arial, sans-serif; font-size: 9pt; color: #1c1c1c; line-height: 1.3; }
    table { border-collapse: collapse; width: 100%; }
    .cabecera { width: 100%; margin-bottom: 4mm; }
    .cabecera td { vertical-align: top; }
    .emisor .razon { font-size: 13pt; font-weight: bold; }
    .emisor .comercial { font-size: 10pt; color: #444; }
    .emisor .dato { color: #444; }
    .recuadro { border: 1.2pt solid #1c1c1c; border-radius: 3pt; text-align: center; padding: 3mm 2mm; width: 62mm; }
    .recuadro .ruc { font-size: 11pt; font-weight: bold; }
    .recuadro .titulo { font-size: 11pt; font-weight: bold; margin: 1.5mm 0; }
    .recuadro .numero { font-size: 12pt; font-weight: bold; }
    .bloque { border: 0.6pt solid #9a9a9a; border-radius: 2pt; padding: 2mm 2.5mm; margin-bottom: 3mm; }
    .bloque h2 { font-size: 8pt; text-transform: uppercase; letter-spacing: 0.4pt; color: #555; margin: 0 0 1mm 0; }
    .datos td { padding: 0.4mm 1mm 0.4mm 0; vertical-align: top; }
    .datos td.k { color: #555; white-space: nowrap; width: 28mm; }
    .lineas th { background: #ececec; border-bottom: 0.8pt solid #1c1c1c; border-top: 0.8pt solid #1c1c1c; padding: 1.5mm 1mm; font-size: 8pt; text-transform: uppercase; }
    .lineas td { border-bottom: 0.4pt solid #cfcfcf; padding: 1.2mm 1mm; vertical-align: top; }
    .n { text-align: right; white-space: nowrap; }
    .c { text-align: center; }
    .totales { width: 78mm; margin-left: auto; margin-top: 2mm; }
    .totales td { padding: 0.8mm 1mm; }
    .totales td.k { color: #444; }
    .totales tr.total td { border-top: 1pt solid #1c1c1c; font-weight: bold; font-size: 10pt; }
    .letras { font-weight: bold; margin: 2mm 0 3mm 0; }
    .pie { margin-top: 4mm; }
    .pie td { vertical-align: top; }
    .qr img { width: 32mm; height: 32mm; }
    .hash { font-family: Courier, monospace; font-size: 7.5pt; word-break: break-all; }
    .leyenda { color: #444; font-size: 8pt; margin: 0.5mm 0; }
    .obs { color: #444; font-size: 8pt; }
    .logo { margin-bottom: 1.5mm; }
    .observaciones p { margin: 0; white-space: pre-line; }
    <#-- Plantillas: cada una solo redefine lo que la distingue de la clásica (bordes definidos). El color primario de la empresa
         entra como acento donde la plantilla lo admite; la gris lo ignora a propósito. -->
    <#assign color = p.colorPrimario()>
    .moderno .recuadro { border: none; background: #f4f4f4; }
    .moderno .recuadro .titulo, .moderno .recuadro .numero, .moderno .emisor .razon, .moderno .bloque h2 { color: ${color}; }
    .moderno .bloque { border: none; background: #f7f7f7; }
    .moderno .lineas th { background: none; border-top: none; border-bottom: 1.2pt solid ${color}; color: ${color}; }
    .moderno .totales tr.total td { border-top-color: ${color}; color: ${color}; }
    .sutil .recuadro { border: 0.6pt solid #9a9a9a; }
    .sutil .recuadro .ruc, .sutil .recuadro .titulo { font-weight: normal; color: #444; }
    .sutil .bloque { border: none; border-bottom: 0.4pt solid #cfcfcf; border-radius: 0; padding-left: 0; padding-right: 0; }
    .sutil .lineas th { background: none; border-top: none; border-bottom: 0.6pt solid #9a9a9a; color: #555; font-weight: normal; }
    .sutil .totales tr.total td { border-top: 0.6pt solid #9a9a9a; }
    .corporativo .recuadro { border-color: ${color}; background: ${color}; color: #ffffff; }
    .corporativo .emisor .razon, .corporativo .bloque h2, .corporativo .totales tr.total td { color: ${color}; }
    .corporativo .bloque { border-color: ${color}; }
    .corporativo .lineas th { background: ${color}; color: #ffffff; border-top-color: ${color}; border-bottom-color: ${color}; }
    .corporativo .totales tr.total td { border-top-color: ${color}; }
    .gris .recuadro { border-color: #3a3a3a; background: #3a3a3a; color: #ffffff; }
    .gris .lineas th { background: #3a3a3a; color: #ffffff; border-top-color: #3a3a3a; border-bottom-color: #3a3a3a; }
    .gris .bloque { border-color: #bdbdbd; background: #fafafa; }
  </style>
</head>
<body class="${plantilla}">
  <table class="cabecera">
    <tr>
      <td class="emisor">
        <#if logo??><div><img class="logo" src="${logo}" style="${logoEstilo}" alt="" /></div></#if>
        <div class="razon">${t.razonSocial()}</div>
        <#if t.nombreComercial()??><div class="comercial">${t.nombreComercial()}</div></#if>
        <#if t.domicilio()??>
        <div class="dato">${t.domicilio().direccion()}<#if t.domicilio().urbanizacion()??>, ${t.domicilio().urbanizacion()}</#if></div>
        <div class="dato"><#if t.domicilio().distrito()??>${t.domicilio().distrito()}</#if><#if t.domicilio().provincia()??> - ${t.domicilio().provincia()}</#if><#if t.domicilio().departamento()??> - ${t.domicilio().departamento()}</#if></div>
        </#if>
      </td>
      <td style="width: 66mm; text-align: right;">
        <div class="recuadro">
          <div class="ruc">R.U.C. ${t.ruc()}</div>
          <div class="titulo">${titulo}</div>
          <div class="numero">${c.serie()}-${c.numero()}</div>
        </div>
      </td>
    </tr>
  </table>
  <#nested>
</body>
</html>
</#macro>

<#-- Adquirente y datos de emisión. -->
<#macro partes>
  <div class="bloque">
    <table class="datos">
      <tr>
        <td class="k">${c.receptor().esRuc()?then("Señor(es)", "Cliente")}</td><td>${c.receptor().razonSocial()}</td>
        <td class="k">Fecha de emisión</td><td>${fechaEmision}</td>
      </tr>
      <tr>
        <td class="k">${desc("06", c.receptor().tipoDoc())}</td><td>${c.receptor().numDoc()}</td>
        <td class="k">Fecha de vencimiento</td><td>${fechaVencimiento!"-"}</td>
      </tr>
      <tr>
        <td class="k">Dirección</td><td>${c.receptor().direccion()!"-"}</td>
        <td class="k">Moneda</td><td>${desc("02", c.moneda())}</td>
      </tr>
      <tr>
        <td class="k">Tipo de operación</td><td>${c.tipoOperacion()} - ${desc("51", c.tipoOperacion())}</td>
        <td class="k">Forma de pago</td>
        <td><#if c.formaPago().esCredito()>Crédito (pendiente ${c.moneda()} ${m(c.formaPago().montoPendiente())})<#else>Contado</#if></td>
      </tr>
      <#if !c.referencias().vacias()>
      <tr>
        <td class="k">Referencias</td>
        <td colspan="3">
          <#if c.referencias().ordenCompra()??>Orden de compra ${c.referencias().ordenCompra()}<#if c.referencias().guias()?has_content || c.referencias().otros()?has_content> · </#if></#if>
          <#list c.referencias().guias() as g>Guía ${g.numero()}<#sep> · </#list><#if c.referencias().guias()?has_content && c.referencias().otros()?has_content> · </#if>
          <#list c.referencias().otros() as d>${desc("12", d.tipo())} ${d.numero()}<#sep> · </#list>
        </td>
      </tr>
      </#if>
    </table>
  </div>
</#macro>

<#-- Detalle de ítems: cantidad, unidad, descripción, valor unitario, descuento, IGV y precio de venta de la línea. -->
<#macro lineas>
  <table class="lineas">
    <thead>
      <tr>
        <th class="c" style="width: 12mm;">Cant.</th>
        <th class="c" style="width: 10mm;">Und.</th>
        <th style="width: 18mm;">Código</th>
        <th>Descripción</th>
        <th class="n" style="width: 20mm;">V. unitario</th>
        <th class="n" style="width: 16mm;">Dscto.</th>
        <th class="n" style="width: 16mm;">IGV</th>
        <th class="n" style="width: 22mm;">Importe</th>
      </tr>
    </thead>
    <tbody>
      <#list tot.items() as l>
      <tr>
        <td class="n">${l.item().cantidad()?string("#,##0.###")}</td>
        <td class="c">${l.item().unidad()}</td>
        <td>${l.item().codigo()!""}</td>
        <td>
          ${l.item().descripcion()}
          <#if l.gratuita()><div class="obs">Transferencia gratuita (valor referencial ${m(l.precioVentaUnitario())})</div></#if>
          <#if l.tieneIsc()><div class="obs">ISC ${m(l.isc())}</div></#if>
          <#if l.tieneIcbper()><div class="obs">ICBPER ${m(l.icbper())}</div></#if>
        </td>
        <td class="n">${m(l.valorUnitario())}</td>
        <td class="n"><#if l.descuento() gt 0>-${m(l.descuento())}<#else>-</#if></td>
        <td class="n">${m(l.igv())}</td>
        <td class="n">${l.gratuita()?then("0.00", m(l.precioVenta()))}</td>
      </tr>
      </#list>
    </tbody>
  </table>
</#macro>

<#-- Totales por afectación, descuentos, cargos, anticipos, tributos y el importe total (etiqueta variable: nota de débito "Importe total"). -->
<#macro totales>
  <table class="totales">
    <#if tot.gravado() gt 0 || tot.exonerado() == 0 && tot.inafecto() == 0><tr><td class="k">Op. gravadas</td><td class="n">${c.moneda()} ${m(tot.gravado())}</td></tr></#if>
    <#if tot.exonerado() gt 0><tr><td class="k">Op. exoneradas</td><td class="n">${c.moneda()} ${m(tot.exonerado())}</td></tr></#if>
    <#if tot.inafecto() gt 0><tr><td class="k">Op. inafectas</td><td class="n">${c.moneda()} ${m(tot.inafecto())}</td></tr></#if>
    <#if tot.tieneGratuitas()><tr><td class="k">Op. gratuitas</td><td class="n">${c.moneda()} ${m(tot.gratuito())}</td></tr></#if>
    <#if tot.totalDescuentos() gt 0><tr><td class="k">Descuentos</td><td class="n">- ${c.moneda()} ${m(tot.totalDescuentos())}</td></tr></#if>
    <#if tot.totalCargos() gt 0><tr><td class="k">Cargos</td><td class="n">${c.moneda()} ${m(tot.totalCargos())}</td></tr></#if>
    <#if tot.isc() gt 0><tr><td class="k">ISC</td><td class="n">${c.moneda()} ${m(tot.isc())}</td></tr></#if>
    <tr><td class="k">IGV (${tot.tasaIgv()?string["0.##"]}%)</td><td class="n">${c.moneda()} ${m(tot.igv())}</td></tr>
    <#if tot.icbper() gt 0><tr><td class="k">ICBPER</td><td class="n">${c.moneda()} ${m(tot.icbper())}</td></tr></#if>
    <#if tot.tieneAnticipos()><tr><td class="k">Anticipos</td><td class="n">- ${c.moneda()} ${m(tot.totalAnticipos())}</td></tr></#if>
    <#if tot.tieneRedondeo()><tr><td class="k">Redondeo</td><td class="n">${c.moneda()} ${m(tot.redondeo())}</td></tr></#if>
    <tr class="total"><td class="k">Importe total</td><td class="n">${c.moneda()} ${m(tot.total())}</td></tr>
    <#if c.percepcion()??><tr><td class="k">Percepción (${m(c.percepcion().porcentaje())}%)</td><td class="n">${c.moneda()} ${m(c.percepcion().monto())}</td></tr>
    <tr class="total"><td class="k">Total a pagar</td><td class="n">${c.moneda()} ${m(c.percepcion().totalConPercepcion(tot.total()))}</td></tr></#if>
  </table>
  <div class="letras">SON: ${montoEnLetras}</div>
</#macro>

<#-- Cuotas, detracción, retención y anticipos: solo cuando aplican. -->
<#macro condiciones>
  <#if c.formaPago().esCredito() && c.formaPago().cuotas()?has_content>
  <div class="bloque">
    <h2>Cuotas</h2>
    <table class="datos"><#list c.formaPago().cuotas() as q><tr><td class="k">Cuota ${q?index + 1}</td><td>${c.moneda()} ${m(q.monto())} · vence ${q.vencimiento().format(statics["java.time.format.DateTimeFormatter"].ofPattern("dd/MM/yyyy"))}</td></tr></#list></table>
  </div>
  </#if>
  <#if c.detraccion()??>
  <div class="bloque">
    <h2>Operación sujeta al SPOT (detracción)</h2>
    <table class="datos">
      <tr><td class="k">Bien o servicio</td><td>${c.detraccion().codigoBienServicio()} - ${c.detraccion().descripcionBienServicio()}</td></tr>
      <tr><td class="k">Detracción</td><td>${m(c.detraccion().porcentaje())}% · PEN ${m(c.detraccion().monto())}</td></tr>
      <tr><td class="k">Cuenta Banco de la Nación</td><td>${c.detraccion().cuentaBancoNacion()}</td></tr>
    </table>
  </div>
  </#if>
  <#if c.retencion()??>
  <div class="bloque">
    <h2>Operación sujeta a retención del IGV</h2>
    <table class="datos"><tr><td class="k">Retención</td><td>${m(c.retencion().porcentaje())}% · ${c.moneda()} ${m(c.retencion().monto())}</td></tr></table>
  </div>
  </#if>
  <#if tot.tieneAnticipos()>
  <div class="bloque">
    <h2>Anticipos regularizados</h2>
    <table class="datos"><#list tot.anticipos() as ac><#assign a = ac.anticipo()><tr><td class="k">${a.comprobante()}</td><td>${c.moneda()} ${m(a.monto())} (pagado ${m(ac.importePagado())}<#if a.fechaPago()??> el ${a.fechaPago().format(statics["java.time.format.DateTimeFormatter"].ofPattern("dd/MM/yyyy"))}</#if>)</td></tr></#list></table>
  </div>
  </#if>
</#macro>

<#-- Observaciones del comprobante o, si no trae, las que la empresa imprime por defecto. -->
<#macro observaciones>
  <#assign texto = c.observaciones()!p.observacionesPorDefecto()!"">
  <#if texto?has_content>
  <div class="bloque observaciones">
    <h2>Observaciones</h2>
    <p>${texto}</p>
  </div>
  </#if>
</#macro>

<#-- QR, hash y la leyenda de representación impresa (el pie configurado por la empresa reemplaza la primera línea). -->
<#macro pie tipoNombre>
  <table class="pie">
    <tr>
      <td class="qr" style="width: 36mm;"><img src="${qr}" alt="QR" /></td>
      <td>
        <#if p.pieDePagina()??>
        <p class="leyenda">${p.pieDePagina()}</p>
        <#else>
        <p class="leyenda">Representación impresa de la ${tipoNombre?upper_case} ELECTRÓNICA emitida por ${t.razonSocial()} (RUC ${t.ruc()}) a través de khipu.</p>
        </#if>
        <p class="leyenda">Consulte el documento en el portal de SUNAT (www.sunat.gob.pe) con el RUC del emisor, tipo, serie y número.</p>
        <p class="leyenda">Valor resumen (hash): <span class="hash">${c.hash()}</span></p>
      </td>
    </tr>
  </table>
</#macro>
