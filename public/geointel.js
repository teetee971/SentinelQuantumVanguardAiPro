import {GEOINTEL_LAYERS,evidenceState,filterEvents,projectEquirectangular,riskScore} from "./geointel-core.js";
import {loadUsgsEarthquakes} from "./geointel-source-usgs-live.js";

const now=Date.now();
const demoEvents=[
 {id:"demo-natural",layer:"natural",title:"Événement naturel — démonstration",lat:16.2,lon:-61.5,severity:3,confidence:.8,occurredAt:new Date(now-2*86400000).toISOString(),sourceName:"Jeu de démonstration",status:"unverified",detectedAt:new Date(now-2*86400000).toISOString()},
 {id:"demo-outage",layer:"outages",title:"Coupure réseau — démonstration",lat:48.85,lon:2.35,severity:2,confidence:.7,occurredAt:new Date(now-86400000).toISOString(),sourceName:"Jeu de démonstration",status:"unverified",detectedAt:new Date(now-86400000).toISOString()},
 {id:"demo-hotspot",layer:"hotspots",title:"Point chaud — démonstration",lat:25.2,lon:55.3,severity:4,confidence:.65,occurredAt:new Date(now-4*86400000).toISOString(),sourceName:"Jeu de démonstration",status:"unverified",detectedAt:new Date(now-4*86400000).toISOString()}
];

const state={layers:new Set(GEOINTEL_LAYERS),days:7,selected:null,events:demoEvents,mode:"DEMO",collectorState:"UNKNOWN"};
const canvas=document.querySelector("#geointel-map"),ctx=canvas.getContext("2d");
const list=document.querySelector("#geointel-events"),inspector=document.querySelector("#geointel-inspector"),count=document.querySelector("#geointel-count"),status=document.querySelector("#geointel-status");

function resize(){const r=canvas.getBoundingClientRect(),d=Math.max(1,window.devicePixelRatio||1);canvas.width=Math.round(r.width*d);canvas.height=Math.round(r.height*d);ctx.setTransform(d,0,0,d,0,0);render();}
function drawGrid(w,h){ctx.clearRect(0,0,w,h);ctx.fillStyle="#071018";ctx.fillRect(0,0,w,h);ctx.strokeStyle="rgba(92,199,255,.12)";ctx.lineWidth=1;for(let lon=-180;lon<=180;lon+=30){const{x}=projectEquirectangular(0,lon,w,h);ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke();}for(let lat=-60;lat<=60;lat+=30){const{y}=projectEquirectangular(lat,0,w,h);ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke();}}
function current(){return filterEvents(state.events,{layers:[...state.layers],timeRangeDays:state.days});}
function render(){const w=canvas.clientWidth,h=canvas.clientHeight;drawGrid(w,h);const events=current();for(const e of events){const p=projectEquirectangular(e.lat,e.lon,w,h);ctx.beginPath();ctx.arc(p.x,p.y,5+riskScore(e)/25,0,Math.PI*2);ctx.fillStyle=e.status==="verified"?"#31d6a0":"#ffb45c";ctx.fill();ctx.strokeStyle="#fff";ctx.stroke();}count.textContent=`${events.length} événement(s) visible(s) · ${state.mode==="LIVE"?"USGS live":"données de démonstration"}`;list.replaceChildren(...events.map(e=>{const b=document.createElement("button");b.className="geo-event";b.type="button";b.textContent=`${e.title} · risque ${riskScore(e)}/100`;b.onclick=()=>inspect(e);return b;}));}
function inspect(e){state.selected=e;inspector.innerHTML="";const h=document.createElement("h2");h.textContent=e.title;const p=document.createElement("p");p.textContent=`Couche: ${e.layer} · score: ${riskScore(e)}/100 · confiance: ${Math.round(e.confidence*100)} %`;const proof=evidenceState(e,{now,ttlMs:21600000});const s=document.createElement("p");s.textContent=`Preuve: ${proof.trust} · fraîcheur: ${proof.freshness}. ${state.mode==="LIVE"?"Source réelle affichée avec provenance.":"Aucun événement réel n’est affirmé par cette vue."}`;inspector.append(h,p,s);}
document.querySelectorAll("[data-layer]").forEach(c=>c.addEventListener("change",()=>{c.checked?state.layers.add(c.dataset.layer):state.layers.delete(c.dataset.layer);render();}));
document.querySelector("#geointel-range").addEventListener("change",e=>{state.days=Number(e.target.value);render();});
async function loadLive(){
  status.textContent="LIVE · CHARGEMENT";
  const result=await loadUsgsEarthquakes();
  state.collectorState=result.collectorState;
  if (result.collectorState==="HEALTHY"||result.collectorState==="DEGRADED") {
    state.events=result.accepted;state.mode="LIVE";
    status.textContent=`LIVE · USGS · ${result.collectorState}`;
  } else {
    state.events=demoEvents;state.mode="DEMO";
    status.textContent="USGS DOWN · MODE DÉMO";
  }
  render();
}
window.addEventListener("resize",resize);resize();loadLive();
