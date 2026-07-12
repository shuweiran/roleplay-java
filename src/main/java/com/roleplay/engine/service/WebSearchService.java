package com.roleplay.engine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Web search via DuckDuckGo.
 * Maps from Python services/web_search.py.
 */
@Service
public class WebSearchService {
    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private final HttpClient client;

    public WebSearchService() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /** Search DuckDuckGo and return text snippets. */
    public List<Map<String, String>> search(String query, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            String url = "https://html.duckduckgo.com/html/?q=" +
                java.net.URLEncoder.encode(query, "UTF-8");
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(15))
                .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            // Parse result snippets (simplified)
            String body = resp.body();
            String[] snippets = body.split("<a rel=\"nofollow\" class=\"result__a\"");
            for (int i = 1; i < Math.min(snippets.length, maxResults + 1); i++) {
                String s = snippets[i];
                String title = s.replaceAll(".*?>", "").replaceAll("<.*", "").trim();
                String snippet = "";
                var m = java.util.regex.Pattern.compile("class=\"result__snippet\">(.*?)</a>")
                    .matcher(s);
                if (m.find()) snippet = m.group(1).replaceAll("<[^>]+>", "").trim();
                if (!title.isEmpty()) {
                    results.add(Map.of("title", title, "snippet", snippet));
                }
            }
        } catch (Exception e) {
            log.warn("Search failed: {}", e.getMessage());
        }
        return results;
    }

    /** Fetch a URL's text content. */
    public String fetchContent(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(15))
                .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            // Strip HTML tags
            return body.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .substring(0, Math.min(2000, body.length()));
        } catch (Exception e) {
            log.warn("Fetch failed: {}", e.getMessage());
            return "";
        }
    }
}
