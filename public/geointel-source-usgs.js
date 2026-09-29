import {createAdapter} from "./geointel-adapter.js";

export const USGS_EARTHQUAKE_SOURCE="USGS Earthquake Hazards Program";

function severityFromMagnitude(magnitude) {
  const mag=Number(magnitude);
  if (!Number.isFinite(mag)) throw new RangeError("USGS magnitude required");
  if (mag>=7) return 5;
  if (mag>=6) return 4;
  if (mag>=5) return 3;
  if (mag>=4) return 2;
  return 1;
}

export const usgsEarthquakeAdapter=createAdapter({
  name:USGS_EARTHQUAKE_SOURCE,
  mapRecord(feature) {
    if (!feature||feature.type!=="Feature") throw new TypeError("USGS GeoJSON feature required");
    const coordinates=feature.geometry?.coordinates;
    if (feature.geometry?.type!=="Point"||!Array.isArray(coordinates)||coordinates.length<2) throw new RangeError("USGS point coordinates required");
    const p=feature.properties||{};
    const occurred=Number(p.time),updated=Number(p.updated);
    if (!Number.isFinite(occurred)||!Number.isFinite(updated)) throw new RangeError("USGS event timestamps required");
    const magnitude=Number(p.mag);
    return {
      id:"usgs:"+String(feature.id||"").trim(),
      sourceEventId:String(feature.id||"").trim(),
      layer:"natural",
      title:`Séisme M${Number.isFinite(magnitude)?magnitude.toFixed(1):"?"} — ${String(p.place||"localisation non renseignée")}`,
      lat:Number(coordinates[1]),lon:Number(coordinates[0]),
      severity:severityFromMagnitude(magnitude),
      confidence:p.status==="reviewed"?1:.85,
      occurredAt:new Date(occurred).toISOString(),
      detectedAt:new Date(updated).toISOString(),
      sourceUrl:typeof p.url==="string"?p.url:null,
      status:p.status==="reviewed"?"verified":"unverified"
    };
  }
});

export function extractUsgsFeatures(payload) {
  if (!payload||payload.type!=="FeatureCollection"||!Array.isArray(payload.features)) throw new TypeError("USGS FeatureCollection required");
  return payload.features;
}
