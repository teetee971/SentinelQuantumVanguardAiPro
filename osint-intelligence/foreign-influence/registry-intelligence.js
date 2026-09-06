const EVIDENCE_LEVELS = Object.freeze({
  DECLARED_FACT: 'DECLARED_FACT',
  PUBLIC_SOURCE_OBSERVATION: 'PUBLIC_SOURCE_OBSERVATION',
  CORRELATION: 'CORRELATION',
  ANALYTICAL_INFERENCE: 'ANALYTICAL_INFERENCE',
  CONFIRMED_BY_AUTHORITY: 'CONFIRMED_BY_AUTHORITY'
});

const ACTIVITY_CHANNELS = new Set([
  'PUBLIC_OFFICIAL_CONTACT',
  'PUBLIC_COMMUNICATION',
  'FUNDING_OR_SUPPORT',
  'OTHER_DECLARED_ACTIVITY'
]);

function requiredString(value, field, maxLength = 512) {
  if (typeof value !== 'string' || value.trim() === '' || value.length > maxLength || /[\u0000-\u001f\u007f-\u009f]/.test(value)) {
    throw new TypeError(`INVALID_${field.toUpperCase()}`);
  }
  return value.trim();
}

function optionalString(value, field, maxLength = 2048) {
  if (value === undefined || value === null || value === '') return null;
  return requiredString(value, field, maxLength);
}

function normalizeStringArray(value, field, maxItems = 100) {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value) || value.length > maxItems) throw new TypeError(`INVALID_${field.toUpperCase()}`);
  return [...new Set(value.map((item) => requiredString(item, field, 512)))];
}

function normalizeDate(value, field) {
  if (value === undefined || value === null || value === '') return null;
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value)) {
    throw new TypeError(`INVALID_${field.toUpperCase()}`);
  }
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) throw new TypeError(`INVALID_${field.toUpperCase()}`);
  return new Date(timestamp).toISOString();
}

function normalizeSourceUrl(value) {
  const text = requiredString(value, 'source_url', 2048);
  let url;
  try { url = new URL(text); } catch { throw new TypeError('INVALID_SOURCE_URL'); }
  if (url.protocol !== 'https:' || url.username || url.password) throw new TypeError('INVALID_SOURCE_URL');
  return url.href;
}

function deepFreeze(value) {
  if (!value || typeof value !== 'object' || Object.isFrozen(value)) return value;
  for (const child of Object.values(value)) deepFreeze(child);
  return Object.freeze(value);
}

function normalizeForeignInfluenceRecord(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new TypeError('INVALID_RECORD');

  const channel = requiredString(input.activity?.channel, 'activity_channel', 64);
  if (!ACTIVITY_CHANNELS.has(channel)) throw new TypeError('INVALID_ACTIVITY_CHANNEL');

  const source = {
    publisher: requiredString(input.source?.publisher, 'source_publisher', 256),
    url: normalizeSourceUrl(input.source?.url),
    retrieved_at: normalizeDate(input.source?.retrieved_at, 'source_retrieved_at')
  };
  if (!source.retrieved_at) throw new TypeError('INVALID_SOURCE_RETRIEVED_AT');

  const startDate = normalizeDate(input.activity?.start_date, 'activity_start_date');
  const endDate = normalizeDate(input.activity?.end_date, 'activity_end_date');
  if (startDate && endDate && Date.parse(endDate) < Date.parse(startDate)) throw new TypeError('INVALID_ACTIVITY_PERIOD');

  return deepFreeze({
    record_id: requiredString(input.record_id, 'record_id', 256),
    record_type: 'FOREIGN_INFLUENCE_REGISTRY_OBSERVATION',
    evidence_level: EVIDENCE_LEVELS.PUBLIC_SOURCE_OBSERVATION,
    source_verification: 'UNVERIFIED',
    attribution_status: 'NOT_ASSESSED',
    interference_status: 'NOT_ASSESSED',
    source,
    declarant: {
      name: requiredString(input.declarant?.name, 'declarant_name', 512),
      identifier: optionalString(input.declarant?.identifier, 'declarant_identifier', 256)
    },
    principal: {
      name: requiredString(input.principal?.name, 'principal_name', 512),
      jurisdiction: optionalString(input.principal?.jurisdiction, 'principal_jurisdiction', 256)
    },
    activity: {
      channel,
      objective: optionalString(input.activity?.objective, 'activity_objective'),
      start_date: startDate,
      end_date: endDate,
      targets: normalizeStringArray(input.activity?.targets, 'activity_targets'),
      policy_topics: normalizeStringArray(input.activity?.policy_topics, 'activity_policy_topics'),
      intermediaries: normalizeStringArray(input.activity?.intermediaries, 'activity_intermediaries')
    }
  });
}

function sourceReference(record) {
  return JSON.stringify([
    record.source.publisher,
    record.source.url,
    record.record_id,
    record.source.retrieved_at
  ]);
}

function entityReference(role, label, sourceRef) {
  return JSON.stringify([role, sourceRef, label]);
}

function buildInfluenceGraphEdges(record) {
  const normalized = normalizeForeignInfluenceRecord(record);
  const sourceRef = sourceReference(normalized);
  const evidence = {
    evidence_level: EVIDENCE_LEVELS.PUBLIC_SOURCE_OBSERVATION,
    source_verification: 'UNVERIFIED',
    source_ref: sourceRef,
    attribution_status: 'NOT_ASSESSED',
    interference_status: 'NOT_ASSESSED'
  };

  const principal = entityReference('principal', normalized.principal.name, sourceRef);
  const declarant = entityReference('declarant', normalized.declarant.name, sourceRef);
  const edges = [{
    from: principal,
    from_label: normalized.principal.name,
    relation: 'DECLARES_FOREIGN_PRINCIPAL_RELATIONSHIP',
    to: declarant,
    to_label: normalized.declarant.name,
    ...evidence
  }];

  for (const intermediary of normalized.activity.intermediaries) {
    edges.push({
      from: principal,
      from_label: normalized.principal.name,
      relation: 'DECLARES_INTERMEDIARY',
      to: entityReference('intermediary', intermediary, sourceRef),
      to_label: intermediary,
      ...evidence
    });
  }
  for (const target of normalized.activity.targets) {
    edges.push({
      from: declarant,
      from_label: normalized.declarant.name,
      relation: 'DECLARES_ACTIVITY_TOWARD',
      to: entityReference('target', target, sourceRef),
      to_label: target,
      ...evidence
    });
  }
  for (const topic of normalized.activity.policy_topics) {
    edges.push({
      from: declarant,
      from_label: normalized.declarant.name,
      relation: 'DECLARES_POLICY_TOPIC',
      to: entityReference('policy', topic, sourceRef),
      to_label: topic,
      ...evidence
    });
  }

  return deepFreeze(edges);
}

export {
  ACTIVITY_CHANNELS,
  EVIDENCE_LEVELS,
  buildInfluenceGraphEdges,
  normalizeForeignInfluenceRecord
};
