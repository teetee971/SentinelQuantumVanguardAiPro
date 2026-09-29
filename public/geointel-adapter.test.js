import test from "node:test";
import assert from "node:assert/strict";
import {createAdapter,ingestRecords} from "./geointel-adapter.js";

const adapter=createAdapter({name:"Fixture Provider",mapRecord:r=>({id:r.id,sourceEventId:r.id,layer:"natural",title:r.title,lat:r.lat,lon:r.lon,severity:2,confidence:.9,occurredAt:r.time,detectedAt:r.time,status:"verified"})});
const options={ingestedAt:"2026-09-29T13:00:00Z"};

test("adapter accepts valid records with explicit provenance",()=>{const out=ingestRecords(adapter,[{id:"1",title:"Fixture",lat:16.2,lon:-61.5,time:"2026-09-29T12:00:00Z"}],options);assert.equal(out.collectorState,"HEALTHY");assert.equal(out.accepted[0].sourceName,"Fixture Provider");});
test("one malformed record degrades source and every accepted record",()=>{const out=ingestRecords(adapter,[{id:"1",title:"Good",lat:16.2,lon:-61.5,time:"2026-09-29T12:00:00Z"},{id:"2",title:"Bad",lat:999,lon:0,time:"2026-09-29T12:00:00Z"}],options);assert.equal(out.collectorState,"DEGRADED");assert.equal(out.accepted.length,1);assert.equal(out.accepted[0].collectorState,"DEGRADED");assert.equal(out.rejected.length,1);});
test("all malformed records mark collector down instead of reporting empty healthy feed",()=>{const out=ingestRecords(adapter,[{id:"bad",title:"Bad",lat:999,lon:0,time:"2026-09-29T12:00:00Z"}],options);assert.equal(out.collectorState,"DOWN");assert.equal(out.accepted.length,0);});
