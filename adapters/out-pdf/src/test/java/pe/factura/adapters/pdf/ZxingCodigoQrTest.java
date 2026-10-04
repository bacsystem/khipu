package pe.factura.adapters.pdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ZxingCodigoQrTest {
    /** Lo que importa es que la app de autenticación lo lea: se decodifica la imagen y se compara con lo que se codificó. */
    @Test void generaUnPngQueSeLeeComoElContenido() throws Exception {
        String uri = "otpauth://totp/khipu:ana%40khipu.pe?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=khipu&algorithm=SHA1&digits=6&period=30";

        byte[] png = new ZxingCodigoQr().png(uri);

        assertThat(png).startsWith(0x89, 'P', 'N', 'G');
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).as("escaneable desde la pantalla").isGreaterThanOrEqualTo(200);
        String leido = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)))).getText();
        assertThat(leido).isEqualTo(uri);
    }
}
