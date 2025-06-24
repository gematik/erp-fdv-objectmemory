package de.gematik.erp.omemory.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
open class WebConfig : WebMvcConfigurer {
    //This config is necessary to allow CORS in Sping Boot, i.e. allow requests to
    // Spring Boot service from different origins(e.g. localhost:5173...)
    // Spring Boot blocks CORS requests per default
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173", "https://storage.googleapis.com") // Frontend port
            .allowedMethods("GET", "POST", "PUT", "OPTIONS")
            .allowedHeaders("*")
    }
}