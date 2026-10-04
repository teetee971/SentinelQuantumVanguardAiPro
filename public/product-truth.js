(() => {
  if (window.__sentinelProductTruthRuntimeLoaded) return;
  window.__sentinelProductTruthRuntimeLoaded = true;

  const endpoint = '/public/product-truth.generated.json';
  const pageBindings = {
    '/public/mobile-security.html': { capabilities: ['phone_core_android'] },
    '/public/download-guide.html': { capabilities: ['public_android_release', 'phone_core_android'] },
    '/public/phone-intelligence.html': { modules: ['phone_intelligence_basic'], capabilities: ['collective_defense_backend', 'collective_defense_android'] },
    '/public/geointel.html': { modules: ['geointel_basic'], capabilities: ['geointel_usgs', 'geointel_multisource'] },
    '/public/investigations.html': { modules: ['sentinel_investigations'] },
    '/public/threat-intelligence.html': { modules: ['threat_brief_pro', 'foreign_interference_defense'] },
    '/public/security-audit.html': { modules: ['security_audit_basic'] },
    '/public/espace-client.html': { capabilities: ['saas_identity'] },
    '/public/pricing.html': { commerce: true },
    '/public/product-status.html': { allCapabilities: true },
    '/public/roadmap.html': { allCapabilities: true },
    '/public/system-status.html': { allCapabilities: true },
  };

  const text = (value) => value == null ? '—' : String(value);
  const labelTier = (tier) => ({
    FREE: 'Free',
    PREMIUM_INDIVIDUAL: 'Premium',
    PRO: 'Pro',
    ENTERPRISE: 'Enterprise',
  }[tier] ?? text(tier));

  const labelOffer = (offer) => ({
    INCLUDED: 'Inclus',
    INCLUDED_WHEN_READY: 'Inclus lorsqu’il sera prêt',
    NOT_FOR_SALE: 'Non commercialisé',
  }[offer] ?? text(offer));

  const capabilityAvailable = (capability, now = Date.now()) => {
    const deadline = Number(capability?.expires_at_ms);
    return capability?.customer_available === true
      && Number.isSafeInteger(deadline)
      && deadline > now;
  };

  const effectiveCapabilityState = (capability) => {
    if (capability?.state === 'AVAILABLE' && !capabilityAvailable(capability)) return 'VALIDATION';
    return capability?.state ?? 'VALIDATION';
  };

  const labelCapability = (capability) => {
    if (capabilityAvailable(capability)) return 'Disponible';
    const state = effectiveCapabilityState(capability);
    if (state === 'PLANNED') return 'Planifié';
    if (state === 'INFRASTRUCTURE') return 'Infrastructure requise';
    return 'En validation';
  };

  function td(value) {
    const cell = document.createElement('td');
    cell.textContent = text(value);
    return cell;
  }

  function currentPageBinding() {
    const path = window.location.pathname === '/' ? '/index.html' : window.location.pathname;
    return pageBindings[path] ?? null;
  }

  function refreshStaticCapabilityAvailability() {
    for (const element of document.querySelectorAll('[data-product-available]')) {
      const deadline = Number(element.dataset.productExpires);
      const available = element.dataset.productAvailable === 'true'
        && Number.isSafeInteger(deadline)
        && deadline > Date.now();
      element.textContent = available ? 'Disponible' : 'Non disponible';
    }
  }

  function injectGlobalStyles() {
    if (document.getElementById('sentinel-product-truth-styles')) return;
    const style = document.createElement('style');
    style.id = 'sentinel-product-truth-styles';
    style.textContent = `
      .sentinel-truth-strip{box-sizing:border-box;margin:0 auto 14px;max-width:1180px;padding:10px 14px;border:1px solid rgba(121,177,255,.24);border-radius:12px;background:rgba(8,13,21,.92);color:#dbe8ff;font:600 12px/1.45 system-ui,-apple-system,Segoe UI,sans-serif;display:flex;gap:10px;align-items:center;justify-content:space-between;flex-wrap:wrap}
      .sentinel-truth-strip strong{color:#fff}.sentinel-truth-strip a{color:#91bdff;text-decoration:none}.sentinel-truth-strip a:hover{text-decoration:underline}.sentinel-truth-state{opacity:.86;font-weight:500}.sentinel-truth-state[data-state="AVAILABLE"]{color:#9ce5b2}.sentinel-truth-state[data-state="VALIDATION"]{color:#ffd48a}.sentinel-truth-state[data-state="INFRASTRUCTURE"],.sentinel-truth-state[data-state="PLANNED"]{color:#b8c8dc}
    `;
    document.head.append(style);
  }

  function relevantState(payload) {
    const binding = currentPageBinding();
    if (!binding) return null;
    const states = [];
    for (const id of binding.capabilities ?? []) {
      const item = payload.capabilities?.find((capability) => capability.id === id);
      if (item) states.push({ name: item.surface, state: effectiveCapabilityState(item), label: labelCapability(item) });
    }
    for (const id of binding.modules ?? []) {
      const item = payload.modules?.find((module) => module.id === id);
      if (item) states.push({ name: item.name, state: item.offer === 'NOT_FOR_SALE' ? 'PLANNED' : 'VALIDATION', label: `${labelTier(item.tier)} · ${labelOffer(item.offer)}` });
    }
    return states;
  }

  function renderGlobalTruthStrip(payload) {
    injectGlobalStyles();
    if (document.querySelector('.sentinel-truth-strip')) return;
    const capabilities = payload.capabilities ?? [];
    const available = capabilities.filter((item) => capabilityAvailable(item)).length;
    const updated = [
      payload.generated_from?.product_capabilities_updated_at,
      payload.generated_from?.commercial_catalog_updated_at,
      payload.generated_from?.commercial_pro_extension_updated_at,
    ].filter(Boolean).sort().at(-1) ?? 'inconnue';
    const relevant = relevantState(payload);

    const strip = document.createElement('aside');
    strip.className = 'sentinel-truth-strip';
    strip.setAttribute('aria-label', 'État produit Sentinel synchronisé');

    const main = document.createElement('span');
    const strong = document.createElement('strong');
    strong.textContent = 'État Sentinel synchronisé';
    main.append(strong, document.createTextNode(` · source canonique ${updated} · ${available}/${capabilities.length} capacités disponibles client`));

    const status = document.createElement('span');
    status.className = 'sentinel-truth-state';
    if (relevant?.length) {
      const representative = relevant.find((item) => item.state !== 'AVAILABLE') ?? relevant[0];
      status.dataset.state = representative.state;
      status.textContent = relevant.map((item) => `${item.name}: ${item.label}`).join(' · ');
    } else {
      status.dataset.state = 'VALIDATION';
      status.textContent = payload.commerce?.checkout_enabled ? 'Commerce actif selon entitlement' : 'Commerce payant non activé';
    }

    const link = document.createElement('a');
    link.href = '/public/product-status.html';
    link.textContent = 'Voir l’état produit';

    strip.append(main, status, link);
    const mainContent = document.querySelector('main');
    if (mainContent?.parentNode) mainContent.parentNode.insertBefore(strip, mainContent);
    else document.body.prepend(strip);
  }

  function renderCommercialCatalog(payload) {
    document.querySelectorAll('[data-commercial-catalog-generated]').forEach((target) => {
      const body = document.createElement('tbody');
      for (const module of payload.modules ?? []) {
        const row = document.createElement('tr');
        row.append(td(module.name));
        row.append(td(labelTier(module.tier)));
        row.append(td(labelOffer(module.offer)));
        row.append(td(module.permanent_free ? 'Gratuit permanent' : (module.trial_eligible ? 'Essai activable' : module.trial_candidate ? 'Candidat essai après validation' : 'Hors essai')));
        body.append(row);
      }
      target.replaceChildren(...body.childNodes);
    });
  }

  function renderCapabilities(payload) {
    document.querySelectorAll('[data-capability-status-generated]').forEach((target) => {
      const body = document.createElement('tbody');
      for (const capability of payload.capabilities ?? []) {
        const row = document.createElement('tr');
        row.append(td(capability.surface));
        row.append(td(capability.implemented ? 'Implémenté' : 'Planifié'));
        row.append(td(labelCapability(capability)));
        row.append(td(capabilityAvailable(capability) ? 'Oui' : 'Non'));
        body.append(row);
      }
      target.replaceChildren(...body.childNodes);
    });
  }

  function renderSummary(payload) {
    const capabilities = payload.capabilities ?? [];
    const modules = payload.modules ?? [];
    const available = capabilities.filter((item) => capabilityAvailable(item)).length;
    const paidForSale = modules.filter((item) => item.tier !== 'FREE' && item.offer !== 'NOT_FOR_SALE').length;
    const updated = [
      payload.generated_from?.product_capabilities_updated_at,
      payload.generated_from?.commercial_catalog_updated_at,
      payload.generated_from?.commercial_pro_extension_updated_at,
    ].filter(Boolean).sort().at(-1) ?? 'inconnue';

    document.querySelectorAll('[data-product-truth-updated]').forEach((node) => { node.textContent = updated; });
    document.querySelectorAll('[data-product-truth-summary]').forEach((node) => {
      node.textContent = `${capabilities.length} capacités suivies · ${available} disponibles client · ${modules.length} modules commerciaux · ${paidForSale} offres payantes actuellement ouvrables.`;
    });
    document.querySelectorAll('[data-trial-status]').forEach((node) => {
      node.textContent = payload.commerce?.trial_enrollment_enabled
        ? `Essai Premium ${payload.commerce.trial_duration_days ?? ''} jours activé`
        : `Essai Premium ${payload.commerce?.trial_duration_days ?? ''} jours défini mais désactivé`;
    });
    document.querySelectorAll('[data-checkout-status]').forEach((node) => {
      node.textContent = payload.commerce?.checkout_enabled ? 'Checkout activé' : 'Checkout désactivé';
    });
  }

  refreshStaticCapabilityAvailability();
  window.setInterval(refreshStaticCapabilityAvailability, 1000);

  fetch(endpoint, { cache: 'no-store', credentials: 'same-origin' })
    .then((response) => {
      if (!response.ok) throw new Error(`HTTP_${response.status}`);
      return response.json();
    })
    .then((payload) => {
      window.SentinelProductTruth = payload;
      renderSummary(payload);
      renderCommercialCatalog(payload);
      renderCapabilities(payload);
      renderGlobalTruthStrip(payload);
      refreshStaticCapabilityAvailability();
      document.documentElement.dataset.productTruth = 'loaded';
      window.dispatchEvent(new CustomEvent('sentinel:product-truth', { detail: payload }));
    })
    .catch(() => {
      document.documentElement.dataset.productTruth = 'unavailable';
      document.querySelectorAll('[data-product-truth-summary]').forEach((node) => {
        node.textContent = 'Snapshot produit indisponible : consulter la roadmap et les états statiques de repli.';
      });
    });
})();
