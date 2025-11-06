import Route from '@ioc:Adonis/Core/Route'

Route.get('/', async () => {
  return { sentinel: 'Quantum Vanguard AI Pro API is online 🚀' }
})

Route.get('/admin/agents', 'AgentsController.status')

// Usage Metering API Routes
Route.group(() => {
  // Get usage for a user
  Route.get('/:userId', 'UsageController.get')
  
  // Track usage increment
  Route.post('/track', 'UsageController.track')
  
  // Check quota for a specific metric
  Route.get('/:userId/check/:metric', 'UsageController.checkQuota')
  
  // Export usage data
  Route.get('/:userId/export', 'UsageController.export')
  
  // Get overage charges
  Route.get('/:userId/overage', 'UsageController.getOverage')
  
  // Reset usage (admin/scheduler only)
  Route.post('/:userId/reset', 'UsageController.reset')
}).prefix('/api/usage')
