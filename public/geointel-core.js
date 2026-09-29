export const GEOINTEL_LAYERS = Object.freeze(["conflicts","hotspots","sanctions","weather","outages","natural"]);

export function normalizeEvent(raw) {
  if (!raw || typeof raw !== "object") throw new TypeError("GeoIntel event required");
  if (!GEOINTEL_LAYERS.includes(raw.layer)) throw new RangeError("Unsupported GeoIntel layer");
  const lat=Number(raw.lat), lon=Number(raw.lon), severity=Number(raw.severity), confidence=Number(raw.confidence);
  if (!Number.isFinite(lat)||lat < -90||lat > 90||!Number.isFinite(lon)||lon < -180||lon > 180) throw new RangeError("Invalid coordinates");
  if (!Number.isFinite(severity)||severity < 1||severity > 5) throw new RangeError("Severity must be 1..5");
  if (!Number.isFinite(confidence)||confidence < 0||confidence > 1) throw new RangeError("Confidence must be 0..1");
  const occurredAt=new Date(raw.occurredAt);
  if (Number.isNaN(occurredAt.getTime())) throw new RangeError("Invalid occurredAt");
  return Object.freeze({id:String(raw.id),layer:raw.layer,title:String(raw.title),lat,lon,severity,confidence,occurredAt:occurredAt.toISOString(),sourceName:String(raw.sourceName||"Source non renseignée"),sourceUrl:raw.sourceUrl?String(raw.sourceUrl):null,status:raw.status==="verified"?"verified":"unverified"});
}

export function riskScore(event) {
  const e=normalizeEvent(event);
  return Math.round((e.severity/5)*e.confidence*100);
}

export function filterEvents(events,{layers=GEOINTEL_LAYERS,timeRangeDays=7,now=Date.now()}={}) {
  const allowed=new Set(layers);
  const cutoff=now-(timeRangeDays*86400000);
  return events.map(normalizeEvent).filter(e=>allowed.has(e.layer)&&Date.parse(e.occurredAt)>=cutoff&&Date.parse(e.occurredAt)<=now);
}

export function projectEquirectangular(lat,lon,width,height) {
  return {x:((lon+180)/360)*width,y:((90-lat)/180)*height};
}
