package pe.factura.application.port.in;

import pe.factura.domain.tenant.Entorno;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Listado de empresas de toda la plataforma para el backoffice (#185): las que pueden emitir y las que están por quedarse fuera.
 * Lectura pura: no se audita. «Hoy» y «el mes» son los de Lima (la zona de la aplicación), no los de la base.
 */
public interface ListarEmpresasAdminUseCase {
    /** Un certificado con menos de estos días de vigencia está «por vencer»: el umbral de la épica #11 («< 30 días»). */
    int DIAS_POR_VENCER = 30;

    List<EmpresaResumen> listar(Filtro filtro, int pagina, int porPagina);
    long contar(Filtro filtro);

    /**
     * Cómo está el certificado de una empresa hoy. El último día de vigencia todavía vale ({@code VENCIDO} desde el día siguiente), y con
     * exactamente {@link #DIAS_POR_VENCER} días todavía es {@code VIGENTE}. {@code SIN_FECHA}: hay certificado, pero no se conoce su vigencia.
     */
    enum EstadoCertificado { SIN_CERTIFICADO, SIN_FECHA, VIGENTE, POR_VENCER, VENCIDO }

    /**
     * Una fila del listado. {@code certificadoDiasRestantes} es negativo si ya venció y nulo si no hay fecha. {@code series}: las activas.
     * {@code comprobantesDelMes}: los documentos con fecha de emisión en el mes de hoy. {@code ultimaEmision}: la fecha de emisión del
     * documento más reciente; nula si nunca emitió.
     */
    record EmpresaResumen(UUID id, String ruc, String razonSocial, UUID cuentaId, String cuentaNombre, Entorno entorno,
                          EstadoCertificado certificado, LocalDate certificadoVigenteHasta, Integer certificadoDiasRestantes,
                          boolean tieneCredencialesSol, int series, int comprobantesDelMes, LocalDate ultimaEmision) {}

    /** Ambos filtros son opcionales y se combinan con «y». */
    record Filtro(Entorno entorno, EstadoCertificado certificado) {
        public static final Filtro NINGUNO = new Filtro(null, null);
    }
}
