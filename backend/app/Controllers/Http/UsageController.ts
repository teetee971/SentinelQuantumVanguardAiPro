import type { HttpContextContract } from '@ioc:Adonis/Core/HttpContext'

/**
 * UsageController
 * Handles usage metering, quota checking, and usage export for Sentinel pricing plans
 */
export default class UsageController {
  /**
   * Get usage statistics for a user
   * GET /api/usage/:userId
   */
  public async get({ params, response }: HttpContextContract) {
    const { userId } = params

    // TODO: Replace with actual database query
    // This is a mock response for MVP
    const mockUsage = {
      userId,
      plan: 'freemium',
      period: {
        start: new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString(),
        end: new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0).toISOString(),
      },
      usage: {
        events: Math.floor(Math.random() * 800),
        api_calls: Math.floor(Math.random() * 400),
        scans: Math.floor(Math.random() * 40),
        stt_minutes: Math.floor(Math.random() * 8),
        tts_minutes: Math.floor(Math.random() * 7),
      },
      quotas: {
        events: 1000,
        api_calls: 500,
        scans: 50,
        stt_minutes: 10,
        tts_minutes: 10,
      },
      overage: {
        events: 0,
        api_calls: 0,
        scans: 0,
        stt_minutes: 0,
        tts_minutes: 0,
      },
    }

    return response.ok(mockUsage)
  }

  /**
   * Track usage increment
   * POST /api/usage/track
   * Body: { userId, metric, amount }
   */
  public async track({ request, response }: HttpContextContract) {
    const { userId, metric, amount = 1 } = request.only(['userId', 'metric', 'amount'])

    if (!userId || !metric) {
      return response.badRequest({ error: 'userId and metric are required' })
    }

    // Validate metric
    const validMetrics = ['events', 'api_calls', 'scans', 'stt_minutes', 'tts_minutes']
    if (!validMetrics.includes(metric)) {
      return response.badRequest({ error: 'Invalid metric' })
    }

    // TODO: Implement actual usage tracking in database
    // For MVP, we just acknowledge the tracking
    const tracked = {
      userId,
      metric,
      amount,
      timestamp: new Date().toISOString(),
      success: true,
    }

    return response.created(tracked)
  }

  /**
   * Check if user has exceeded quota
   * GET /api/usage/:userId/check/:metric
   */
  public async checkQuota({ params, response }: HttpContextContract) {
    const { userId, metric } = params

    // Define plan quotas
    const quotas = {
      freemium: {
        events: 1000,
        api_calls: 500,
        scans: 50,
        stt_minutes: 10,
        tts_minutes: 10,
      },
      starter: {
        events: 10000,
        api_calls: 5000,
        scans: 500,
        stt_minutes: 60,
        tts_minutes: 60,
      },
      pro: {
        events: 50000,
        api_calls: 25000,
        scans: 2500,
        stt_minutes: 300,
        tts_minutes: 300,
      },
      business: {
        events: 500000,
        api_calls: 250000,
        scans: 25000,
        stt_minutes: 1000,
        tts_minutes: 1000,
      },
      enterprise: {
        events: Infinity,
        api_calls: Infinity,
        scans: Infinity,
        stt_minutes: Infinity,
        tts_minutes: Infinity,
      },
    }

    // TODO: Fetch actual user plan and usage from database
    const userPlan = 'freemium'
    const currentUsage = Math.floor(Math.random() * 800)
    const limit = quotas[userPlan][metric] || 0

    const quotaCheck = {
      userId,
      metric,
      plan: userPlan,
      current: currentUsage,
      limit,
      remaining: Math.max(limit - currentUsage, 0),
      exceeded: currentUsage >= limit,
      percentage: limit > 0 ? Math.min((currentUsage / limit) * 100, 100) : 0,
    }

    return response.ok(quotaCheck)
  }

  /**
   * Export usage data
   * GET /api/usage/:userId/export?format=json|csv
   */
  public async export({ params, request, response }: HttpContextContract) {
    const { userId } = params
    const format = request.input('format', 'json')

    // TODO: Fetch actual usage data from database
    const usageData = {
      userId,
      exportDate: new Date().toISOString(),
      period: {
        start: new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString(),
        end: new Date().toISOString(),
      },
      metrics: [
        { date: '2025-11-01', metric: 'events', value: 150 },
        { date: '2025-11-01', metric: 'api_calls', value: 75 },
        { date: '2025-11-02', metric: 'events', value: 200 },
        { date: '2025-11-02', metric: 'api_calls', value: 100 },
        { date: '2025-11-03', metric: 'scans', value: 10 },
      ],
    }

    if (format === 'csv') {
      // Convert to CSV
      const csvHeader = 'Date,Metric,Value\n'
      const csvRows = usageData.metrics
        .map((m) => `${m.date},${m.metric},${m.value}`)
        .join('\n')
      const csv = csvHeader + csvRows

      response.header('Content-Type', 'text/csv')
      response.header('Content-Disposition', `attachment; filename="usage-${userId}.csv"`)
      return response.send(csv)
    }

    // Default: JSON
    return response.ok(usageData)
  }

  /**
   * Reset usage for a user (admin/scheduler only)
   * POST /api/usage/:userId/reset
   */
  public async reset({ params, response }: HttpContextContract) {
    const { userId } = params

    // TODO: Implement actual reset logic in database
    // This would typically be called by a scheduler on the 1st of each month

    const resetResult = {
      userId,
      resetDate: new Date().toISOString(),
      success: true,
      message: 'Usage counters reset successfully',
    }

    return response.ok(resetResult)
  }

  /**
   * Get overage charges for a user
   * GET /api/usage/:userId/overage
   */
  public async getOverage({ params, response }: HttpContextContract) {
    const { userId } = params

    // Overage pricing per unit
    const overagePrices = {
      events: 0.00001, // €0.01 per 1000 events
      api_calls: 0.00002, // €0.02 per 1000 calls
      scans: 0.05, // €0.05 per scan
      stt_minutes: 0.15, // €0.15 per minute
      tts_minutes: 0.12, // €0.12 per minute
    }

    // TODO: Calculate actual overage from database
    const mockOverage = {
      events: 0,
      api_calls: 0,
      scans: 0,
      stt_minutes: 0,
      tts_minutes: 0,
    }

    const overageCharges = Object.keys(mockOverage).reduce((acc, metric) => {
      const overage = mockOverage[metric]
      const price = overagePrices[metric] || 0
      acc[metric] = {
        units: overage,
        pricePerUnit: price,
        totalCharge: overage * price,
      }
      return acc
    }, {})

    const totalCharge = Object.values(overageCharges).reduce(
      (sum: number, charge: any) => sum + charge.totalCharge,
      0
    )

    return response.ok({
      userId,
      period: new Date().toISOString().substring(0, 7), // YYYY-MM
      overageCharges,
      totalCharge,
      currency: 'EUR',
    })
  }
}
