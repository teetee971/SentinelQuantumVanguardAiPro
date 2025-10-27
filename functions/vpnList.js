// functions/vpnList.js
export async function onRequest(context) {
  try {
    const data = await fetch(
      "https://raw.githubusercontent.com/teetee971/SentinelQuantumVanguardAIPro/main/backend/vpn_nodes.json"
    );
    const json = await data.json();
    return new Response(JSON.stringify(json), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(
      JSON.stringify({ error: "Unable to load VPN nodes", detail: err.message }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
}
