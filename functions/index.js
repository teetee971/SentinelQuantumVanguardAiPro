export async function onRequestGet() {
  return new Response(
    JSON.stringify({ status: "ok", service: "Sentinel Quantum Vanguard AI Pro API active" }),
    { headers: { "Content-Type": "application/json" } }
  );
}
