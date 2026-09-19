<#ftl output_format="XML" strip_whitespace=true>
<#-- Bloques UBL comunes a factura (Invoice), nota de crédito (CreditNote) y nota de débito (DebitNote): los tres comparten
     emisor, receptor, forma de pago, cargos/descuentos, impuestos, totales y líneas; solo cambian la raíz, la cabecera y los
     nombres de tres elementos (línea, cantidad y total monetario). Las reglas SUNAT citadas son las de la hoja Factura2_0;
     NotaCredito2_0 y NotaDebito2_0 repiten las mismas validaciones sobre los mismos tags. -->
<#-- Categoría (UN/ECE 5305, sin validación SUNAT) y tributo (catálogo 05) de una afectación: mismo bloque en los subtotales globales y en cada línea.
     El ID del tributo lleva los atributos del catálogo 05 (reglas 4255–4257); e-beta observa los de UN/ECE 5153 que trae la guía UBL genérica. -->
<#macro categoriaTributo tr>
<cbc:ID schemeID="UN/ECE 5305" schemeName="Tax Category Identifier" schemeAgencyName="United Nations Economic Commission for Europe">${tr.categoria()}</cbc:ID>
<#nested>
<cac:TaxScheme>
  <cbc:ID schemeName="Codigo de tributos" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo05">${tr.codigo()}</cbc:ID>
  <cbc:Name>${tr.nombre()}</cbc:Name>
  <cbc:TaxTypeCode>${tr.tipoInternacional()}</cbc:TaxTypeCode>
</cac:TaxScheme>
</#macro>
<#-- Orden de compra (campo 59, regla 4233). -->
<#macro ordenCompra>
  <#-- Documentos relacionados (campos 59, 22 y 23): orden de compra, guías de remisión (catálogo 01: 09/31) y otros (catálogo 12). Reglas 4233, 4005, 4006, 4009, 4010. -->
  <#if c.referencias().ordenCompra()??>
  <cac:OrderReference>
    <cbc:ID>${c.referencias().ordenCompra()}</cbc:ID>
  </cac:OrderReference>
  </#if>
</#macro>
<#-- Guías de remisión (campo 22, catálogo 01: 09/31) y otros documentos (campo 23, catálogo 12). Reglas 4005, 4006, 4009, 4010. -->
<#macro guiasYOtrosDocumentos>
  <#list c.referencias().guias() as g>
  <cac:DespatchDocumentReference>
    <cbc:ID>${g.numero()}</cbc:ID>
    <cbc:DocumentTypeCode listAgencyName="PE:SUNAT" listName="Tipo de Documento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01">${g.tipo()}</cbc:DocumentTypeCode>
  </cac:DespatchDocumentReference>
  </#list>
  <#-- Sin listName: la hoja SUNAT lo documenta como "Documento Relacionado" pero la regla 4252 exige "Tipo de Documento"; al ser opcional, se omite. -->
  <#list c.referencias().otros() as d>
  <cac:AdditionalDocumentReference>
    <cbc:ID>${d.numero()}</cbc:ID>
    <cbc:DocumentTypeCode listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo12">${d.tipo()}</cbc:DocumentTypeCode>
  </cac:AdditionalDocumentReference>
  </#list>
</#macro>
<#-- Firma (el ds:Signature real lo coloca XmlDsigSigner en ExtensionContent), emisor y receptor. -->
<#macro firmaYPartes>
  <cac:Signature>
    <cbc:ID>signatureFACTURA</cbc:ID>
    <cac:SignatoryParty>
      <cac:PartyIdentification><cbc:ID>${t.ruc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyName><cbc:Name>${t.razonSocial()}</cbc:Name></cac:PartyName>
    </cac:SignatoryParty>
    <cac:DigitalSignatureAttachment>
      <cac:ExternalReference><cbc:URI>#signatureFACTURA</cbc:URI></cac:ExternalReference>
    </cac:DigitalSignatureAttachment>
  </cac:Signature>
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="6" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${t.ruc()}</cbc:ID></cac:PartyIdentification>
      <#-- Nombre comercial (campo 11, regla 4092): opcional; solo cuando la empresa lo configuró. -->
      <#if t.nombreComercial()??><cac:PartyName><cbc:Name>${t.nombreComercial()}</cbc:Name></cac:PartyName></#if>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${t.razonSocial()}</cbc:RegistrationName>
        <#-- Domicilio fiscal (reglas 4093–4098, 4041; establecimiento anexo 3030): ubigeo del catálogo 13 y dirección en una línea. -->
        <#if t.domicilio()??>
        <cac:RegistrationAddress>
          <cbc:ID schemeName="Ubigeos" schemeAgencyName="PE:INEI">${t.domicilio().ubigeo()}</cbc:ID>
          <cbc:AddressTypeCode listAgencyName="PE:SUNAT" listName="Establecimientos anexos">${t.domicilio().codigoEstablecimiento()}</cbc:AddressTypeCode>
          <#if t.domicilio().urbanizacion()??><cbc:CitySubdivisionName>${t.domicilio().urbanizacion()}</cbc:CitySubdivisionName></#if>
          <#if t.domicilio().provincia()??><cbc:CityName>${t.domicilio().provincia()}</cbc:CityName></#if>
          <#if t.domicilio().departamento()??><cbc:CountrySubentity>${t.domicilio().departamento()}</cbc:CountrySubentity></#if>
          <#if t.domicilio().distrito()??><cbc:District>${t.domicilio().distrito()}</cbc:District></#if>
          <cac:AddressLine><cbc:Line>${t.domicilio().direccion()}</cbc:Line></cac:AddressLine>
          <cac:Country><cbc:IdentificationCode listID="ISO 3166-1" listAgencyName="United Nations Economic Commission for Europe" listName="Country">${statics["pe.factura.domain.tenant.Domicilio"].PAIS}</cbc:IdentificationCode></cac:Country>
        </cac:RegistrationAddress>
        <#else>
        <cac:RegistrationAddress><cbc:AddressTypeCode listAgencyName="PE:SUNAT" listName="Establecimientos anexos">0000</cbc:AddressTypeCode></cac:RegistrationAddress>
        </#if>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="${c.receptor().tipoDoc()}" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${c.receptor().numDoc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${c.receptor().razonSocial()}</cbc:RegistrationName>
        <#if c.receptor().direccion()??>
        <cac:RegistrationAddress><cac:AddressLine><cbc:Line>${c.receptor().direccion()}</cbc:Line></cac:AddressLine></cac:RegistrationAddress>
        </#if>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingCustomerParty>
</#macro>
<#-- Detracción, forma de pago, percepción y anticipos pagados (PaymentMeans/PaymentTerms/PrepaidPayment).
     contado=false en las notas: su PaymentTerms FormaPago solo admite Credito/Cuota (regla 3246) y solo va en la NC 13. -->
<#macro pagos contado=true>
  <#-- Detracción (SPOT, reglas 3033–3037, 3127–3129, 3174, 3208): cuenta BN en PaymentMeans y bien/servicio, % y monto en PEN en PaymentTerms. -->
  <#if c.detraccion()??>
  <cac:PaymentMeans>
    <cbc:ID>Detraccion</cbc:ID>
    <cbc:PaymentMeansCode listName="Medio de pago" listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo59">${c.detraccion().medioPago()}</cbc:PaymentMeansCode>
    <cac:PayeeFinancialAccount><cbc:ID>${c.detraccion().cuentaBancoNacion()}</cbc:ID></cac:PayeeFinancialAccount>
  </cac:PaymentMeans>
  <cac:PaymentTerms>
    <cbc:ID>Detraccion</cbc:ID>
    <cbc:PaymentMeansID schemeName="Codigo de detraccion" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo54">${c.detraccion().codigoBienServicio()}</cbc:PaymentMeansID>
    <cbc:PaymentPercent>${c.detraccion().porcentaje()?string["0.#####"]}</cbc:PaymentPercent>
    <cbc:Amount currencyID="PEN">${c.detraccion().monto()}</cbc:Amount>
  </cac:PaymentTerms>
  </#if>
  <#-- Forma de pago (reglas 3244–3267, 3319): un PaymentTerms por indicador; al crédito, el neto pendiente y una cuota por PaymentTerms. -->
  <#if c.formaPago().esCredito()>
  <cac:PaymentTerms>
    <cbc:ID>FormaPago</cbc:ID>
    <cbc:PaymentMeansID>Credito</cbc:PaymentMeansID>
    <cbc:Amount currencyID="${c.moneda()}">${c.formaPago().montoPendiente()}</cbc:Amount>
  </cac:PaymentTerms>
  <#list c.formaPago().cuotas() as q>
  <cac:PaymentTerms>
    <cbc:ID>FormaPago</cbc:ID>
    <cbc:PaymentMeansID>Cuota${(q?index + 1)?string["000"]}</cbc:PaymentMeansID>
    <cbc:Amount currencyID="${c.moneda()}">${q.monto()}</cbc:Amount>
    <cbc:PaymentDueDate>${q.vencimiento().toString()}</cbc:PaymentDueDate>
  </cac:PaymentTerms>
  </#list>
  <#elseif contado>
  <cac:PaymentTerms>
    <cbc:ID>FormaPago</cbc:ID>
    <cbc:PaymentMeansID>Contado</cbc:PaymentMeansID>
  </cac:PaymentTerms>
  </#if>
  <#if c.percepcion()??>
  <cac:PaymentTerms>
    <cbc:ID>Percepcion</cbc:ID>
    <cbc:Amount currencyID="PEN">${c.percepcion().totalConPercepcion(tot.total())}</cbc:Amount>
  </cac:PaymentTerms>
  </#if>
  <#-- Importe pagado con cada anticipo, IGV incluido (reglas 2503, 3211–3213, 3220); el ID es el mismo identificador de pago del documento referenciado. -->
  <#list tot.anticipos() as ac>
  <cac:PrepaidPayment>
    <cbc:ID schemeName="Anticipo" schemeAgencyName="PE:SUNAT">${(ac?index + 1)?c}</cbc:ID>
    <cbc:PaidAmount currencyID="${c.moneda()}">${ac.importePagado()}</cbc:PaidAmount>
    <#if ac.anticipo().fechaPago()??><cbc:PaidDate>${ac.anticipo().fechaPago().toString()}</cbc:PaidDate></#if>
  </cac:PrepaidPayment>
  </#list>
</#macro>
<#-- AllowanceCharge globales: retención, percepción, descuento global, cargos y descuentos por anticipo. -->
<#macro cargosYDescuentosGlobales>
  <#-- Retención del IGV (62, reglas 3262–3264) y percepción (51/52/53, reglas 2788–2798, 3233): AllowanceCharge globales informativos. -->
  <#if c.retencion()??>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">62</cbc:AllowanceChargeReasonCode>
    <cbc:MultiplierFactorNumeric>${c.retencion().factor()?string["0.00000"]}</cbc:MultiplierFactorNumeric>
    <cbc:Amount currencyID="${c.moneda()}">${c.retencion().monto()}</cbc:Amount>
    <cbc:BaseAmount currencyID="${c.moneda()}">${tot.total()}</cbc:BaseAmount>
  </cac:AllowanceCharge>
  </#if>
  <#if c.percepcion()??>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${c.percepcion().regimen()}</cbc:AllowanceChargeReasonCode>
    <cbc:MultiplierFactorNumeric>${c.percepcion().factor()?string["0.00000"]}</cbc:MultiplierFactorNumeric>
    <cbc:Amount currencyID="PEN">${c.percepcion().monto()}</cbc:Amount>
    <cbc:BaseAmount currencyID="PEN">${c.percepcion().base()}</cbc:BaseAmount>
  </cac:AllowanceCharge>
  </#if>
  <#-- Descuento global (catálogo 53: 02 afecta la base del IGV, 03 no). Reglas 3072, 3025, 2968, 3016. -->
  <#if tot.descuentoGlobal()??>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${tot.descuentoGlobal().codigo()}</cbc:AllowanceChargeReasonCode>
    <#if tot.descuentoGlobal().factor().isPresent()><cbc:MultiplierFactorNumeric>${tot.descuentoGlobal().factor().get()?string["0.00000"]}</cbc:MultiplierFactorNumeric></#if>
    <cbc:Amount currencyID="${c.moneda()}">${tot.descuentoGlobal().monto()}</cbc:Amount>
    <cbc:BaseAmount currencyID="${c.moneda()}">${tot.descuentoGlobal().base()}</cbc:BaseAmount>
  </cac:AllowanceCharge>
  </#if>
  <#-- Cargos globales (catálogo 53: 49 afecta la base del IGV, 46/50 no). Reglas 3114, 3072, 3025, 2968, 3016, 3307. -->
  <#list tot.cargosGlobales() as cg>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${cg.codigo()}</cbc:AllowanceChargeReasonCode>
    <#if cg.factor().isPresent()><cbc:MultiplierFactorNumeric>${cg.factor().get()?string["0.00000"]}</cbc:MultiplierFactorNumeric></#if>
    <cbc:Amount currencyID="${c.moneda()}">${cg.monto()}</cbc:Amount>
    <cbc:BaseAmount currencyID="${c.moneda()}">${cg.base()}</cbc:BaseAmount>
  </cac:AllowanceCharge>
  </#list>
  <#-- Descuento global por anticipo (catálogo 53: 04 gravado, 05 exonerado, 06 inafecto) por el valor sin IGV; reduce la base del tributo (3277, 3291) y exige PrepaidAmount (3282, 3287). -->
  <#list tot.anticipos() as ac>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${ac.codigo()}</cbc:AllowanceChargeReasonCode>
    <cbc:Amount currencyID="${c.moneda()}">${ac.monto()}</cbc:Amount>
    <cbc:BaseAmount currencyID="${c.moneda()}">${ac.base()}</cbc:BaseAmount>
  </cac:AllowanceCharge>
  </#list>
</#macro>
<#-- Totales por tributo (TaxTotal global). -->
<#macro impuestos>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="${c.moneda()}">${tot.igv() + tot.ivap() + tot.isc() + tot.icbper()}</cbc:TaxAmount>
    <#list tot.subtotales() as st>
    <cac:TaxSubtotal>
      <#if st.tributo().codigo() != "7152"><cbc:TaxableAmount currencyID="${c.moneda()}">${st.base()}</cbc:TaxableAmount></#if>
      <cbc:TaxAmount currencyID="${c.moneda()}">${st.impuesto()}</cbc:TaxAmount>
      <cac:TaxCategory>
        <@categoriaTributo tr=st.tributo()/>
      </cac:TaxCategory>
    </cac:TaxSubtotal>
    </#list>
  </cac:TaxTotal>
</#macro>
<#-- Totales monetarios: LegalMonetaryTotal en factura y NC, RequestedMonetaryTotal en ND (UBL 2.1). -->
<#macro totalMonetario elemento>
  <cac:${elemento}>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${tot.totalValorVenta()}</cbc:LineExtensionAmount>
    <cbc:TaxInclusiveAmount currencyID="${c.moneda()}">${tot.totalPrecioVenta()}</cbc:TaxInclusiveAmount>
    <#if (tot.totalDescuentos() > 0)>
    <cbc:AllowanceTotalAmount currencyID="${c.moneda()}">${tot.totalDescuentos()}</cbc:AllowanceTotalAmount>
    </#if>
    <#if (tot.totalCargos() > 0)>
    <cbc:ChargeTotalAmount currencyID="${c.moneda()}">${tot.totalCargos()}</cbc:ChargeTotalAmount>
    </#if>
    <#if tot.tieneAnticipos()>
    <cbc:PrepaidAmount currencyID="${c.moneda()}">${tot.totalAnticipos()}</cbc:PrepaidAmount>
    </#if>
    <#if tot.tieneRedondeo()>
    <cbc:PayableRoundingAmount currencyID="${c.moneda()}">${tot.redondeo()}</cbc:PayableRoundingAmount>
    </#if>
    <cbc:PayableAmount currencyID="${c.moneda()}">${tot.total()}</cbc:PayableAmount>
  </cac:${elemento}>
</#macro>
<#-- Líneas: InvoiceLine/InvoicedQuantity, CreditNoteLine/CreditedQuantity o DebitNoteLine/DebitedQuantity. -->
<#macro lineas elemento cantidad>
  <#list tot.items() as it>
  <cac:${elemento}>
    <cbc:ID>${(it?index + 1)?c}</cbc:ID>
    <cbc:${cantidad} unitCode="${it.item().unidad()}" unitCodeListID="UN/ECE rec 20" unitCodeListAgencyName="United Nations Economic Commission for Europe">${it.item().cantidad()?string["0.####"]}</cbc:${cantidad}>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${it.valorVenta()}</cbc:LineExtensionAmount>
    <cac:PricingReference>
      <cac:AlternativeConditionPrice>
        <cbc:PriceAmount currencyID="${c.moneda()}">${it.precioVentaUnitario()?string["0.0000000000"]}</cbc:PriceAmount>
        <cbc:PriceTypeCode listName="Tipo de Precio" listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16">${it.tipoPrecio()}</cbc:PriceTypeCode>
      </cac:AlternativeConditionPrice>
    </cac:PricingReference>
    <#if it.item().tieneDescuento()>
    <cac:AllowanceCharge>
      <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
      <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${it.item().descuento().codigoSunat(false)}</cbc:AllowanceChargeReasonCode>
      <#if it.descuentoFactor().isPresent()><cbc:MultiplierFactorNumeric>${it.descuentoFactor().get()?string["0.00000"]}</cbc:MultiplierFactorNumeric></#if>
      <cbc:Amount currencyID="${c.moneda()}">${it.descuento()}</cbc:Amount>
      <cbc:BaseAmount currencyID="${c.moneda()}">${it.baseBruta()}</cbc:BaseAmount>
    </cac:AllowanceCharge>
    </#if>
    <#-- Cargos de línea (catálogo 53: 47 afecta la base del IGV, 48 no). Reglas 3114, 3073, 3052, 2955, 3053, 3290. -->
    <#list it.cargos() as cg>
    <cac:AllowanceCharge>
      <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
      <cbc:AllowanceChargeReasonCode listAgencyName="PE:SUNAT" listName="Cargo/descuento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53">${cg.codigo()}</cbc:AllowanceChargeReasonCode>
      <#if cg.factor().isPresent()><cbc:MultiplierFactorNumeric>${cg.factor().get()?string["0.00000"]}</cbc:MultiplierFactorNumeric></#if>
      <cbc:Amount currencyID="${c.moneda()}">${cg.monto()}</cbc:Amount>
      <cbc:BaseAmount currencyID="${c.moneda()}">${cg.base()}</cbc:BaseAmount>
    </cac:AllowanceCharge>
    </#list>
    <cac:TaxTotal>
      <cbc:TaxAmount currencyID="${c.moneda()}">${it.totalTributos()}</cbc:TaxAmount>
      <#-- ISC (2000): base = valor de venta (01/02) o PVP sugerido × cantidad (03), TierRange = sistema (catálogo 08); reglas 3108, 2373. -->
      <#if it.tieneIsc()>
      <cac:TaxSubtotal>
        <cbc:TaxableAmount currencyID="${c.moneda()}">${it.iscBase()}</cbc:TaxableAmount>
        <cbc:TaxAmount currencyID="${c.moneda()}">${it.isc()}</cbc:TaxAmount>
        <cac:TaxCategory>
          <@categoriaTributo tr=statics["pe.factura.domain.documento.Tributo"].ISC>
          <cbc:Percent>${it.iscPorcentaje()?string["0.00###"]}</cbc:Percent>
          <cbc:TierRange>${it.item().isc().sistema()}</cbc:TierRange>
          </@categoriaTributo>
        </cac:TaxCategory>
      </cac:TaxSubtotal>
      </#if>
      <#-- ICBPER (7152): sin base ni tasa; bolsas = cantidad del ítem y monto unitario vigente (reglas 3236–3238). -->
      <#if it.tieneIcbper()>
      <cac:TaxSubtotal>
        <cbc:TaxAmount currencyID="${c.moneda()}">${it.icbper()}</cbc:TaxAmount>
        <cbc:BaseUnitMeasure unitCode="NIU">${it.item().cantidad()?string["0"]}</cbc:BaseUnitMeasure>
        <cac:TaxCategory>
          <@categoriaTributo tr=statics["pe.factura.domain.documento.Tributo"].ICBPER>
          <cbc:PerUnitAmount currencyID="${c.moneda()}">${it.icbperUnitario()}</cbc:PerUnitAmount>
          </@categoriaTributo>
        </cac:TaxCategory>
      </cac:TaxSubtotal>
      </#if>
      <cac:TaxSubtotal>
        <cbc:TaxableAmount currencyID="${c.moneda()}">${it.baseIgv()}</cbc:TaxableAmount>
        <cbc:TaxAmount currencyID="${c.moneda()}">${it.igv()}</cbc:TaxAmount>
        <cac:TaxCategory>
          <@categoriaTributo tr=it.tributo()>
          <cbc:Percent>${it.porcentajeIgv()}</cbc:Percent>
          <cbc:TaxExemptionReasonCode listAgencyName="PE:SUNAT" listName="Afectacion del IGV" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07">${it.item().afectacion().codigo()}</cbc:TaxExemptionReasonCode>
          </@categoriaTributo>
        </cac:TaxCategory>
      </cac:TaxSubtotal>
    </cac:TaxTotal>
    <cac:Item>
      <cbc:Description>${it.item().descripcion()}</cbc:Description>
      <#if it.item().codigo()??><cac:SellersItemIdentification><cbc:ID>${it.item().codigo()}</cbc:ID></cac:SellersItemIdentification></#if>
      <#-- GTIN (campo 29, reglas 4333–4335) y código de producto SUNAT (campo 28, catálogo 25 UNSPSC; reglas 3496, 4331). -->
      <#if it.item().tieneGtin()><cac:StandardItemIdentification><cbc:ID schemeID="${it.item().gtin().tipo()}">${it.item().gtin().codigo()}</cbc:ID></cac:StandardItemIdentification></#if>
      <#if it.item().tieneCodigoSunat()><cac:CommodityClassification><cbc:ItemClassificationCode listID="UNSPSC" listAgencyName="GS1 US" listName="Item Classification">${it.item().codigoSunat().codigo()}</cbc:ItemClassificationCode></cac:CommodityClassification></#if>
    </cac:Item>
    <cac:Price><cbc:PriceAmount currencyID="${c.moneda()}">${it.valorUnitario()?string["0.0000000000"]}</cbc:PriceAmount></cac:Price>
  </cac:${elemento}>
  </#list>
</#macro>
