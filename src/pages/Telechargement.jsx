export default function Telechargement() {
  return (
    <div>
      <h2 className="text-xl text-sentinel-accent mb-3 font-semibold">Téléchargements Sentinel</h2>
      <p className="text-gray-300 mb-3">Choisis ta version :</p>
      <ul className="space-y-2">
        <li><a href="https://sentinel.pages.dev/downloads/sentinel.apk" className="text-sentinel-accent underline">📱 Télécharger APK Android</a></li>
        <li><a href="https://sentinel.pages.dev" className="text-sentinel-accent underline">💻 Version PWA Web</a></li>
      </ul>
    </div>
  );
}
