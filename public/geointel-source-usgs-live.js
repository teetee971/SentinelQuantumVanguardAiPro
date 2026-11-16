import {ingestRecords} from "./geointel-adapter.js";
import {normalizeEvent} from "./geointel-core.js";
import {fetchJson} from "./geointel-transport.js";
import {extractUsgsFeatures,usgsEarthquakeAdapter} from "./geointel-source-usgs.js";

export const MAX_USGS_FEATURES=10000;

export const USGS_FEED_BY_DAYS=Object.freeze({
  1:"https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson",
  7:"https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_week.geojson",
  30:"https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_month.geojson"
});

export async function loadUsgsEarthquakes({fetchImpl,now=Date.now(),timeoutMs=8000,timeRangeDays=1}={}) {
  try {
    if (!Number.isFinite(now)) throw new Error("GeoIntel acquisition time must be finite");
    const feedUrl=USGS_FEED_BY_DAYS[timeRangeDays];
    if (!feedUrl) throw new Error("Unsupported USGS GeoIntel time range");
    const payload=await fetchJson(feedUrl,{fetchImpl,timeoutMs});
    const generated=payload?.metadata?.generated;
    const declaredCount=payload?.metadata?.count;
    if (declaredCount!=null && (!Number.isInteger(declaredCount)||declaredCount<0)) throw new Error("USGS feed count invalid");
    if (typeof generated!=="number"||!Number.isFinite(generated)) throw new Error("USGS feed generation timestamp required");
    if (generated>now+5*60*1000) throw new Error("USGS feed generation timestamp is in the future");
    const features=extractUsgsFeatures(payload);
    if (features.length>MAX_USGS_FEATURES) throw new Error("USGS feed exceeds safe event limit");
    if (declaredCount!=null && declaredCount!==features.length) throw new Error("USGS feed count mismatch");
    const result=ingestRecords(usgsEarthquakeAdapter,features,{ingestedAt:new Date(now).toISOString()});
    const feedAgeMs=now-generated;
    const collectorState=feedAgeMs>15*60*1000&&result.collectorState==="HEALTHY"?"DEGRADED":result.collectorState;
    const accepted=Object.freeze(result.accepted.map(event=>normalizeEvent({...event,collectorState})));
    return Object.freeze({...result,collectorState,accepted,feedGeneratedAt:new Date(generated).toISOString()});
  } catch (error) {
    return Object.freeze({sourceName:usgsEarthquakeAdapter.name,collectorState:"DOWN",accepted:Object.freeze([]),rejected:Object.freeze([{reason:error instanceof Error?error.message:"USGS acquisition failed"}]),feedGeneratedAt:null});
  }
}
