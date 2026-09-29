import {ingestRecords} from "./geointel-adapter.js";
import {normalizeEvent} from "./geointel-core.js";
import {fetchJson} from "./geointel-transport.js";
import {extractUsgsFeatures,usgsEarthquakeAdapter} from "./geointel-source-usgs.js";

export const USGS_ALL_HOUR_GEOJSON="https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson";

export async function loadUsgsEarthquakes({fetchImpl,now=Date.now(),timeoutMs=8000}={}) {
  try {
    const payload=await fetchJson(USGS_ALL_HOUR_GEOJSON,{fetchImpl,timeoutMs});
    const generated=Number(payload?.metadata?.generated);
    const declaredCount=payload?.metadata?.count;
    if (declaredCount!=null && (!Number.isInteger(declaredCount)||declaredCount<0)) throw new Error("USGS feed count invalid");
    if (!Number.isFinite(generated)) throw new Error("USGS feed generation timestamp required");
    if (generated>now+5*60*1000) throw new Error("USGS feed generation timestamp is in the future");
    const features=extractUsgsFeatures(payload);
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
