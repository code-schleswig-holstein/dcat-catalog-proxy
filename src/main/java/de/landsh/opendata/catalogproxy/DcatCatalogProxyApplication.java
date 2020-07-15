package de.landsh.opendata.catalogproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DcatCatalogProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(DcatCatalogProxyApplication.class, args);
    }

    @Bean
    CatalogFilter catalogFilter() {
        return new CatalogFilter();
    }
}
