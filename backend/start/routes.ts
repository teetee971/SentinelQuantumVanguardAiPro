import Route from '@ioc:Adonis/Core/Route'

Route.get('/', async () => {
  return { sentinel: 'Quantum Vanguard AI Pro API is online 🚀' }
})

Route.get('/admin/agents', 'AgentsController.status')
