import test from "node:test";
import assert from "node:assert/strict";
import {filterEvents,normalizeEvent,projectEquirectangular,riskScore} from "./geointel-core.js";

const sample={id:"demo-1",layer:"natural",title:"Démo",lat:16.2,lon:-61.5,severity:4,confidence:.75,occurredAt:"2026-09-29T12:00:00Z",sourceName:"DEMO",status:"unverified"};

test("normalization rejects impossible coordinates",()=>assert.throws(()=>normalizeEvent({...sample,lat:91}),/coordinates/));
test("risk score is deterministic and bounded",()=>assert.equal(riskScore(sample),60));
test("time and layer filters are enforced",()=>assert.equal(filterEvents([sample],{layers:["natural"],timeRangeDays:7,now:Date.parse("2026-09-29T13:00:00Z")}).length,1));
test("projection maps world bounds",()=>{assert.deepEqual(projectEquirectangular(90,-180,360,180),{x:0,y:0});assert.deepEqual(projectEquirectangular(-90,180,360,180),{x:360,y:180});});

test("freshness is unknown without detection proof",async()=>{const {freshnessState}=await import("./geointel-core.js");assert.equal(freshnessState(sample,{now:Date.parse("2026-09-29T13:00:00Z")}),"UNKNOWN");});
test("verified but stale evidence degrades instead of staying green",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,sourceEventId:"demo-1",status:"verified",collectorState:"HEALTHY",occurredAt:"2026-09-28T00:00:00Z",detectedAt:"2026-09-28T00:01:00Z",ingestedAt:"2026-09-28T00:02:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"DEGRADED",freshness:"STALE"});});
test("only verified current evidence with provenance receives verified-current state",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,sourceEventId:"demo-1",status:"verified",collectorState:"HEALTHY",detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:31:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"SOURCE_VERIFIED_CURRENT",freshness:"CURRENT"});});
test("verified current evidence without source identity degrades",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,status:"verified",collectorState:"HEALTHY",detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:31:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"DEGRADED",freshness:"CURRENT"});});

test("normalization preserves detection proof canonically",()=>{const e=normalizeEvent({...sample,detectedAt:"2026-09-29T12:30:00+00:00"});assert.equal(e.detectedAt,"2026-09-29T12:30:00.000Z");});
test("normalization rejects malformed detection proof",()=>assert.throws(()=>normalizeEvent({...sample,detectedAt:"not-a-date"}),/detectedAt/));

test("provenance rejects ingestion before source detection",()=>assert.throws(()=>normalizeEvent({...sample,detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:29:59Z"}),/ingestedAt/));
test("collector health fails closed for otherwise current verified evidence",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,sourceEventId:"demo-1",status:"verified",collectorState:"DOWN",detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:31:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"DEGRADED",freshness:"CURRENT"});});

test("unknown collector state cannot become verified-current",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,sourceEventId:"demo-1",status:"verified",detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:31:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"DEGRADED",freshness:"CURRENT"});});

test("provenance key namespaces source identifiers",async()=>{const {provenanceKey}=await import("./geointel-core.js");const e={...sample,sourceName:"Provider A",sourceEventId:"42"};assert.equal(provenanceKey(e),"provider a::42");});
test("deduplication keeps newest ingestion for same source event",async()=>{const {deduplicateEvents}=await import("./geointel-core.js");const a={...sample,sourceName:"Provider A",sourceEventId:"42",detectedAt:"2026-09-29T12:00:00Z",ingestedAt:"2026-09-29T12:01:00Z"};const b={...a,title:"Updated",ingestedAt:"2026-09-29T12:02:00Z"};const out=deduplicateEvents([a,b]);assert.equal(out.length,1);assert.equal(out[0].title,"Updated");});
test("same sourceEventId from different providers does not collide",async()=>{const {deduplicateEvents}=await import("./geointel-core.js");const a={...sample,sourceName:"Provider A",sourceEventId:"42"};const b={...sample,id:"demo-2",sourceName:"Provider B",sourceEventId:"42"};assert.equal(deduplicateEvents([a,b]).length,2);});

test("events without source identity are never heuristically merged",async()=>{const {deduplicateEvents}=await import("./geointel-core.js");const a={...sample};const b={...sample,id:"demo-2"};assert.equal(deduplicateEvents([a,b]).length,2);});

test("normalization rejects empty event identity",()=>assert.throws(()=>normalizeEvent({...sample,id:"   "}),/id required/));
test("normalization rejects empty event title",()=>assert.throws(()=>normalizeEvent({...sample,title:"   "}),/title required/));
test("filter rejects invalid time windows",()=>assert.throws(()=>filterEvents([sample],{timeRangeDays:0,now:Date.parse("2026-09-29T13:00:00Z")}),/positive/));
test("future ingestion makes freshness unknown",async()=>{const {freshnessState}=await import("./geointel-core.js");const e={...sample,detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T14:00:00Z"};assert.equal(freshnessState(e,{now:Date.parse("2026-09-29T13:00:00Z")}),"UNKNOWN");});

test("normalization rejects detection before occurrence",()=>assert.throws(()=>normalizeEvent({...sample,occurredAt:"2026-09-29T12:00:00Z",detectedAt:"2026-09-29T11:59:59Z"}),/detectedAt cannot precede occurredAt/));
test("filter rejects unsupported layers instead of silently returning empty data",()=>assert.throws(()=>filterEvents([sample],{layers:["not-a-layer"],timeRangeDays:7,now:Date.parse("2026-09-29T13:00:00Z")}),/supported GeoIntel layers/));
test("filter rejects non-array layer contracts",()=>assert.throws(()=>filterEvents([sample],{layers:"natural",timeRangeDays:7,now:Date.parse("2026-09-29T13:00:00Z")}),/supported GeoIntel layers/));

test("normalization rejects null coordinates instead of coercing them to zero",()=>assert.throws(()=>normalizeEvent({...sample,lat:null,lon:null}),/coordinates/));
test("normalization rejects numeric-string coordinates at the normalized contract boundary",()=>assert.throws(()=>normalizeEvent({...sample,lat:"16.2"}),/coordinates/));
test("null source confidence remains unknown instead of being invented",()=>{const e=normalizeEvent({...sample,confidence:null});assert.equal(e.confidence,null);assert.equal(riskScore(e),null);});
test("non-numeric confidence is rejected",()=>assert.throws(()=>normalizeEvent({...sample,confidence:"0.8"}),/Confidence/));
test("normalization requires an occurrence timestamp instead of treating null as Unix epoch",()=>assert.throws(()=>normalizeEvent({...sample,occurredAt:null}),/occurredAt required/));

test("object-shaped provenance cannot be coerced into a trusted source identity",async()=>{const {provenanceKey}=await import("./geointel-core.js");const e={...sample,sourceName:{name:"Provider"},sourceEventId:{id:"42"}};assert.equal(provenanceKey(e),null);});
test("numeric zero source ids remain stable primitive provenance",async()=>{const {provenanceKey}=await import("./geointel-core.js");const e={...sample,sourceName:"Provider A",sourceEventId:0};assert.equal(provenanceKey(e),"provider a::0");});
