import test from "node:test";
import assert from "node:assert/strict";
import {ingestRecords} from "./geointel-adapter.js";
import {extractUsgsFeatures,usgsEarthquakeAdapter} from "./geointel-source-usgs.js";

const feature={type:"Feature",id:"us-test",properties:{mag:5.2,place:"Fixture Region",time:Date.parse("2026-09-29T12:00:00Z"),updated:Date.parse("2026-09-29T12:05:00Z"),status:"reviewed",url:"https://earthquake.usgs.gov/earthquakes/eventpage/us-test"},geometry:{type:"Point",coordinates:[-61.5,16.2,10]}};

test("USGS fixture maps documented GeoJSON fields into GeoIntel",()=>{const out=ingestRecords(usgsEarthquakeAdapter,[feature],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.collectorState,"HEALTHY");assert.equal(out.accepted[0].layer,"natural");assert.equal(out.accepted[0].severity,3);assert.equal(out.accepted[0].status,"verified");});
test("USGS reviewed and automatic states do not invent probabilistic confidence",()=>{const reviewed=ingestRecords(usgsEarthquakeAdapter,[feature],{ingestedAt:"2026-09-29T12:06:00Z"});const automatic={...feature,id:"us-auto",properties:{...feature.properties,status:"automatic"}};const out=ingestRecords(usgsEarthquakeAdapter,[automatic],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(reviewed.accepted[0].confidence,null);assert.equal(out.accepted[0].status,"unverified");assert.equal(out.accepted[0].confidence,null);});
test("USGS payload must be a FeatureCollection",()=>assert.throws(()=>extractUsgsFeatures({features:[]}),/FeatureCollection/));

test("USGS features without stable source identity are rejected",()=>{const invalid={...feature,id:""};const out=ingestRecords(usgsEarthquakeAdapter,[invalid],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.collectorState,"DOWN");assert.equal(out.accepted.length,0);assert.equal(out.rejected.length,1);});

test("USGS null coordinates are rejected instead of being coerced to zero",()=>{const invalid={...feature,id:"us-null-coord",geometry:{type:"Point",coordinates:[null,null,10]}};const out=ingestRecords(usgsEarthquakeAdapter,[invalid],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.collectorState,"DOWN");assert.match(out.rejected[0].reason,/numeric coordinates/);});
test("USGS null magnitude is rejected instead of becoming M0.0",()=>{const invalid={...feature,id:"us-null-mag",properties:{...feature.properties,mag:null}};const out=ingestRecords(usgsEarthquakeAdapter,[invalid],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.collectorState,"DOWN");assert.match(out.rejected[0].reason,/magnitude/);});
test("USGS null timestamps are rejected instead of becoming Unix epoch",()=>{const invalid={...feature,id:"us-null-time",properties:{...feature.properties,time:null}};const out=ingestRecords(usgsEarthquakeAdapter,[invalid],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.collectorState,"DOWN");assert.match(out.rejected[0].reason,/timestamps/);});

test("USGS rejects non-official source URLs",()=>{const invalid={...feature,id:"us-evil-url",properties:{...feature.properties,url:"https://example.com/fake"}};const out=ingestRecords(usgsEarthquakeAdapter,[invalid],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.collectorState,"DOWN");assert.match(out.rejected[0].reason,/official HTTPS host/);});
test("USGS rejects non-HTTPS source URLs",()=>{const invalid={...feature,id:"us-http-url",properties:{...feature.properties,url:"http://earthquake.usgs.gov/earthquakes/eventpage/us-http-url"}};const out=ingestRecords(usgsEarthquakeAdapter,[invalid],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.collectorState,"DOWN");assert.match(out.rejected[0].reason,/official HTTPS host/);});
