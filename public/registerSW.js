if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/sw.js')
      .then(() => console.log('✅ Service Worker enregistré'))
      .catch(err => console.error('❌ Erreur Service Worker :', err));
  });
}
