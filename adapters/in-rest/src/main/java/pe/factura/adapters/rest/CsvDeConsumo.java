package pe.factura.adapters.rest;

import pe.factura.application.port.in.ConsultarConsumoDeCuentasUseCase.Fila;
import pe.factura.domain.plan.CicloMensual;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * La exportación del consumo de todas las cuentas (#193) en CSV (RFC 4180: registros con CRLF, celdas con coma, comillas o saltos entre comillas) con la marca UTF-8 al
 * inicio para que Excel lea bien las tildes. Los textos los escribió un cliente (nombre, correo, plan): una celda que empieza por {@code = + - @}, tabulador o retorno
 * se evalúa como fórmula al abrirla en una hoja de cálculo, así que se le antepone una comilla simple y queda como texto. Las columnas numéricas las pone el sistema y
 * no se tocan.
 */
final class CsvDeConsumo {
    private static final String CABECERA = "cuenta_id,cuenta,correo,plan,documentos,limite,porcentaje,en_alerta,estado_del_plan,pagado_hasta,se_sirve_hasta";
    private static final String FIN_DE_LINEA = "\r\n";

    private CsvDeConsumo() {}

    static String de(List<Fila> filas) {
        return "﻿" + Stream.concat(Stream.of(CABECERA), filas.stream().map(CsvDeConsumo::linea)).collect(Collectors.joining(FIN_DE_LINEA, "", FIN_DE_LINEA));
    }

    private static String linea(Fila f) {
        return String.join(",", f.cuentaId().toString(), texto(f.nombre()), texto(f.email()), texto(f.planNombre()), String.valueOf(f.documentos()),
                f.limite().ilimitado() ? "ilimitado" : String.valueOf(f.limite().maximo()), f.porcentaje() == null ? "" : String.valueOf(f.porcentaje()),
                f.enAlerta() ? "si" : "no", f.estado().name(), ultimoDiaCubierto(f.venceEn()), ultimoDiaCubierto(f.hastaCuandoCubre()));
    }

    /**
     * El último día que cubre un vencimiento, en hora de Lima ({@code AAAA-MM-DD}), como lo dice la pantalla: el vencimiento es exclusivo, así que es el día del
     * instante anterior. Vacío si no vence.
     */
    private static String ultimoDiaCubierto(Instant vence) {
        return vence == null ? "" : vence.minusSeconds(1).atZone(CicloMensual.ZONA).toLocalDate().toString();
    }

    /** Un texto del cliente: sin que se lea como fórmula y, si hace falta, entre comillas. */
    private static String texto(String valor) {
        String t = valor == null ? "" : valor;
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0) t = "'" + t;
        return t.matches("(?s).*[\",\r\n].*") ? "\"" + t.replace("\"", "\"\"") + "\"" : t;
    }
}
