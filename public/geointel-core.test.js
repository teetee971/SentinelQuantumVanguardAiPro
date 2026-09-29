import test from "node:test";
import assert from "node:assert/strict";
import {filterEvents,normalizeEvent,projectEquirectangular,riskScore} from "./geointel-core.js";

const sample={id:"demo-1",layer:"natural",title:"Démo",lat:16.2,lon:-61.5,severity:4,confidence:.75,occurredAt:"2026-09-29T12:00:00Z",sourceName:"DEMO",status:"unverified"};

test("normalization rejects impossible coordinates",()=>assert.throws(()=>normalizeEvent({...sample,lat:91}),/coordinates/));
test("risk score is deterministic and bounded",()=>assert.equal(riskScore(sample),60));
test("time and layer filters are enforced",()=>assert.equal(filterEvents([sample],{layers:["natural"],timeRangeDays:7,now:Date.parse("2026-09-29T13:00:00Z")}).length,1));
test("projection maps world bounds",()=>{assert.deepEqual(projectEquirectangular(90,-180,360,180),{x:0,y:0});assert.deepEqual(projectEquirectangular(-90,180,360,180),{x:360,y:180});});

test("freshness is unknown without detection proof",async()=>{const {freshnessState}=await import("./geointel-core.js");assert.equal(freshnessState(sample,{now:Date.parse("2026-09-29T13:00:00Z")}),"UNKNOWN");});
test("verified but stale evidence degrades instead of staying green",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,status:"verified",detectedAt:"2026-09-28T00:00:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"DEGRADED",freshness:"STALE"});});
test("only verified current evidence receives verified-current state",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,status:"verified",collectorState:"HEALTHY",detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:31:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"VERIFIED_CURRENT",freshness:"CURRENT"});});

test("normalization preserves detection proof canonically",()=>{const e=normalizeEvent({...sample,detectedAt:"2026-09-29T12:30:00+00:00"});assert.equal(e.detectedAt,"2026-09-29T12:30:00.000Z");});
test("normalization rejects malformed detection proof",()=>assert.throws(()=>normalizeEvent({...sample,detectedAt:"not-a-date"}),/detectedAt/));

test("provenance rejects ingestion before source detection",()=>assert.throws(()=>normalizeEvent({...sample,detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:29:59Z"}),/ingestedAt/));
test("collector health fails closed for otherwise current verified evidence",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,status:"verified",collectorState:"DOWN",detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:31:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"DEGRADED",freshness:"CURRENT"});});

test("unknown collector state cannot become verified-current",async()=>{const {evidenceState}=await import("./geointel-core.js");const e={...sample,status:"verified",detectedAt:"2026-09-29T12:30:00Z",ingestedAt:"2026-09-29T12:31:00Z"};assert.deepEqual(evidenceState(e,{now:Date.parse("2026-09-29T13:00:00Z"),ttlMs:21600000}),{trust:"DEGRADED",freshness:"CURRENT"});});
