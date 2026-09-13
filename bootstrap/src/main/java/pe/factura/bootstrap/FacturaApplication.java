package pe.factura.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "pe.factura")
@EnableScheduling
public class FacturaApplication {
    public static void main(String[] args) {
        SpringApplication.run(FacturaApplication.class, args);
    }
}
