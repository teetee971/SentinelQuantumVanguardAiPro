
// Charger les modules
fetch('modules.json')
  .then(res => res.json())
  .then(data => {
    const container = document.getElementById('modules-grid');
    data.forEach(mod => {
      const div = document.createElement('div');
      div.className = 'module';
      
      // Add special handling for OPMR module
      if (mod.name === 'Surveillance prix DOM') {
        div.innerHTML = `<h3><a href="test-opmr.html" style="color: inherit; text-decoration: none;">${mod.name}</a></h3><p>${mod.description}</p>`;
        div.style.cursor = 'pointer';
        div.addEventListener('click', () => {
          window.open('test-opmr.html', '_blank');
        });
      } else {
        div.innerHTML = `<h3>${mod.name}</h3><p>${mod.description}</p>`;
      }
      
      container.appendChild(div);
    });
  });

// Charger les actualités
fetch('news.json')
  .then(res => res.json())
  .then(data => {
    const newsList = document.getElementById('news-list');
    data.forEach(item => {
      const li = document.createElement('li');
      li.textContent = `${item.date} – ${item.title}`;
      newsList.appendChild(li);
    });
  });

// Multilingue simple (placeholder)
document.getElementById('lang').addEventListener('change', (e) => {
  alert('Multilingue non activé ici. Démo FR uniquement pour l’instant.');
});

// Charger le blog
fetch('blog.json')
  .then(res => res.json())
  .then(data => {
    const blogList = document.getElementById('blog-list');
    data.forEach(post => {
      const div = document.createElement('div');
      div.className = 'blog-post';
      div.innerHTML = `<h4>${post.title}</h4><p><i>${post.author}</i> – ${post.content}</p>`;
      blogList.appendChild(div);
    });
  });

// Traduction dynamique (simulée sans API)
const translations = {
  en: {
    'Modules actifs': 'Active modules',
    'Fil d’actualités IA & Sécurité': 'AI & Security News Feed',
    'Blog & Articles': 'Blog & Articles',
    'Tester l’IA': 'Test the AI',
    'Téléchargement': 'Download',
    'Mode Gouvernemental (Premium)': 'Government Mode (Premium)',
    'Activer Silencieux': 'Enable Silent Mode'
  },
  es: {
    'Modules actifs': 'Módulos activos',
    'Fil d’actualités IA & Sécurité': 'Noticias de IA y seguridad',
    'Blog & Articles': 'Blog y artículos',
    'Tester l’IA': 'Probar la IA',
    'Téléchargement': 'Descargar',
    'Mode Gouvernemental (Premium)': 'Modo gubernamental (Premium)',
    'Activer Silencieux': 'Activar modo silencioso'
  }
};

document.getElementById('lang').addEventListener('change', (e) => {
  const lang = e.target.value;
  if (!translations[lang]) return;
  for (const fr in translations[lang]) {
    document.body.innerHTML = document.body.innerHTML.replaceAll(fr, translations[lang][fr]);
  }
});
