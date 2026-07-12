package com.roleplay.engine;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serve the built Vite frontend from Spring Boot.
 * In dev mode, run the Vite dev server separately (port 5173 → proxies to 8000).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve frontend dist/ as static resources (for production / single-jar deploy)
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "file:../frontend/dist/")
                .setCachePeriod(0);
    }
}
