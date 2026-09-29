import test from "node:test";
import assert from "node:assert/strict";
import {filterEvents,normalizeEvent,projectEquirectangular,riskScore} from "./geointel-core.js";

const sample={id:"demo-1",layer:"natural",title:"Démo",lat:16.2,lon:-61.5,severity:4,confidence:.75,occurredAt:"2026-09-29T12:00:00Z",sourceName:"DEMO",status:"unverified"};

test("normalization rejects impossible coordinates",()=>assert.throws(()=>normalizeEvent({...sample,lat:91}),/coordinates/));
test("risk score is deterministic and bounded",()=>assert.equal(riskScore(sample),60));
test("time and layer filters are enforced",()=>assert.equal(filterEvents([sample],{layers:["natural"],timeRangeDays:7,now:Date.parse("2026-09-29T13:00:00Z")}).length,1));
test("projection maps world bounds",()=>{assert.deepEqual(projectEquirectangular(90,-180,360,180),{x:0,y:0});assert.deepEqual(projectEquirectangular(-90,180,360,180),{x:360,y:180});});
