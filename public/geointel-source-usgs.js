import {createAdapter} from "./geointel-adapter.js";

export const USGS_EARTHQUAKE_SOURCE="USGS Earthquake Hazards Program";

function severityFromMagnitude(magnitude) {
  if (typeof magnitude!=="number"||!Number.isFinite(magnitude)) throw new RangeError("USGS magnitude required");
  const mag=magnitude;
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
    const lon=coordinates[0],lat=coordinates[1];
    if (typeof lat!=="number"||!Number.isFinite(lat)||typeof lon!=="number"||!Number.isFinite(lon)) throw new RangeError("USGS finite numeric coordinates required");
    const p=feature.properties||{};
    const sourceEventId=feature.id==null?"":String(feature.id).trim();
    if (!sourceEventId) throw new RangeError("USGS feature id required");
    const occurred=p.time,updated=p.updated;
    if (typeof occurred!=="number"||!Number.isFinite(occurred)||typeof updated!=="number"||!Number.isFinite(updated)) throw new RangeError("USGS event timestamps required");
    const magnitude=p.mag;
    if (typeof magnitude!=="number"||!Number.isFinite(magnitude)) throw new RangeError("USGS magnitude required");
    return {
      id:"usgs:"+sourceEventId,
      sourceEventId,
      layer:"natural",
      title:`Séisme M${Number.isFinite(magnitude)?magnitude.toFixed(1):"?"} — ${String(p.place||"localisation non renseignée")}`,
      lat,lon,
      severity:severityFromMagnitude(magnitude),
      confidence:null,
      occurredAt:new Date(occurred).toISOString(),
      detectedAt:new Date(updated).toISOString(),
      sourceUrl:(()=>{if(typeof p.url!=="string") return null;const url=new URL(p.url);if(url.protocol!=="https:"||url.hostname!=="earthquake.usgs.gov") throw new RangeError("USGS source URL must use official HTTPS host");return url.href;})(),
      status:p.status==="reviewed"?"verified":"unverified"
    };
  }
});

export function extractUsgsFeatures(payload) {
  if (!payload||payload.type!=="FeatureCollection"||!Array.isArray(payload.features)) throw new TypeError("USGS FeatureCollection required");
  return payload.features;
}
