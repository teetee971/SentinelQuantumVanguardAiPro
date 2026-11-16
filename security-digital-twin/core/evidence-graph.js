const RELATIONS = new Set(['supports','contradicts','derived_from','observes','affects','belongs_to','mitigates','targets']);

function createEvidenceGraph() {
  return { nodes: new Map(), edges: [] };
}

function addNode(graph, node) {
  if (!node?.id || !node?.kind) throw new Error('invalid_graph_node');
  const nodes = new Map(graph.nodes);
  nodes.set(node.id, Object.freeze(structuredClone(node)));
  return { ...graph, nodes, edges: [...graph.edges] };
}

function addEdge(graph, from, relation, to, confidence = 1) {
  if (!graph.nodes.has(from) || !graph.nodes.has(to)) throw new Error('unknown_graph_node');
  if (!RELATIONS.has(relation) || !Number.isFinite(confidence) || confidence < 0 || confidence > 1) throw new Error('invalid_graph_edge');
  const edge = Object.freeze({ from, relation, to, confidence });
  return { ...graph, nodes: new Map(graph.nodes), edges: [...graph.edges, edge] };
}

function traceEvidence(graph, nodeId, maxDepth = 5) {
  const result = new Set([nodeId]);
  let frontier = new Set([nodeId]);
  for (let depth = 0; depth < maxDepth && frontier.size; depth += 1) {
    const next = new Set();
    for (const edge of graph.edges) {
      if (frontier.has(edge.from) || frontier.has(edge.to)) {
        const candidate = frontier.has(edge.from) ? edge.to : edge.from;
        if (!result.has(candidate)) { result.add(candidate); next.add(candidate); }
      }
    }
    frontier = next;
  }
  return [...result];
}

export { createEvidenceGraph, addNode, addEdge, traceEvidence, RELATIONS };
