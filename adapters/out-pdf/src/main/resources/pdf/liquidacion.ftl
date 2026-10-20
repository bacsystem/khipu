<#ftl output_format="XHTML" strip_whitespace=true>
<#import "comun.ftl" as u>
<#-- Liquidación de compra (04, #327): el emisor es el comprador y la contraparte el vendedor sin RUC. Lo que cambia frente a la factura: el bloque del vendedor (con su
     domicilio y su condición, catálogo 60), el lugar de la operación y los totales, que terminan en lo que el comprador retiene (IGV y renta) y el neto que paga. -->
<#assign lc = c.liquidacion()>
<@u.pagina titulo="LIQUIDACIÓN DE COMPRA ELECTRÓNICA">
  <div class="bloque">
    <h2>Vendedor</h2>
    <table class="datos">
      <tr>
        <td class="k">Nombre</td><td>${c.receptor().razonSocial()}</td>
        <td class="k">Fecha de emisión</td><td>${fechaEmision}</td>
      </tr>
      <tr>
        <td class="k">Documento</td><td>${u.desc("06", c.receptor().tipoDoc())} ${c.receptor().numDoc()}</td>
        <td class="k">Moneda</td><td>${u.desc("02", c.moneda())}</td>
      </tr>
      <tr>
        <td class="k">Domicilio</td><td>${c.receptor().direccion()} (${u.desc("13", lc.ubigeoVendedor())})</td>
        <td class="k">Condición</td><td>${u.desc("60", lc.condicionVendedor())}</td>
      </tr>
      <tr>
        <td class="k">Tipo de operación</td><td colspan="3">${c.tipoOperacion()} - ${u.desc("51", c.tipoOperacion())}</td>
      </tr>
    </table>
  </div>
  <div class="bloque">
    <h2>Lugar de la operación</h2>
    <table class="datos">
      <tr><td class="k">Dirección</td><td>${lc.lugarOperacion().direccion()} (${u.desc("13", lc.lugarOperacion().ubigeo())})</td></tr>
      <tr><td class="k">Condición</td><td>${u.desc("60", lc.lugarOperacion().condicion())}</td></tr>
      <#if !c.referencias().vacias()>
      <tr>
        <td class="k">Referencias</td>
        <td><#list c.referencias().guias() as g>Guía ${g.numero()}<#sep> · </#list><#if c.referencias().guias()?has_content && c.referencias().otros()?has_content> · </#if><#list c.referencias().otros() as d>${u.desc("12", d.tipo())} ${d.numero()}<#sep> · </#list></td>
      </tr>
      </#if>
    </table>
  </div>
  <@u.lineas />
  <table class="totales">
    <#if tot.gravado() gt 0 || tot.exonerado() == 0 && tot.inafecto() == 0><tr><td class="k">Op. gravadas</td><td class="n">${c.moneda()} ${u.m(tot.gravado())}</td></tr></#if>
    <#if tot.exonerado() gt 0><tr><td class="k">Op. exoneradas</td><td class="n">${c.moneda()} ${u.m(tot.exonerado())}</td></tr></#if>
    <#if tot.inafecto() gt 0><tr><td class="k">Op. inafectas</td><td class="n">${c.moneda()} ${u.m(tot.inafecto())}</td></tr></#if>
    <tr><td class="k">IGV (${tot.tasaIgv()?string["0.##"]}%)</td><td class="n">${c.moneda()} ${u.m(tot.igv())}</td></tr>
    <tr><td class="k">Subtotal</td><td class="n">${c.moneda()} ${u.m(tot.totalPrecioVenta())}</td></tr>
    <#if tot.igv() gt 0><tr><td class="k">IGV retenido</td><td class="n">- ${c.moneda()} ${u.m(tot.igv())}</td></tr></#if>
    <tr><td class="k">Retención Impuesto a la Renta (${lc.tasaRetencionRenta()?string["0.##"]}%)</td><td class="n">- ${c.moneda()} ${u.m(c.retencionRenta())}</td></tr>
    <#if tot.tieneRedondeo()><tr><td class="k">Redondeo</td><td class="n">${c.moneda()} ${u.m(tot.redondeo())}</td></tr></#if>
    <tr class="total"><td class="k">Importe neto a pagar</td><td class="n">${c.moneda()} ${u.m(tot.total())}</td></tr>
    <#-- #316: el tipo de cambio informado y el equivalente en soles; no son parte del XML. -->
    <#if c.tipoCambio()??>
    <tr><td class="k">Tipo de cambio (S/ por ${c.moneda()})</td><td class="n">${c.tipoCambio()?string["0.000"]}</td></tr>
    <tr><td class="k">Equivalente en soles</td><td class="n">S/ ${u.m(tot.total() * c.tipoCambio())}</td></tr>
    </#if>
  </table>
  <div class="letras">SON: ${montoEnLetras}</div>
  <@u.observaciones />
  <@u.pie tipoNombre="liquidación de compra" />
</@u.pagina>
