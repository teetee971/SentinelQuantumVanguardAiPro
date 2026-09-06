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
  if (typeof value !== 'string' || value.trim() === '' || value.length > maxLength) {
    throw new Error(`INVALID_${field.toUpperCase()}`);
  }
  return value.trim();
}

function optionalString(value, field, maxLength = 2048) {
  if (value === undefined || value === null || value === '') return null;
  return requiredString(value, field, maxLength);
}

function normalizeStringArray(value, field, maxItems = 100) {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value) || value.length > maxItems) {
    throw new Error(`INVALID_${field.toUpperCase()}`);
  }
  return [...new Set(value.map((item) => requiredString(item, field, 512)))];
}

function normalizeDate(value, field) {
  if (value === undefined || value === null || value === '') return null;
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) throw new Error(`INVALID_${field.toUpperCase()}`);
  return new Date(timestamp).toISOString();
}

function normalizeForeignInfluenceRecord(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    throw new Error('INVALID_RECORD');
  }

  const channel = requiredString(input.activity?.channel, 'activity_channel', 64);
  if (!ACTIVITY_CHANNELS.has(channel)) throw new Error('INVALID_ACTIVITY_CHANNEL');

  const source = {
    publisher: requiredString(input.source?.publisher, 'source_publisher', 256),
    url: requiredString(input.source?.url, 'source_url', 2048),
    retrieved_at: normalizeDate(input.source?.retrieved_at, 'source_retrieved_at')
  };

  if (!source.retrieved_at) throw new Error('INVALID_SOURCE_RETRIEVED_AT');

  const startDate = normalizeDate(input.activity?.start_date, 'activity_start_date');
  const endDate = normalizeDate(input.activity?.end_date, 'activity_end_date');
  if (startDate && endDate && Date.parse(endDate) < Date.parse(startDate)) {
    throw new Error('INVALID_ACTIVITY_PERIOD');
  }

  return {
    record_id: requiredString(input.record_id, 'record_id', 256),
    record_type: 'DECLARED_FOREIGN_INFLUENCE',
    evidence_level: EVIDENCE_LEVELS.DECLARED_FACT,
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
  };
}

function buildInfluenceGraphEdges(record) {
  const normalized = normalizeForeignInfluenceRecord(record);
  const sourceRef = `${normalized.source.publisher}:${normalized.record_id}`;
  const evidence = {
    evidence_level: EVIDENCE_LEVELS.DECLARED_FACT,
    source_ref: sourceRef
  };

  const edges = [
    {
      from: `principal:${normalized.principal.name}`,
      relation: 'MANDATES_OR_BENEFITS_FROM',
      to: `declarant:${normalized.declarant.name}`,
      ...evidence
    }
  ];

  for (const intermediary of normalized.activity.intermediaries) {
    edges.push({
      from: `principal:${normalized.principal.name}`,
      relation: 'USES_INTERMEDIARY',
      to: `intermediary:${intermediary}`,
      ...evidence
    });
  }

  for (const target of normalized.activity.targets) {
    edges.push({
      from: `declarant:${normalized.declarant.name}`,
      relation: 'DECLARES_ACTIVITY_TOWARD',
      to: `target:${target}`,
      ...evidence
    });
  }

  for (const topic of normalized.activity.policy_topics) {
    edges.push({
      from: `declarant:${normalized.declarant.name}`,
      relation: 'DECLARES_POLICY_TOPIC',
      to: `policy:${topic}`,
      ...evidence
    });
  }

  return edges;
}

export {
  ACTIVITY_CHANNELS,
  EVIDENCE_LEVELS,
  buildInfluenceGraphEdges,
  normalizeForeignInfluenceRecord
};
