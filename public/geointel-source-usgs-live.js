import {ingestRecords} from "./geointel-adapter.js";
import {fetchJson} from "./geointel-transport.js";
import {extractUsgsFeatures,usgsEarthquakeAdapter} from "./geointel-source-usgs.js";

export const USGS_ALL_HOUR_GEOJSON="https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson";

export async function loadUsgsEarthquakes({fetchImpl,now=Date.now(),timeoutMs=8000}={}) {
  try {
    const payload=await fetchJson(USGS_ALL_HOUR_GEOJSON,{fetchImpl,timeoutMs});
    const generated=Number(payload?.metadata?.generated);
    if (!Number.isFinite(generated)) throw new Error("USGS feed generation timestamp required");
    if (generated>now+5*60*1000) throw new Error("USGS feed generation timestamp is in the future");
    const result=ingestRecords(usgsEarthquakeAdapter,extractUsgsFeatures(payload),{ingestedAt:new Date(now).toISOString()});
    return Object.freeze({...result,feedGeneratedAt:new Date(generated).toISOString()});
  } catch (error) {
    return Object.freeze({sourceName:usgsEarthquakeAdapter.name,collectorState:"DOWN",accepted:Object.freeze([]),rejected:Object.freeze([{reason:error instanceof Error?error.message:"USGS acquisition failed"}]),feedGeneratedAt:null});
  }
}
