const DEFAULT_LIMITS = Object.freeze({ maxNodes: 5000, maxEdges: 20000 });

/** Pure simulation: predicts graph impact without executing any action. */
export function simulateImpact({ nodes = [], edges = [], action, targetIds = [] } = {}, limits = {}) {
  const maxNodes = Number.isInteger(limits.maxNodes) ? limits.maxNodes : DEFAULT_LIMITS.maxNodes;
  const maxEdges = Number.isInteger(limits.maxEdges) ? limits.maxEdges : DEFAULT_LIMITS.maxEdges;

  if (!Array.isArray(nodes) || !Array.isArray(edges) || !Array.isArray(targetIds) || !action) {
    return { safe: false, reason: 'INVALID_SIMULATION_INPUT', affectedNodes: [], blastRadius: 0, warnings: [] };
  }
  if (nodes.length > maxNodes || edges.length > maxEdges) {
    return { safe: false, reason: 'SIMULATION_LIMIT_EXCEEDED', affectedNodes: [], blastRadius: 0, warnings: ['BOUNDED_SIMULATION_REQUIRED'] };
  }

  const ids = new Set(nodes.map((node) => node?.id).filter((id) => typeof id === 'string'));
  if (targetIds.some((id) => !ids.has(id))) {
    return { safe: false, reason: 'UNKNOWN_TARGET', affectedNodes: [], blastRadius: 0, warnings: [] };
  }

  const adjacency = new Map([...ids].map((id) => [id, []]));
  for (const edge of edges) {
    if (ids.has(edge?.from) && ids.has(edge?.to)) adjacency.get(edge.from).push(edge.to);
  }

  const visited = new Set(targetIds);
  const queue = [...targetIds];
  while (queue.length) {
    const current = queue.shift();
    for (const next of adjacency.get(current) ?? []) {
      if (!visited.has(next)) {
        visited.add(next);
        queue.push(next);
      }
    }
  }

  const affectedNodes = [...visited];
  const criticalImpact = affectedNodes.some((id) => nodes.find((node) => node.id === id)?.critical === true);
  const warnings = [];
  if (criticalImpact) warnings.push('CRITICAL_NODE_IN_BLAST_RADIUS');
  if (affectedNodes.length > Math.max(10, nodes.length * 0.25)) warnings.push('LARGE_BLAST_RADIUS');

  return {
    safe: !criticalImpact && warnings.length === 0,
    reason: criticalImpact ? 'CRITICAL_IMPACT' : warnings.length ? 'SIMULATION_WARNINGS' : 'SIMULATION_SAFE',
    action,
    affectedNodes,
    blastRadius: affectedNodes.length,
    criticalImpact,
    warnings,
  };
}
