export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Serve robots.txt with correct content-type
    if (url.pathname === "/robots.txt" && request.method === "GET") {
      const robotsContent = "User-agent: *\nAllow: /\nSitemap: https://sentinelquantumvanguardaipro.pages.dev/sitemap.xml\n";
      return new Response(robotsContent, {
        status: 200,
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Referrer-Policy": "strict-origin-when-cross-origin",
          "X-Content-Type-Options": "nosniff"
        }
      });
    }

    // Serve sitemap.xml with correct content-type
    if (url.pathname === "/sitemap.xml" && request.method === "GET") {
      const sitemapContent = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>https://sentinelquantumvanguardaipro.pages.dev/</loc></url>
</urlset>`;
      return new Response(sitemapContent, {
        status: 200,
        headers: {
          "Content-Type": "application/xml; charset=utf-8",
          "Referrer-Policy": "strict-origin-when-cross-origin",
          "X-Content-Type-Options": "nosniff"
        }
      });
    }

    // Bypass SPA for static assets
    if (
      url.pathname.startsWith("/assets/") ||
      /\.(css|js|mjs|json|png|jpg|jpeg|svg|webp|ico|map)$/i.test(url.pathname)
    ) {
      return env.ASSETS.fetch(request);
    }

    // 1) Try to fetch the asset as-is
    let res = await env.ASSETS.fetch(request);

    // 2) If not found, fallback to SPA -> index.html
    if (res.status === 404 || res.status === 405) {
      return env.ASSETS.fetch(new Request(new URL("/index.html", url), request));
    }
    return res;
  }
}
