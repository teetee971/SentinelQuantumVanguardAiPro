
document.getElementById('init').addEventListener('click', async () => {
  const question = prompt("Pose ta question à Sentinel IA");
  if (!question) return;

  const res = await fetch("/api/ia", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ prompt: question })
  });

  const data = await res.json();
  const response = data.response || "Pas de réponse IA.";

  alert(response);

  // 🔊 Lecture vocale automatique
  const synth = window.speechSynthesis;
  const utter = new SpeechSynthesisUtterance(response);
  utter.lang = "fr-FR"; // Changer ici pour d'autres langues : "en-US", "es-ES", etc.
  utter.pitch = 1.0;
  utter.rate = 1.0;
  synth.speak(utter);
});
