import {ingestRecords} from './geointel-adapter.js';
import {deduplicateEvents,normalizeEvent} from './geointel-core.js';
import {fetchText} from './geointel-transport.js';
import {loadUsgsEarthquakes} from './geointel-source-usgs-live.js';
import {GEO_SOURCES} from './geointel-source-registry.js';
export const MAX_SOURCE_RECORDS=500;
function requiredText(v){if(typeof v!=='string'||!v.trim()||v.length>4000)throw Error('Source text/identity missing or excessive');return v.trim();}
function date(v,now){if(typeof v!=='string'||!v.trim()||!Number.isFinite(Date.parse(v))||Date.parse(v)>now)throw Error('Invalid/missing/future source date');return new Date(v).toISOString();}
function official(v,host){const u=new URL(requiredText(v));if(u.protocol!=='https:'||u.hostname!==host||u.username||u.password||(u.port&&u.port!=='443'))throw Error('Non-official source URL');return u.href;}
function base(id,title,layer){return {id,sourceEventId:id,title,layer,lat:null,lon:null,locationStatus:'UNLOCATED',severity:null,severityBasis:'NOT_PROVIDED',confidence:null,status:'unverified',detectedAt:null};}
export function mapEonet(r,now){
 const id=requiredText(r?.id);if(!Array.isArray(r.geometry)||!r.geometry.length||r.geometry.length>10000)throw Error('EONET geometry unavailable/excessive');
 const entries=r.geometry.map(g=>({...g,when:date(g.date,now)})).sort((a,b)=>Date.parse(b.when)-Date.parse(a.when));const g=entries[0];
 const c=g.coordinates;if(g.type!=='Point'||!Array.isArray(c)||c.length<2||!c.slice(0,2).every(x=>typeof x==='number'&&Number.isFinite(x))||Math.abs(c[0])>180||Math.abs(c[1])>90)throw Error('EONET valid point required');
 const cats=r.categories?.map(x=>x.id)||[];const layer=cats.includes('wildfires')||cats.includes('volcanoes')?'hotspots':cats.includes('severeStorms')?'weather':'natural';
 return {...base(`eonet:${id}`,`NASA — ${requiredText(r.title)}`,layer),sourceEventId:id,lat:c[1],lon:c[0],locationStatus:'POINT',locationLabel:'Point publié par EONET',occurredAt:g.when,detectedAt:g.when,sourceUrl:`https://eonet.gsfc.nasa.gov/api/v3/events/${encodeURIComponent(id)}`};
}
export function mapNws(r,now){
 const p=r?.properties;if(r?.type!=='Feature'||!p||p.status!=='Actual')throw Error('NWS actual alert required');
 const id=requiredText(p.id);const when=date(p.sent,now);const end=p.expires==null?null:new Date(p.expires).toISOString();
 const severity={Extreme:5,Severe:4,Moderate:3,Minor:2,Unknown:null}[p.severity];if(severity===undefined)throw Error('Unknown NWS severity');
 return {...base(`nws:${id}`,`NWS — ${requiredText(p.event)}`,'weather'),sourceEventId:id,occurredAt:when,detectedAt:when,endsAt:end,locationLabel:typeof p.areaDesc==='string'?p.areaDesc.slice(0,4000):'Zone NWS non précisée',severity,severityBasis:severity===null?'NOT_PROVIDED':'SOURCE_CATEGORY',sourceUrl:official(r.id||p['@id'],'api.weather.gov')};
}
export function mapIoda(r,now){
 if(!Number.isSafeInteger(r?.start)||r.start<0||r.start*1000>now||!Number.isSafeInteger(r.duration)||r.duration<0)throw Error('IODA valid interval required');
 const location=requiredText(r.location),datasource=requiredText(r.datasource),method=requiredText(r.method);const id=`${location}:${r.start}:${datasource}:${method}`;
 return {...base(`ioda:${id}`,`Signal IODA — ${requiredText(r.location_name||location)} (${datasource})`,'outages'),sourceEventId:id,locationLabel:r.location_name||location,occurredAt:new Date(r.start*1000).toISOString(),endsAt:new Date((r.start+r.duration)*1000).toISOString(),sourceUrl:'https://ioda.inetintel.cc.gatech.edu/'};
}
export function mapPublication(r,id,now){
 if(!['un','ofac'].includes(id))throw Error('Publication source unsupported');const host=id==='un'?'news.un.org':'ofac.treasury.gov';const url=official(r.url,host),key=requiredText(r.id||url),when=date(r.date,now);
 return {...base(`${id}:${key}`,`${id==='un'?'Publication ONU':'Publication OFAC'} — ${requiredText(r.title)}`,id==='un'?'conflicts':'sanctions'),sourceEventId:key,sourceUrl:url,occurredAt:when,detectedAt:when,locationLabel:'Publication non géolocalisée'};
}
export function parsePublicationXml(text,id,parserFactory=()=>new DOMParser()){
 if(typeof text!=='string'||/<!DOCTYPE|<!ENTITY/i.test(text))throw Error('XML DTD/entity forbidden');
 const doc=parserFactory().parseFromString(text,'application/xml');if(doc.querySelector('parsererror')||doc.documentElement?.localName!=='rss')throw Error('RSS document required');
 const items=Array.from(doc.querySelectorAll('channel > item'));if(items.length>MAX_SOURCE_RECORDS)throw Error('Publication feed limit exceeded');
 return items.map(n=>({id:n.querySelector('guid')?.textContent||n.querySelector('link')?.textContent,title:n.querySelector('title')?.textContent,url:n.querySelector('link')?.textContent,date:n.querySelector('pubDate')?.textContent}));
}
export async function loadAdditionalSource(id,{fetchImpl,now=Date.now(),timeRangeDays=7,parserFactory}={}){
 const spec=GEO_SOURCES[id];if(!spec||id==='usgs')throw Error('Unsupported additional source');
 try{
  if(!Number.isFinite(now)||![1,7,30].includes(timeRangeDays))throw Error('Invalid acquisition time/range');
  const text=await fetchText(`/api/geointel/${id}?days=${timeRangeDays}`,{fetchImpl,format:spec.format,maxBytes:5*1024*1024});
  let records,partial=false;
  if(spec.format==='xml')records=parsePublicationXml(text,id,parserFactory);
  else {const p=JSON.parse(text);if(id==='eonet'){if(!Array.isArray(p.events)||p.title!=='EONET Events')throw Error('EONET events required');records=p.events;}
   else if(id==='nws'){if(p.type!=='FeatureCollection'||!Array.isArray(p.features))throw Error('NWS FeatureCollection required');records=p.features;partial=Boolean(p.pagination?.next);}
   else {if(p.type!=='outages.events'||p.error||!Array.isArray(p.data))throw Error('IODA observations required');records=p.data;}}
  if(records.length>MAX_SOURCE_RECORDS)throw Error('Source record limit exceeded');partial ||= records.length===(id==='ioda'?50:MAX_SOURCE_RECORDS);
  const mapRecord=id==='eonet'?r=>mapEonet(r,now):id==='nws'?r=>mapNws(r,now):id==='ioda'?r=>mapIoda(r,now):r=>mapPublication(r,id,now);
  const result=ingestRecords({name:spec.name,mapRecord},records,{ingestedAt:new Date(now).toISOString()});const collectorState=partial&&result.collectorState==='HEALTHY'?'DEGRADED':result.collectorState;
  return {...result,collectorState,accepted:result.accepted.map(e=>normalizeEvent({...e,collectorState})),partial,feedGeneratedAt:null};
 }catch(error){return {sourceName:spec.name,collectorState:'DOWN',accepted:[],rejected:[{reason:error.message}],feedGeneratedAt:null};}
}
export async function loadGeoIntelSnapshot({now=Date.now(),timeRangeDays=7,fetchImpl,parserFactory,loaders}={}){
 if(!Number.isFinite(now)||![1,7,30].includes(timeRangeDays))throw Error('Invalid GeoIntel time/range');
 const jobs=loaders||Object.fromEntries(Object.keys(GEO_SOURCES).map(id=>[id,()=>id==='usgs'?loadUsgsEarthquakes({now,timeRangeDays,fetchImpl:(_url,options)=>(fetchImpl||globalThis.fetch)(`/api/geointel/usgs?days=${timeRangeDays}`,options)}):loadAdditionalSource(id,{now,timeRangeDays,fetchImpl,parserFactory})]));
 const sources=await Promise.all(Object.entries(jobs).map(async([id,load])=>{try{return {id,...GEO_SOURCES[id],...await load(),fetchedAt:new Date(now).toISOString()};}catch(e){return {id,...GEO_SOURCES[id],collectorState:'DOWN',accepted:[],rejected:[{reason:e.message}],fetchedAt:new Date(now).toISOString()};}}));
 return {sources,events:deduplicateEvents(sources.flatMap(s=>s.accepted))};
}
