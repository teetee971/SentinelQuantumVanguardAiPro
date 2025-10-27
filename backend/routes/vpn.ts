import Route from '@ioc:Adonis/Core/Route'
import fs from 'fs'
import path from 'path'

/**
 * ✅ API Sentinel VPN Node Updater
 * Permet d’ajouter un nœud VPN distant via POST /api/vpn/update
 * Le nœud est enregistré dans backend/vpn_nodes.json
 */

Route.post('/api/vpn/update', async ({ request, response }) => {
  try {
    const node = request.body()
    if (!node.provider || !node.ip || !node.port || !node.public_key) {
      return response.badRequest({
        error: 'Champs requis manquants : provider, ip, port, public_key',
      })
    }

    const filePath = path.join(__dirname, '../vpn_nodes.json')

    // Création du fichier si inexistant
    if (!fs.existsSync(filePath)) {
      fs.writeFileSync(filePath, JSON.stringify({ nodes: [] }, null, 2))
    }

    const data = JSON.parse(fs.readFileSync(filePath, 'utf8'))

    // Ajout du nouveau nœud
    const newNode = {
      provider: node.provider,
      ip: node.ip,
      port: Number(node.port),
      public_key: node.public_key,
      status: 'online',
      country: node.country || 'N/A',
      region: node.region || 'N/A',
      updated_at: new Date().toISOString(),
    }

    data.nodes.push(newNode)

    fs.writeFileSync(filePath, JSON.stringify(data, null, 2))
    console.log(`✅ Nouveau nœud ajouté : ${node.provider} (${node.ip})`)

    return response.json({ success: true, node: newNode })
  } catch (err) {
    console.error('❌ Erreur lors de la mise à jour du nœud :', err)
    return response.status(500).json({ error: err.message })
  }
})
