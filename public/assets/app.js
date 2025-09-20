
// Charger les modules
fetch('modules.json')
  .then(res => res.json())
  .then(data => {
    const container = document.getElementById('modules-grid');
    data.forEach(mod => {
      const div = document.createElement('div');
      div.className = 'module';
      div.innerHTML = `<h3>${mod.name}</h3><p>${mod.description}</p>`;
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

// Prix DOM functionality
document.addEventListener('DOMContentLoaded', () => {
  const searchBtn = document.getElementById('search-prix-btn');
  const produitInput = document.getElementById('produit-input');
  const territoireInput = document.getElementById('territoire-input');
  const resultsDiv = document.getElementById('prix-results');

  if (searchBtn) {
    searchBtn.addEventListener('click', async () => {
      const produit = produitInput.value.trim();
      const territoire = territoireInput.value.trim();

      if (!produit || !territoire) {
        resultsDiv.innerHTML = '<p style="color: #ff6b6b;">Veuillez remplir les champs produit et territoire.</p>';
        return;
      }

      searchBtn.textContent = 'Recherche en cours...';
      searchBtn.disabled = true;
      resultsDiv.innerHTML = '<p style="color: #74c0fc;">Recherche en cours...</p>';

      try {
        // Wait for the API utils to be loaded
        if (typeof window.fetchPrixDOM === 'function') {
          const data = await window.fetchPrixDOM(produit, territoire);
          displayPrixResults(data, produit, territoire);
        } else {
          resultsDiv.innerHTML = '<p style="color: #ff6b6b;">Erreur: Module API non disponible.</p>';
        }
      } catch (error) {
        console.error('Erreur lors de la recherche:', error);
        resultsDiv.innerHTML = '<p style="color: #ff6b6b;">Erreur lors de la recherche de prix.</p>';
      } finally {
        searchBtn.textContent = 'Rechercher Prix';
        searchBtn.disabled = false;
      }
    });
  }

  function displayPrixResults(data, produit, territoire) {
    if (!data || data.length === 0) {
      resultsDiv.innerHTML = `<p style="color: #ffd43b;">Aucun résultat trouvé pour "${produit}" en "${territoire}".</p>`;
      return;
    }

    let html = `<h3>Résultats pour "${produit}" en "${territoire}" (${data.length} résultats)</h3>`;
    html += '<div class="prix-grid">';
    
    data.forEach((item, index) => {
      html += `
        <div class="prix-item">
          <div class="prix-header">
            <strong>${item.enseigne || 'N/A'}</strong>
            <span class="prix-value">${item.prix || 'N/A'}€</span>
          </div>
          <div class="prix-date">${item.date || 'Date non disponible'}</div>
        </div>
      `;
    });
    
    html += '</div>';
    resultsDiv.innerHTML = html;
  }
});
