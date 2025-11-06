export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);

      // Helper function to add security headers
      const addSecurityHeaders = (headers) => {
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
      };

      // Serve robots.txt with correct content-type
      if (url.pathname === "/robots.txt") {
        if (request.method !== "GET" && request.method !== "HEAD") {
          return new Response("Method Not Allowed", {
            status: 405,
            headers: addSecurityHeaders(new Headers({
              "Allow": "GET, HEAD"
            }))
          });
        }
        const robotsContent = "User-agent: *\nAllow: /\nSitemap: https://sentinelquantumvanguardaipro.pages.dev/sitemap.xml\n";
        return new Response(robotsContent, {
          status: 200,
          headers: addSecurityHeaders(new Headers({
            "Content-Type": "text/plain; charset=utf-8",
            "Cache-Control": "public, max-age=3600"
          }))
        });
      }

      // Serve sitemap.xml with correct content-type
      if (url.pathname === "/sitemap.xml") {
        if (request.method !== "GET" && request.method !== "HEAD") {
          return new Response("Method Not Allowed", {
            status: 405,
            headers: addSecurityHeaders(new Headers({
              "Allow": "GET, HEAD"
            }))
          });
        }
        const sitemapContent = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>https://sentinelquantumvanguardaipro.pages.dev/</loc></url>
</urlset>`;
        return new Response(sitemapContent, {
          status: 200,
          headers: addSecurityHeaders(new Headers({
            "Content-Type": "application/xml; charset=utf-8",
            "Cache-Control": "public, max-age=3600"
          }))
        });
      }

      // Bypass SPA for static assets
      if (
        url.pathname.startsWith("/assets/") ||
        /\.(css|js|mjs|json|png|jpg|jpeg|svg|webp|ico|map)$/i.test(url.pathname)
      ) {
        const response = await env.ASSETS.fetch(request);
        const headers = new Headers(response.headers);
        addSecurityHeaders(headers);
        return new Response(response.body, {
          status: response.status,
          statusText: response.statusText,
          headers
        });
      }

      // 1) Try to fetch the asset as-is
      let res = await env.ASSETS.fetch(request);

      // 2) If not found, fallback to SPA -> index.html
      if (res.status === 404 || res.status === 405) {
        res = await env.ASSETS.fetch(new Request(new URL("/index.html", url), request));
      }

      // Add security headers to all responses
      const headers = new Headers(res.headers);
      addSecurityHeaders(headers);
      return new Response(res.body, {
        status: res.status,
        statusText: res.statusText,
        headers
      });

    } catch (error) {
      // Error handling for any unexpected issues
      return new Response("Internal Server Error", {
        status: 500,
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Referrer-Policy": "strict-origin-when-cross-origin",
          "X-Content-Type-Options": "nosniff"
        }
      });
    }
  }
}
