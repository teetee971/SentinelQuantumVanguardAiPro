import {sourceUrl,GEO_SOURCES} from './public/geointel-source-registry.js';
import {fetchText} from './public/geointel-transport.js';

/** Fixed public feeds only. Never accepts upstream URL, credentials or arbitrary headers. */
export async function routeGeoIntel(request,{fetchImpl=fetch,now=Date.now()}={}) {
 const u=new URL(request.url),match=/^\/api\/geointel\/([a-z]+)$/.exec(u.pathname);
 if(!match)return null;
 const headers={'content-type':'application/json; charset=utf-8','x-content-type-options':'nosniff','cache-control':'no-store'};
 if(request.method!=='GET')return new Response(JSON.stringify({error:'GET required'}),{status:405,headers:{...headers,allow:'GET'}});
 const id=match[1],raw=u.searchParams.get('days')||'7',days=Number(raw);
 if(!Object.hasOwn(GEO_SOURCES,id)||!['1','7','30'].includes(raw)||([...u.searchParams.keys()].some(k=>k!=='days')||u.searchParams.getAll('days').length>1))return new Response(JSON.stringify({error:'Unsupported source/range'}),{status:400,headers});
 try{
  const format=GEO_SOURCES[id].format;
  const upstream=(url,options)=>fetchImpl(url,{...options,redirect:'error',headers:{...options.headers,'user-agent':'SentinelGeoIntel/1.0 (+https://github.com/teetee971/SentinelQuantumVanguardAiPro)'}});
  const text=await fetchText(sourceUrl(id,days,now),{fetchImpl:upstream,format,timeoutMs:7000,maxBytes:5*1024*1024,allowMislabelledJson:id==='eonet'});
  if(id==='eonet'&&JSON.parse(text)?.title!=='EONET Events')throw Error('EONET schema rejected');
  return new Response(text,{headers:{...headers,'content-type':format==='xml'?'application/xml; charset=utf-8':'application/json; charset=utf-8'}});
 }catch{return new Response(JSON.stringify({error:'Public source unavailable or response rejected'}),{status:502,headers});}
}
export default {async fetch(request,env){return await routeGeoIntel(request)||env.ASSETS.fetch(request);}};
