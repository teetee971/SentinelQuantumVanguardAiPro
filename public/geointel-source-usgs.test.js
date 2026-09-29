import test from "node:test";
import assert from "node:assert/strict";
import {ingestRecords} from "./geointel-adapter.js";
import {extractUsgsFeatures,usgsEarthquakeAdapter} from "./geointel-source-usgs.js";

const feature={type:"Feature",id:"us-test",properties:{mag:5.2,place:"Fixture Region",time:Date.parse("2026-09-29T12:00:00Z"),updated:Date.parse("2026-09-29T12:05:00Z"),status:"reviewed",url:"https://earthquake.usgs.gov/earthquakes/eventpage/us-test"},geometry:{type:"Point",coordinates:[-61.5,16.2,10]}};

test("USGS fixture maps documented GeoJSON fields into GeoIntel",()=>{const out=ingestRecords(usgsEarthquakeAdapter,[feature],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.collectorState,"HEALTHY");assert.equal(out.accepted[0].layer,"natural");assert.equal(out.accepted[0].severity,3);assert.equal(out.accepted[0].status,"verified");});
test("USGS reviewed and automatic states do not share trust",()=>{const automatic={...feature,id:"us-auto",properties:{...feature.properties,status:"automatic"}};const out=ingestRecords(usgsEarthquakeAdapter,[automatic],{ingestedAt:"2026-09-29T12:06:00Z"});assert.equal(out.accepted[0].status,"unverified");assert.equal(out.accepted[0].confidence,.85);});
test("USGS payload must be a FeatureCollection",()=>assert.throws(()=>extractUsgsFeatures({features:[]}),/FeatureCollection/));
