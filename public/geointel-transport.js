export async function fetchJson(url,{fetchImpl=globalThis.fetch,timeoutMs=8000}={}) {
  if (typeof fetchImpl!=="function") throw new TypeError("fetch implementation required");
  if (!Number.isFinite(timeoutMs)||timeoutMs<=0) throw new RangeError("timeoutMs must be positive");
  const controller=new AbortController();
  const timer=setTimeout(()=>controller.abort(),timeoutMs);
  try {
    const response=await fetchImpl(url,{signal:controller.signal,headers:{accept:"application/json"}});
    if (!response||!response.ok) throw new Error(`HTTP ${response?.status??"unknown"}`);
    const contentType=response.headers?.get?.("content-type")||"";
    if (!contentType.toLowerCase().includes("json")) throw new Error("Expected JSON response");
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}
