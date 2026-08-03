package com.eventbooking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Maps the local disk folder event posters get uploaded to
 * (app.uploads.poster-dir) onto the public URL path "/uploads/posters/**",
 * which is exactly the path EventService.uploadPosterImage() stores in
 * Event.posterImageUrl. Without this, an uploaded file would sit on disk
 * with no way for the frontend to actually load it as an <img> src.
 *
 * Deliberately NOT under /api - poster images are public static assets, not
 * API responses, and SecurityConfig's JWT filter only applies to /api/**.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.uploads.poster-dir}")
    private String posterDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = new File(posterDir).getAbsolutePath();
        registry.addResourceHandler("/uploads/posters/**")
                .addResourceLocations("file:" + absolutePath + File.separator);
    }
}
