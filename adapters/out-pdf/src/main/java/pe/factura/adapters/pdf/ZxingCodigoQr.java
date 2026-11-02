package pe.factura.adapters.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import pe.factura.application.port.out.CodigoQr;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/** QR en PNG con ZXing: el del PDF del comprobante y el que se escanea para configurar el segundo factor (#177). */
public class ZxingCodigoQr implements CodigoQr {
    static final int PX = 220;

    @Override public byte[] png(String contenido) {
        try {
            BitMatrix m = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, PX, PX,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(m, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("No se pudo generar el QR", e); }
    }
}
