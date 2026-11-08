export async function fetchJson(url,{fetchImpl=globalThis.fetch,timeoutMs=8000}={}) {
  if (typeof fetchImpl!=="function") throw new TypeError("fetch implementation required");
  if (!Number.isFinite(timeoutMs)||timeoutMs<=0) throw new RangeError("timeoutMs must be positive");
  const controller=new AbortController();
  const timer=setTimeout(()=>controller.abort(),timeoutMs);
  try {
    const response=await fetchImpl(url,{signal:controller.signal,redirect:"error",credentials:"omit",cache:"no-store",headers:{accept:"application/json"}});
    if (!response||!response.ok) throw new Error(`HTTP ${response?.status??"unknown"}`);
    const contentType=response.headers?.get?.("content-type")||"";
    if (!contentType.toLowerCase().includes("json")) throw new Error("Expected JSON response");
    if(response.body?.getReader) return JSON.parse(await fetchText(url,{fetchImpl:async()=>response,timeoutMs,format:"json"}));
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

/** Bounds the actual bytes read, not only an optional Content-Length header. */
export async function fetchText(url,{fetchImpl=globalThis.fetch,timeoutMs=8000,maxBytes=5*1024*1024,format='json',allowMislabelledJson=false}={}) {
 if(typeof fetchImpl!=='function'||!Number.isFinite(timeoutMs)||timeoutMs<=0||!Number.isSafeInteger(maxBytes)||maxBytes<=0)throw Error('Invalid transport limits');
 const controller=new AbortController();let timer,reader;
 const timedOut=new Promise((_,reject)=>{timer=setTimeout(()=>{controller.abort();reader?.cancel().catch(()=>{});reject(Error('Source timeout'));},timeoutMs);});
 try{return await Promise.race([timedOut,(async()=>{
  const r=await fetchImpl(url,{signal:controller.signal,redirect:'error',credentials:'omit',cache:'no-store',headers:{accept:format==='xml'?'application/rss+xml, application/xml':'application/geo+json, application/json'}});
  if(!r?.ok)throw Error(`HTTP ${r?.status??'unknown'}`);
  const type=r.headers?.get?.('content-type')||'';if(!(format==='xml'?/\b(?:xml|rss\+xml|atom\+xml)\b/i:/\b(?:json|geo\+json)\b/i).test(type)&&!(allowMislabelledJson&&/^application\/rss\+xml(?:;|$)/i.test(type)))throw Error('Unexpected source content type');
  const length=r.headers?.get?.('content-length');if(length&&Number(length)>maxBytes)throw Error('Source byte limit exceeded');
  if(!r.body?.getReader)throw Error('Readable response stream required');
  reader=r.body.getReader();const chunks=[];let size=0;
  while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>maxBytes)throw Error('Source byte limit exceeded');chunks.push(value);}
  const all=new Uint8Array(size);let at=0;for(const c of chunks){all.set(c,at);at+=c.byteLength;}return new TextDecoder('utf-8',{fatal:true}).decode(all);
 })()]);}finally{clearTimeout(timer);controller.abort();if(reader){try{reader.cancel().catch(()=>{});}catch{}try{reader.releaseLock();}catch{}}}
}
