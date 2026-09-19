package vn.printnest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Diem khoi dong ung dung PrintNest.
 *
 * <p>Kien truc monolithic, to chuc package-by-feature: moi package con
 * ({@code file}, {@code nesting}, {@code export}, {@code job}) chua tron bo
 * controller - service - model cua mot nghiep vu.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class PrintNestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrintNestApplication.class, args);
    }
}
