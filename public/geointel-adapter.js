import {normalizeEvent} from "./geointel-core.js";

export const CollectorState=Object.freeze({HEALTHY:"HEALTHY",DEGRADED:"DEGRADED",DOWN:"DOWN"});

export function createAdapter({name,mapRecord}) {
  if (typeof name!=="string"||!name.trim()) throw new TypeError("Adapter name required");
  if (typeof mapRecord!=="function") throw new TypeError("mapRecord required");
  return Object.freeze({name:name.trim(),mapRecord});
}

export function ingestRecords(adapter,records,{ingestedAt=new Date().toISOString()}={}) {
  if (!adapter||typeof adapter.mapRecord!=="function") throw new TypeError("Valid adapter required");
  if (!Array.isArray(records)) throw new TypeError("records must be an array");
  const accepted=[],rejected=[];
  for (const record of records) {
    try {
      const mapped=adapter.mapRecord(record);
      accepted.push(normalizeEvent({...mapped,sourceName:adapter.name,ingestedAt:mapped.ingestedAt||ingestedAt,collectorState:"HEALTHY"}));
    } catch (error) {
      rejected.push(Object.freeze({reason:error instanceof Error?error.message:"Unknown mapping error"}));
    }
  }
  const collectorState=rejected.length===0?CollectorState.HEALTHY:(accepted.length===0?CollectorState.DOWN:CollectorState.DEGRADED);
  const finalizedAccepted=accepted.map(event=>normalizeEvent({...event,collectorState}));
  return Object.freeze({sourceName:adapter.name,collectorState,accepted:Object.freeze(finalizedAccepted),rejected:Object.freeze(rejected)});
}
