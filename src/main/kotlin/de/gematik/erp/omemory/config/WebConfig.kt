package de.gematik.erp.omemory.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
open class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173", "https://storage.googleapis.com") // Frontend port
            .allowedMethods("GET", "POST", "PUT", "OPTIONS")
            .allowedHeaders("*")
    }
}