<#ftl output_format="XHTML" strip_whitespace=true>
<#import "comun.ftl" as u>
<@u.pagina titulo="NOTA DE CRÉDITO ELECTRÓNICA">
  <@u.partes />
  <div class="bloque">
    <h2>Documento que modifica</h2>
    <table class="datos">
      <tr><td class="k">Comprobante</td><td>${u.desc("01", c.nota().tipoAfectado().codigo())} ${c.nota().documentoAfectado()}</td></tr>
      <tr><td class="k">Motivo</td><td>${c.nota().motivo()} - ${c.nota().descripcionMotivo(c.tipo())}</td></tr>
      <tr><td class="k">Sustento</td><td>${c.nota().descripcion()}</td></tr>
    </table>
  </div>
  <@u.lineas />
  <@u.totales />
  <@u.condiciones />
  <@u.pie tipoNombre="nota de crédito" />
</@u.pagina>
