const ALLOWED_TYPES = new Set([
  'asset','identity','device','application','service','network','domain','ip',
  'certificate','vulnerability','ioc','threat_actor_hypothesis','campaign',
  'narrative','event','incident','control','policy'
]);

function createTwin() {
  return Object.freeze({ version: 1, entities: new Map(), evidence: new Map() });
}

function addEntity(twin, entity) {
  if (!twin || !entity || !ALLOWED_TYPES.has(entity.entity_type)) throw new Error('invalid_entity');
  if (!entity.entity_id || !entity.observed_at || !entity.provenance?.source_id) throw new Error('missing_provenance');
  if (entity.entity_type === 'threat_actor_hypothesis' && entity.attributes?.confirmed === true) {
    throw new Error('hypothesis_cannot_be_confirmed_by_twin');
  }
  const entities = new Map(twin.entities);
  entities.set(entity.entity_id, Object.freeze(structuredClone(entity)));
  return Object.freeze({ ...twin, entities });
}

function addEvidence(twin, evidence) {
  if (!twin || !evidence || !evidence.evidence_id) throw new Error('invalid_evidence');
  if (!['observation','inference','hypothesis'].includes(evidence.kind)) throw new Error('invalid_evidence_kind');
  const store = new Map(twin.evidence);
  store.set(evidence.evidence_id, Object.freeze(structuredClone(evidence)));
  return Object.freeze({ ...twin, evidence: store });
}

export { createTwin, addEntity, addEvidence, ALLOWED_TYPES };
