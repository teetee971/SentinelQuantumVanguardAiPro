function buildPropagationGraph(events) {
  const nodes = new Map();
  const edges = [];

  for (const event of events || []) {
    const entityId = event.entity?.public_id;
    if (!entityId) continue;
    nodes.set(entityId, {
      id: entityId,
      type: event.entity.type,
      platform: event.entity.platform,
      first_observed_at: nodes.get(entityId)?.first_observed_at || event.observed_at
    });

    for (const target of event.links || []) {
      const targetId = `url:${target}`;
      if (!nodes.has(targetId)) nodes.set(targetId, { id: targetId, type: 'domain_or_url' });
      edges.push({ from: entityId, to: targetId, relation: 'references', observed_at: event.observed_at });
    }

    for (const marker of event.narrative_markers || []) {
      const narrativeId = `narrative:${marker}`;
      if (!nodes.has(narrativeId)) nodes.set(narrativeId, { id: narrativeId, type: 'narrative' });
      edges.push({ from: entityId, to: narrativeId, relation: 'amplifies', observed_at: event.observed_at });
    }
  }

  return { nodes: [...nodes.values()], edges };
}

function propagationTimeline(graph) {
  return [...graph.edges]
    .filter((edge) => edge.observed_at)
    .sort((a, b) => new Date(a.observed_at) - new Date(b.observed_at));
}

export { buildPropagationGraph, propagationTimeline };
