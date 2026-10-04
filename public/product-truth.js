(() => {
  const endpoint = '/public/product-truth.generated.json';

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

  const labelCapability = (capability) => {
    if (capability.customer_available) return 'Disponible';
    if (capability.state === 'PLANNED') return 'Planifié';
    if (capability.state === 'INFRASTRUCTURE') return 'Infrastructure requise';
    return 'En validation';
  };

  function td(value) {
    const cell = document.createElement('td');
    cell.textContent = text(value);
    return cell;
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
      target.replaceChildren(body);
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
        row.append(td(capability.customer_available ? 'Oui' : 'Non'));
        body.append(row);
      }
      target.replaceChildren(body);
    });
  }

  function renderSummary(payload) {
    const capabilities = payload.capabilities ?? [];
    const modules = payload.modules ?? [];
    const available = capabilities.filter((item) => item.customer_available).length;
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

  fetch(endpoint, { cache: 'no-store', credentials: 'same-origin' })
    .then((response) => {
      if (!response.ok) throw new Error(`HTTP_${response.status}`);
      return response.json();
    })
    .then((payload) => {
      renderSummary(payload);
      renderCommercialCatalog(payload);
      renderCapabilities(payload);
      document.documentElement.dataset.productTruth = 'loaded';
    })
    .catch(() => {
      document.documentElement.dataset.productTruth = 'unavailable';
      document.querySelectorAll('[data-product-truth-summary]').forEach((node) => {
        node.textContent = 'Snapshot produit indisponible : consulter la roadmap et les états statiques de repli.';
      });
    });
})();
