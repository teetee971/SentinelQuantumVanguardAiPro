import {GEOINTEL_LAYERS,evidenceState,filterEvents,projectEquirectangular,riskScore,selectMostRecentEvents} from './geointel-core.js';
import {loadGeoIntelSnapshot} from './geointel-multisource.js';

const state={layers:new Set(GEOINTEL_LAYERS),days:7,selected:null,events:[],sources:[]};
const DISPLAY_LIMIT=500;let sourceLoadEpoch=0;
const canvas=document.querySelector('#geointel-map'),ctx=canvas.getContext('2d');
const list=document.querySelector('#geointel-events'),inspector=document.querySelector('#geointel-inspector'),count=document.querySelector('#geointel-count'),status=document.querySelector('#geointel-status'),sources=document.querySelector('#geointel-sources');
function paragraph(parent,text){const p=document.createElement('p');p.textContent=text;parent.append(p);return p;}
function clearInspector(message='Sélectionnez un événement ou une publication.'){state.selected=null;inspector.replaceChildren();const h=document.createElement('h2');h.textContent='Inspecteur';inspector.append(h);paragraph(inspector,message);}
function resize(){const r=canvas.getBoundingClientRect(),d=Math.max(1,window.devicePixelRatio||1);canvas.width=Math.round(r.width*d);canvas.height=Math.round(r.height*d);ctx.setTransform(d,0,0,d,0,0);render();}
function drawGrid(w,h){ctx.clearRect(0,0,w,h);ctx.fillStyle='#071018';ctx.fillRect(0,0,w,h);ctx.strokeStyle='rgba(92,199,255,.12)';ctx.lineWidth=1;for(let lon=-180;lon<=180;lon+=30){const{x}=projectEquirectangular(0,lon,w,h);ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke();}for(let lat=-60;lat<=60;lat+=30){const{y}=projectEquirectangular(lat,0,w,h);ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke();}}
function current(){return filterEvents(state.events,{layers:[...state.layers],timeRangeDays:state.days});}
function render(){
 drawGrid(canvas.clientWidth,canvas.clientHeight);const filtered=current(),events=selectMostRecentEvents(filtered,{limit:DISPLAY_LIMIT});
 if(state.selected&&!events.some(e=>e.id===state.selected.id&&e.sourceName===state.selected.sourceName))clearInspector('La sélection précédente n’est plus visible.');
 let plotted=0;
 for(const e of events){if(e.locationStatus==='UNLOCATED')continue;plotted++;const p=projectEquirectangular(e.lat,e.lon,canvas.clientWidth,canvas.clientHeight),proof=evidenceState(e,{now:Date.now(),ttlMs:21600000});ctx.beginPath();ctx.arc(p.x,p.y,5+(e.severity??1),0,Math.PI*2);ctx.fillStyle=proof.trust==='SOURCE_VERIFIED_CURRENT'?'#31d6a0':'#ffb45c';ctx.fill();ctx.strokeStyle='#fff';ctx.stroke();}
 count.textContent=`${events.length} affiché(s) sur ${filtered.length} correspondant(s) · ${plotted} point(s) · ${events.length-plotted} sans coordonnées`;
 list.replaceChildren(...events.map(e=>{const b=document.createElement('button');b.className='geo-event';b.type='button';b.textContent=`${e.title} · ${e.sourceName}${e.locationStatus==='UNLOCATED'?' · non géolocalisé':''}`;b.onclick=()=>inspect(e);return b;}));
 if(!events.length)paragraph(list,state.sources.some(s=>s.collectorState==='DOWN')?'Aucun résultat affiché ; une ou plusieurs sources sont indisponibles. Cela ne prouve pas une absence d’événements.':'Aucun résultat de ces sources dans les filtres choisis. Couverture non exhaustive.');
}
function inspect(e){
 state.selected=e;inspector.replaceChildren();const h=document.createElement('h2');h.textContent=e.title;inspector.append(h);
 const risk=riskScore(e);paragraph(inspector,`Couche : ${e.layer} · sévérité ${e.severityBasis==='SOURCE_CATEGORY'?'issue de la catégorie source':'Sentinel'} : ${e.severity??'non fournie'} · risque : ${risk??'non calculé'} · confiance : ${e.confidence===null?'non fournie':e.confidence}`);
 paragraph(inspector,`${e.locationLabel||'Point source'}${e.locationStatus==='UNLOCATED'?' — aucune coordonnée inventée.':` — coordonnées source : ${e.lat}, ${e.lon}`}`);
 paragraph(inspector,`Date source : ${e.occurredAt} · mise à jour/observation source : ${e.detectedAt||'inconnue'} · acquisition : ${e.ingestedAt||'inconnue'}`);
 const proof=evidenceState(e,{now:Date.now(),ttlMs:21600000});paragraph(inspector,`Preuve : ${proof.trust} · fraîcheur : ${proof.freshness}. Une publication ou anomalie ne constitue pas une confirmation indépendante de l’événement.`);
 if(e.sourceUrl){const a=document.createElement('a');a.href=e.sourceUrl;a.target='_blank';a.rel='noopener noreferrer';a.textContent='Consulter la source';inspector.append(a);}
}
document.querySelectorAll('[data-layer]').forEach(c=>c.addEventListener('change',()=>{c.checked?state.layers.add(c.dataset.layer):state.layers.delete(c.dataset.layer);render();}));
document.querySelector('#geointel-range').addEventListener('change',e=>{state.days=Number(e.target.value);loadSourceSnapshot();});
document.querySelector('#geointel-refresh').addEventListener('click',loadSourceSnapshot);
async function loadSourceSnapshot(){
 const epoch=++sourceLoadEpoch;document.querySelector('#geointel-refresh').disabled=true;document.querySelector('#geointel-range').disabled=true;status.textContent='ACQUISITION DES SOURCES…';
 const result=await loadGeoIntelSnapshot({timeRangeDays:state.days});if(epoch!==sourceLoadEpoch)return;
 document.querySelector('#geointel-refresh').disabled=false;document.querySelector('#geointel-range').disabled=false;state.events=result.events;state.sources=result.sources;clearInspector();
 const healthy=result.sources.filter(s=>s.collectorState==='HEALTHY').length,down=result.sources.filter(s=>s.collectorState==='DOWN').length;
 status.textContent=`${healthy}/${result.sources.length} source(s) HEALTHY · ${down} DOWN · instantanés, couverture partielle`;
 sources.replaceChildren(...result.sources.map(s=>{const row=document.createElement('li');row.dataset.source=s.id;row.dataset.health=s.collectorState;row.textContent=`${s.sourceName||s.name} — ${s.collectorState} — ${s.accepted.length} reçu(s), ${s.rejected.length} rejet(s). ${s.coverage} Acquisition : ${s.fetchedAt}. ${s.partial?'Limite/pagination : résultats partiels. ':''}${s.rejected.slice(0,1).map(e=>e.reason).join('')}`;return row;}));render();
}
window.addEventListener('resize',resize);resize();loadSourceSnapshot();
