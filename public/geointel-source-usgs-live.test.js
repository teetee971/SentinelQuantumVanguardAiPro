import test from "node:test";
import assert from "node:assert/strict";
import {fetchJson} from "./geointel-transport.js";
import {loadUsgsEarthquakes} from "./geointel-source-usgs-live.js";

const headers={get:()=> "application/geo+json"};
const feature={type:"Feature",id:"fixture",properties:{mag:4.5,place:"Fixture",time:Date.parse("2026-09-29T12:00:00Z"),updated:Date.parse("2026-09-29T12:01:00Z"),status:"reviewed",url:"https://earthquake.usgs.gov/"},geometry:{type:"Point",coordinates:[-61.5,16.2,10]}};

test("transport rejects non-success HTTP",()=>assert.rejects(()=>fetchJson("x",{fetchImpl:async()=>({ok:false,status:503,headers})}),/HTTP 503/));
test("transport rejects non-JSON content",()=>assert.rejects(()=>fetchJson("x",{fetchImpl:async()=>({ok:true,status:200,headers:{get:()=>"text/html"},json:async()=>({})})}),/Expected JSON/));
test("USGS acquisition returns healthy validated records",async()=>{const fetchImpl=async()=>({ok:true,status:200,headers,json:async()=>({type:"FeatureCollection",metadata:{generated:Date.parse("2026-09-29T12:02:00Z")},features:[feature]})});const out=await loadUsgsEarthquakes({fetchImpl,now:Date.parse("2026-09-29T12:03:00Z")});assert.equal(out.collectorState,"HEALTHY");assert.equal(out.accepted.length,1);});
test("USGS acquisition fails closed on invalid payload",async()=>{const fetchImpl=async()=>({ok:true,status:200,headers,json:async()=>({features:[]})});const out=await loadUsgsEarthquakes({fetchImpl,now:Date.parse("2026-09-29T12:03:00Z")});assert.equal(out.collectorState,"DOWN");assert.equal(out.accepted.length,0);});

test("stale USGS feed degrades the source and accepted evidence",async()=>{const fetchImpl=async()=>({ok:true,status:200,headers,json:async()=>({type:"FeatureCollection",metadata:{generated:Date.parse("2026-09-29T11:00:00Z")},features:[feature]})});const out=await loadUsgsEarthquakes({fetchImpl,now:Date.parse("2026-09-29T12:03:00Z")});assert.equal(out.collectorState,"DEGRADED");assert.equal(out.accepted.length,1);assert.equal(out.accepted[0].collectorState,"DEGRADED");});

test("fresh valid empty USGS feed is healthy zero, not outage",async()=>{const fetchImpl=async()=>({ok:true,status:200,headers,json:async()=>({type:"FeatureCollection",metadata:{generated:Date.parse("2026-09-29T12:02:00Z"),count:0},features:[]})});const out=await loadUsgsEarthquakes({fetchImpl,now:Date.parse("2026-09-29T12:03:00Z")});assert.equal(out.collectorState,"HEALTHY");assert.equal(out.accepted.length,0);});

test("USGS acquisition rejects null feed generation timestamps",async()=>{const fetchImpl=async()=>({ok:true,status:200,headers,json:async()=>({type:"FeatureCollection",metadata:{generated:null,count:1},features:[feature]})});const out=await loadUsgsEarthquakes({fetchImpl,now:Date.parse("2026-09-29T12:03:00Z")});assert.equal(out.collectorState,"DOWN");assert.equal(out.accepted.length,0);assert.match(out.rejected[0].reason,/generation timestamp/);});

test("USGS acquisition selects the weekly feed for a seven-day view",async()=>{let requested="";const fetchImpl=async url=>{requested=url;return {ok:true,status:200,headers,json:async()=>({type:"FeatureCollection",metadata:{generated:Date.parse("2026-09-29T12:02:00Z"),count:1},features:[feature]})};};const out=await loadUsgsEarthquakes({fetchImpl,now:Date.parse("2026-09-29T12:03:00Z"),timeRangeDays:7});assert.equal(out.collectorState,"HEALTHY");assert.match(requested,/2\.5_week\.geojson$/);});
test("USGS acquisition selects the monthly feed for a thirty-day view",async()=>{let requested="";const fetchImpl=async url=>{requested=url;return {ok:true,status:200,headers,json:async()=>({type:"FeatureCollection",metadata:{generated:Date.parse("2026-09-29T12:02:00Z"),count:1},features:[feature]})};};const out=await loadUsgsEarthquakes({fetchImpl,now:Date.parse("2026-09-29T12:03:00Z"),timeRangeDays:30});assert.equal(out.collectorState,"HEALTHY");assert.match(requested,/2\.5_month\.geojson$/);});
test("unsupported USGS time ranges fail closed",async()=>{const out=await loadUsgsEarthquakes({fetchImpl:async()=>{throw new Error("must not fetch");},now:Date.parse("2026-09-29T12:03:00Z"),timeRangeDays:90});assert.equal(out.collectorState,"DOWN");assert.match(out.rejected[0].reason,/Unsupported USGS/);});
