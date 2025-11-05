export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Bypass SPA pour ces fichiers (servent le vrai asset)
    if (
      url.pathname === "/robots.txt" ||
      url.pathname === "/sitemap.xml" ||
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
