package com.roleplay.engine.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA catch-all: for non-API 404s, serve index.html instead of error page.
 * API endpoints return proper JSON errors.
 */
@Controller
public class FrontendController implements ErrorController {

    private final Resource index = new ClassPathResource("static/index.html");

    @RequestMapping("/error")
    public ResponseEntity<Resource> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = status != null ? Integer.parseInt(status.toString()) : 404;

        // Only SPA-fallback for HTML requests, not API
        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path != null && path.startsWith("/api/")) {
            return ResponseEntity.status(code).build();
        }

        if (code == 404 && index.exists()) {
            return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.TEXT_HTML)
                .body(index);
        }

        return ResponseEntity.status(code).build();
    }
}
