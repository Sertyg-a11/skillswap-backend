package nl.ak.skillswap.skillswap;

import nl.ak.skillswap.skillswap.config.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(SecurityConfig.class)
public class ApiGetawayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGetawayApplication.class, args);
    }

}
