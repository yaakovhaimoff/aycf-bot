package com.aycf.flightFinder.mcp;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Handles MCP 2025-03-26 "Streamable HTTP" POST requests to /sse.
 * VS Code 1.120+ sends POST to the configured URL instead of GET for SSE.
 * We tell VS Code to use GET /sse for the SSE stream instead.
 */
@Slf4j
@RestController
public class McpStreamableController {

    @PostMapping(value = "/sse", produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public void handleStreamableHttpPost(
            @RequestBody(required = false) String body,
            HttpServletResponse response) throws IOException {

        log.info("[MCP] POST /sse received (VS Code streamable HTTP) — redirecting client to use GET /sse");

        // Return a 405 with an Allow header telling the client to use GET.
        // This prompts VS Code to fall back to the classic SSE GET transport.
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        response.setHeader("Allow", "GET");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Use GET /sse for MCP SSE transport\"}");
    }
}
