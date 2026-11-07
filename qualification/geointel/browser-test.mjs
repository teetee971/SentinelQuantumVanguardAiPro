import {createServer} from 'node:http';
import {readFile,writeFile,mkdtemp,rm} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {extname,resolve} from 'node:path';
import assert from 'node:assert/strict';
import {routeGeoIntel} from '/workspace/sentinel-play-ready/_worker.js';
import {sourceUrl} from '/workspace/sentinel-play-ready/public/geointel-source-registry.js';
const root='/workspace/sentinel-play-ready',out='/workspace/qualification-evidence/geointel-multisource';let broken=false;
const payload={};for(const id of ['usgs','eonet','nws','ioda','un','ofac'])payload[id]=await readFile(`${out}/${id}.payload`,'utf8');
const server=createServer(async(req,res)=>{try{
 const u=new URL(req.url,'http://localhost');
 if(u.pathname.startsWith('/api/geointel/')){
  const result=await routeGeoIntel(new Request('http://localhost'+req.url),{fetchImpl:async url=>{const id=Object.keys(payload).find(id=>new URL(sourceUrl(id,7)).hostname===new URL(url).hostname);if(broken&&id==='ofac')return new Response('down',{status:503});return new Response(payload[id],{headers:{'content-type':['un','ofac'].includes(id)?'application/xml':id==='eonet'?'application/rss+xml':'application/json'}});}});
  res.writeHead(result.status,Object.fromEntries(result.headers));res.end(await result.text());return;
 }
 const path=resolve(root,'.'+(u.pathname==='/'?'/index.html':u.pathname));if(!path.startsWith(root+'/'))throw Error('path');
 const b=await readFile(path);const policy=(await readFile(root+'/_headers','utf8')).split('\n').find(x=>x.trim().startsWith('Content-Security-Policy:')).trim().slice('Content-Security-Policy:'.length).trim();res.setHeader('Content-Security-Policy',policy);res.setHeader('content-type',({'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'})[extname(path)]||'application/octet-stream');res.end(b);
 }catch(e){res.statusCode=404;res.end(e.message);}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const port=server.address().port;
const profile=await mkdtemp('/dev/shm/geointel-browser-');
const browser=spawn('/usr/bin/chromium',['--headless','--no-sandbox','--disable-gpu','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'],{stdio:'ignore'});
let ws,results={};try{
 let debugPort;for(let i=0;i<100;i++){try{debugPort=(await readFile(profile+'/DevToolsActivePort','utf8')).split('\n')[0];break;}catch{await new Promise(r=>setTimeout(r,100));}}
 assert.ok(debugPort);const targets=await(await fetch(`http://127.0.0.1:${debugPort}/json`)).json();ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));
 let seq=0;const pending=new Map();const errors=[];ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}else if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.text);});
 const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;};
 await call('Page.enable');await call('Runtime.enable');await call('Network.enable');await call('Network.setCacheDisabled',{cacheDisabled:true});await call('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});

 await call('Page.navigate',{url:`http://127.0.0.1:${port}/public/geointel.html`});
 for(let i=0;i<150;i++){if(await evaluate(`document.querySelectorAll('#geointel-sources li').length===6`))break;await new Promise(r=>setTimeout(r,100));}
 results.sources=await evaluate(`[...document.querySelectorAll('#geointel-sources li')].map(x=>({id:x.dataset.source,health:x.dataset.health,text:x.textContent}))`);
 assert.equal(results.sources.length,6);assert.ok(results.sources.filter(s=>s.health!=='DOWN').length>=4,JSON.stringify(results.sources));
 results.serviceWorker=await evaluate(`(async()=>{await navigator.serviceWorker.register('/public/sw.js');await Promise.race([navigator.serviceWorker.ready,new Promise((_,reject)=>setTimeout(()=>reject(Error('service worker activation timeout')),8000))]);for(let i=0;i<100&&!navigator.serviceWorker.controller;i++)await new Promise(r=>setTimeout(r,50));return Boolean(navigator.serviceWorker.controller);})()`);assert.equal(results.serviceWorker,true);
 results.counter=await evaluate(`document.querySelector('#geointel-count').textContent`);
 results.xml=await evaluate(`(async()=>{const m=await import('/public/geointel-multisource.js');const t='<rss><channel><item><guid>fake</guid><title><![CDATA[<img src=x onerror=alert(1)>]]></title><link>https://ofac.treasury.gov/recent-actions/test</link><pubDate>Fri, 06 Nov 2026 10:00:00 GMT</pubDate></item></channel></rss>';const x=m.parsePublicationXml(t,'ofac');return {title:x[0].title,layer:m.mapPublication(x[0],'ofac',Date.now()).layer};})()`);
 assert.equal(results.xml.layer,'sanctions');assert.match(results.xml.title,/<img/);
 await evaluate(`document.querySelector('[data-layer="natural"]').click()`);assert.ok(await evaluate(`document.querySelectorAll('#geointel-events button').length>=1`));
 await evaluate(`document.querySelector('#geointel-events button').click()`);assert.ok(await evaluate(`document.querySelector('#geointel-inspector a').href.startsWith('https://')`));
 const shot=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});await writeFile(out+'/browser.png',Buffer.from(shot.data,'base64'));
 broken=true;await evaluate(`document.querySelector('#geointel-refresh').click()`);
 for(let i=0;i<100;i++){if(await evaluate(`document.querySelector('[data-source="ofac"]')?.dataset.health==='DOWN'`))break;await new Promise(r=>setTimeout(r,100));}
 assert.equal(await evaluate(`document.querySelector('[data-source="ofac"]').dataset.health`),'DOWN');assert.ok(await evaluate(`document.querySelectorAll('#geointel-events button').length>0`));
 results.failurePreservesOthers=true;await call('Network.emulateNetworkConditions',{offline:true,latency:0,downloadThroughput:0,uploadThroughput:0});await evaluate(`document.querySelector('#geointel-refresh').click()`);for(let i=0;i<100;i++){if(await evaluate(`document.querySelector('#geointel-status').textContent.includes('6 DOWN')`))break;await new Promise(r=>setTimeout(r,100));}results.offlineAllDown=await evaluate(`document.querySelector('#geointel-status').textContent.includes('6 DOWN')&&document.querySelectorAll('#geointel-events button').length===0`);assert.equal(results.offlineAllDown,true);results.browserExceptions=errors;assert.deepEqual(errors,[]);
 results.mobileOverflow=await(async()=>{await call('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await new Promise(r=>setTimeout(r,150));return await evaluate(`document.documentElement.scrollWidth>window.innerWidth`);})();assert.equal(results.mobileOverflow,false);
 await writeFile(out+'/BROWSER_RESULTS.json',JSON.stringify(results,null,2));console.log(JSON.stringify(results.sources.map(s=>({id:s.id,health:s.health}))),results.counter,'Browser checks passed');
}finally{ws?.close();browser.kill('SIGTERM');server.close();await rm(profile,{recursive:true,force:true,maxRetries:5,retryDelay:100});}
