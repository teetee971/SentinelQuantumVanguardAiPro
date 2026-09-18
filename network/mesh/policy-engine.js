export const SUBJECT_TYPES = new Set(["user","device","workload","agent"]);
export const EFFECTS = new Set(["allow","deny"]);

function boundedStrings(values, name, max = 64) {
  if (!Array.isArray(values)) throw new TypeError(`${name} must be an array`);
  if (values.length > max) throw new RangeError(`${name} exceeds ${max}`);
  return [...new Set(values.map(v => {
    if (typeof v !== "string") throw new TypeError(`${name} entries must be strings`);
    const s = v.trim();
    if (!s || s.length > 128) throw new RangeError(`${name} contains an invalid entry`);
    return s;
  }))];
}

export function normalizeSubject(subject) {
  if (!subject || typeof subject !== "object") throw new TypeError("subject required");
  if (!SUBJECT_TYPES.has(subject.type)) throw new TypeError("unsupported subject type");
  const id = String(subject.id || "").trim();
  if (!id || id.length > 256) throw new RangeError("invalid subject id");
  return {
    type: subject.type,
    id,
    groups: boundedStrings(subject.groups || [], "groups"),
    tags: boundedStrings(subject.tags || [], "tags"),
    deviceTrust: String(subject.deviceTrust || "unknown"),
  };
}

export function normalizeResource(resource) {
  if (!resource || typeof resource !== "object") throw new TypeError("resource required");
  const id = String(resource.id || "").trim();
  if (!id || id.length > 256) throw new RangeError("invalid resource id");
  return {
    id,
    tags: boundedStrings(resource.tags || [], "resource.tags"),
    environment: String(resource.environment || "unknown"),
  };
}

function intersects(required, actual) {
  return required.length === 0 || required.some(v => actual.includes(v));
}

function ruleMatches(rule, subject, resource, action) {
  if (rule.actions?.length && !rule.actions.includes(action)) return false;
  if (rule.subjectTypes?.length && !rule.subjectTypes.includes(subject.type)) return false;
  if (rule.subjectIds?.length && !rule.subjectIds.includes(subject.id)) return false;
  if (!intersects(rule.groups || [], subject.groups)) return false;
  if (!intersects(rule.subjectTags || [], subject.tags)) return false;
  if (!intersects(rule.resourceTags || [], resource.tags)) return false;
  if (rule.environments?.length && !rule.environments.includes(resource.environment)) return false;
  if (rule.deviceTrust?.length && !rule.deviceTrust.includes(subject.deviceTrust)) return false;
  return true;
}

export function evaluateAccess({ subject, resource, action, rules }) {
  const s = normalizeSubject(subject);
  const r = normalizeResource(resource);
  if (typeof action !== "string" || !action.trim() || action.length > 128) {
    throw new RangeError("invalid action");
  }
  if (!Array.isArray(rules)) throw new TypeError("rules must be an array");
  if (rules.length > 1000) throw new RangeError("too many rules");

  let allow = null;
  for (const raw of rules) {
    if (!raw || typeof raw !== "object") continue;
    if (!EFFECTS.has(raw.effect)) continue;
    const rule = {
      id: String(raw.id || "unnamed"),
      effect: raw.effect,
      actions: boundedStrings(raw.actions || [], "actions"),
      subjectTypes: boundedStrings(raw.subjectTypes || [], "subjectTypes"),
      subjectIds: boundedStrings(raw.subjectIds || [], "subjectIds"),
      groups: boundedStrings(raw.groups || [], "groups"),
      subjectTags: boundedStrings(raw.subjectTags || [], "subjectTags"),
      resourceTags: boundedStrings(raw.resourceTags || [], "resourceTags"),
      environments: boundedStrings(raw.environments || [], "environments"),
      deviceTrust: boundedStrings(raw.deviceTrust || [], "deviceTrust"),
    };
    if (!ruleMatches(rule, s, r, action)) continue;
    if (rule.effect === "deny") {
      return { allowed: false, reason: "EXPLICIT_DENY", ruleId: rule.id };
    }
    allow ??= rule;
  }
  if (allow) return { allowed: true, reason: "EXPLICIT_ALLOW", ruleId: allow.id };
  return { allowed: false, reason: "DEFAULT_DENY", ruleId: null };
}
