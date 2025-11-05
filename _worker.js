export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // robots.txt - Force text/plain
    if (url.pathname === "/robots.txt") {
      const body = [
        "User-agent: *",
        "Allow: /",
        "Sitemap: https://sentinelquantumvanguardaipro.pages.dev/sitemap.xml",
        ""
      ].join("\n");
      return new Response(body, {
        headers: { "content-type": "text/plain; charset=utf-8" }
      });
    }

    // sitemap.xml - Force application/xml
    if (url.pathname === "/sitemap.xml") {
      const xml = `<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n  <url><loc>https://sentinelquantumvanguardaipro.pages.dev/</loc></url>\n</urlset>`;
      return new Response(xml, {
        headers: { "content-type": "application/xml; charset=utf-8" }
      });
    }

    // Bypass SPA pour ces fichiers (servent le vrai asset)
    if (
      url.pathname.startsWith("/assets/") ||
      /\.(css|js|mjs|json|png|jpg|jpeg|svg|webp|ico|txt|xml|map)$/i.test(url.pathname)
    ) {
      return env.ASSETS.fetch(request);
    }

    // 1) Essaye l'asset tel quel
    let res = await env.ASSETS.fetch(request);

    // 2) Sinon fallback SPA -> index.html
    if (res.status === 404 || res.status === 405) {
      return env.ASSETS.fetch(new Request(new URL("/index.html", url), request));
    }
    return res;
  }
}