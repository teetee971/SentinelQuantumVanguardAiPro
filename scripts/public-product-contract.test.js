import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';

const read = (path) => readFileSync(resolve(path), 'utf8');
const index = read('index.html');
const navigation = read('public/shared-navigation.js');
const pricing = read('public/pricing.html');
const downloadGuide = read('public/download-guide.html');
const clientSpace = read('public/espace-client.html');
const faq = read('public/faq.html');
const mobileSecurity = read('public/mobile-security.html');
const serviceWorker = read('public/sw.js');
const roadmap = read('public/roadmap.html');
const readme = read('README.md');

test('roadmap keeps Zero Trust, SOC automation and sovereignty claims evidence-based', () => {
  assert.match(roadmap, /Zero Trust généralisé/);
  assert.match(roadmap, /SOC automatisé sous contrôle humain/);
  assert.match(roadmap, /Aucune action offensive autonome/);
  assert.match(roadmap, /GitHub, Cloudflare Pages, Render et Upstash/);
  assert.match(roadmap, /ne peut donc pas être qualifiée de souveraine aujourd’hui/);
});

test('the free phone directory remains a first-class public route', () => {
  assert.match(navigation, /phone-intelligence\.html[^\n]+Annuaire gratuit/);
  assert.match(index, /Rechercher un numéro — gratuit/);
  assert.match(pricing, /Téléphone gratuit/);
  assert.match(pricing, /Protection appels\/SMS Android gratuite/);
});

test('RTR coverage and NDR roadmap do not overstate availability or identity', () => {
  assert.match(roadmap, /Index locaux — France et Autriche/);
  assert.match(roadmap, /date de publication de ces CSV n’est pas connue/);
  assert.match(roadmap, /Conception — non opérationnel/);
  assert.match(roadmap, /Aucun capteur NDR ni mécanisme de quarantaine n’est livré/);
  assert.match(roadmap, /approbation humaine/);
  assert.match(readme, /synchronisation de cet index avec Android reste à construire/);
});

test('no Android package is publicly downloadable before the release gate', () => {
  const publicPages = [index, pricing, downloadGuide, clientSpace].join('\n');
  assert.doesNotMatch(publicPages, /chatgpt\.com\/api\/library/i);
  assert.doesNotMatch(publicPages, /href=["'][^"']+\.(?:apk|aab)(?:[?#][^"']*)?["']/i);
  assert.match(downloadGuide, /disabled[^>]*aria-disabled="true"|aria-disabled="true"[^>]*disabled/);
  assert.match(clientSpace, /APK indisponible/);
  assert.doesNotMatch(downloadGuide, /APK[^<\n]*réservé[^<\n]*licences actives/i);
});

test('organization activation is visibly unavailable until a server exists', () => {
  assert.match(clientSpace, /Code d’organisation/);
  assert.match(clientSpace, /id="organization-code"[^>]*disabled/);
  assert.match(clientSpace, /Activer — bientôt disponible<\/button>/);
  assert.match(clientSpace, /côté serveur/);
});

test('the Sentinel command-center visual is optimized and identified', () => {
  assert.match(index, /sentinel-command-center\.webp/);
  assert.match(index, /alt="[^"]*[Ss]entinel[^"]*"/);
  assert.match(index, /loading="lazy"/);
  assert.ok(statSync(resolve('assets/images/sentinel-command-center.webp')).size <= 150_000);
});

test('unversioned interface code is not served cache-first', () => {
  assert.match(serviceWorker, /CACHE_VERSION = 'sentinel-v2\.3\.0'/);
  assert.doesNotMatch(serviceWorker, /return \['\.css', '\.js'/);
});

test('desktop and paid offers are not presented as delivered products', () => {
  const publicPages = [index, pricing, downloadGuide, clientSpace, faq].join('\n');
  assert.doesNotMatch(publicPages, /Après activation de licence, le client récupère l'installateur/i);
  assert.doesNotMatch(publicPages, /<li>Application PC locale<\/li>/i);
  assert.doesNotMatch(publicPages, /<li>PC \+ Android pour cette génération produit<\/li>/i);
  assert.match(pricing, /Client PC installable[^<]*<\/td><td>Non livré/);
  assert.match(clientSpace, /Aucun installateur PC n’existe aujourd’hui/);
  assert.match(pricing, /Aucune offre payante n’est actuellement commercialisée/);
});

test('mobile security claims identify the capability owner and current VPN state', () => {
  assert.match(mobileSecurity, /Sentinel est une application Android, pas un système d’exploitation/);
  assert.match(mobileSecurity, /backend client WireGuard Android est désormais présent dans le code/);
  assert.match(mobileSecurity, /aucune passerelle Sentinel de sortie n’est encore provisionnée/);
  assert.match(mobileSecurity, /Client présent · passerelle absente/);
  assert.match(mobileSecurity, /Capacités du téléphone ou du système — pas de Sentinel/);
  assert.match(mobileSecurity, /GrapheneOS/);
  assert.match(mobileSecurity, /CallScreeningService/);
  assert.match(faq, /Le client VPN Sentinel utilise désormais/);
});

test('the verified Render runtime has one explicit official URL', () => {
  const officialApi = 'https://sentinel-moteur-api.onrender.com/';
  assert.match(readme, new RegExp(officialApi.replace(/[.*+?^$\{\}()|[\]\\]/g, '\\$&')));
  assert.match(roadmap, /sentinel-moteur-api\.onrender\.com\/health\/live/);
  assert.match(roadmap, /sentinel-moteur-api\.onrender\.com\/health\/ready/);
  assert.match(roadmap, /Redis annonçait <code>connected<\/code>/);
  assert.doesNotMatch(roadmap, /Aucune URL Render officielle n’est publiée/);
});


test('the ad blocker roadmap is explicit about scope, privacy and Android VPN limits', () => {
  assert.match(roadmap, /Anti-publicité et anti-traceurs/);
  assert.match(roadmap, /VpnService/);
  assert.match(roadmap, /un seul service VPN Android peut être actif à la fois/);
  assert.match(roadmap, /ne déchiffrera pas HTTPS/);
  assert.match(roadmap, /Aucun bloqueur n’est livré aujourd’hui/);
  assert.match(roadmap, /la vitrine Web ne peut pas filtrer les autres sites/);
});
