package com.roleplay.engine;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serve the built Vite frontend from Spring Boot.
 * In dev mode, run the Vite dev server separately (port 5173 → proxies to 8000).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String DIST_PATH = "file:../roleplay-v4/frontend/dist/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static assets (hashed filenames, safe to cache)
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(DIST_PATH)
                .setCachePeriod(3600);

        // Root resources
        registry.addResourceHandler("/favicon.ico", "/vite.svg")
                .addResourceLocations(DIST_PATH)
                .setCachePeriod(3600);

        // SPA fallback: serve index.html for non-API, non-asset paths
        registry.addResourceHandler("/**")
                .addResourceLocations(DIST_PATH)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource r = location.createRelative(resourcePath);
                        if (r.exists() && r.isReadable()) return r;
                        // Fallback to index.html for SPA routing
                        Resource index = location.createRelative("index.html");
                        if (index.exists()) return index;
                        return null;
                    }
                })
                .setCachePeriod(0);
    }
}
