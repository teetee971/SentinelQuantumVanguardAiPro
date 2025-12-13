/**
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Phase F - Minimal Backend (READ-ONLY)
 * 
 * This is a MINIMAL backend implementation with READ-ONLY endpoints
 * Only health and status endpoints are active
 * 
 * Status: ACTIVE (READ-ONLY MODE)
 * Feature Flags: FEATURE_BACKEND=false, FEATURE_BACKEND_READ_ONLY=true
 */

import { FEATURE_FLAGS, getSystemStatus, isBackendReadOnly } from '../config/feature-flags.js';

/**
 * Simulated database/state
 * In real deployment, this would connect to actual database
 */
const simulatedState = {
  systemHealth: {
    status: 'HEALTHY',
    uptime: 0,
    lastCheck: new Date().toISOString()
  },
  agents: [
    {
      id: 'network-guardian',
      name: 'Network Guardian',
      status: 'DORMANT',
      type: 'network-protection',
      lastActivity: new Date().toISOString()
    },
    {
      id: 'pegasus-scan',
      name: 'Pegasus Scanner',
      status: 'DORMANT',
      type: 'threat-detection',
      lastActivity: new Date().toISOString()
    },
    {
      id: 'anti-fraud-pro',
      name: 'Anti-Fraud Pro',
      status: 'DORMANT',
      type: 'fraud-detection',
      lastActivity: new Date().toISOString()
    },
    {
      id: 'privacy-guardian',
      name: 'Privacy Guardian',
      status: 'DORMANT',
      type: 'privacy-protection',
      lastActivity: new Date().toISOString()
    },
    {
      id: 'system-rootkit',
      name: 'Rootkit Scanner',
      status: 'DORMANT',
      type: 'rootkit-detection',
      lastActivity: new Date().toISOString()
    },
    {
      id: 'cloud-sync',
      name: 'Cloud Sync',
      status: 'DORMANT',
      type: 'secure-sync',
      lastActivity: new Date().toISOString()
    }
  ],
  metrics: {
    cpu: 0,
    memory: 0,
    disk: 0,
    network: {
      incoming: 0,
      outgoing: 0
    }
  }
};

/**
 * Backend API Class
 */
export class SentinelBackend {
  constructor() {
    this.baseUrl = '/api/v1';
    this.readOnly = isBackendReadOnly();
    this.startTime = Date.now();
  }

  /**
   * Health Check Endpoint
   * GET /api/v1/health
   */
  async getHealth() {
    const uptime = Math.floor((Date.now() - this.startTime) / 1000);
    
    return {
      status: 'HEALTHY',
      timestamp: new Date().toISOString(),
      uptime: uptime,
      mode: this.readOnly ? 'READ_ONLY' : 'FULL',
      components: {
        backend: 'HEALTHY',
        agents: 'DORMANT',
        database: 'SIMULATED',
        monitoring: 'ACTIVE'
      }
    };
  }

  /**
   * System Status Endpoint
   * GET /api/v1/system/status
   */
  async getStatus() {
    return {
      ...getSystemStatus(),
      timestamp: new Date().toISOString(),
      readOnly: this.readOnly
    };
  }

  /**
   * List Agents Endpoint
   * GET /api/v1/agents
   */
  async listAgents() {
    // Update agent states from feature flags
    const agents = simulatedState.agents.map(agent => ({
      ...agent,
      status: FEATURE_FLAGS[`AGENT_${agent.id.toUpperCase().replace(/-/g, '_')}`] || 'DORMANT'
    }));

    return {
      agents: agents,
      total: agents.length,
      timestamp: new Date().toISOString()
    };
  }

  /**
   * Get Agent Details Endpoint
   * GET /api/v1/agents/:id
   */
  async getAgent(id) {
    const agent = simulatedState.agents.find(a => a.id === id);
    
    if (!agent) {
      throw new Error(`Agent not found: ${id}`);
    }

    const agentKey = `AGENT_${id.toUpperCase().replace(/-/g, '_')}`;
    const currentStatus = FEATURE_FLAGS[agentKey] || 'DORMANT';

    return {
      ...agent,
      status: currentStatus,
      configuration: {
        enabled: FEATURE_FLAGS.FEATURE_AGENTS,
        state: currentStatus,
        readOnly: this.readOnly
      },
      metrics: {
        activations: 0,
        detections: 0,
        lastScan: new Date().toISOString()
      },
      timestamp: new Date().toISOString()
    };
  }

  /**
   * Get System Metrics Endpoint
   * GET /api/v1/monitoring/metrics
   */
  async getMetrics() {
    // Simulated metrics - in real deployment, would get actual system metrics
    return {
      cpu: Math.random() * 20 + 10, // 10-30%
      memory: Math.random() * 30 + 40, // 40-70%
      disk: Math.random() * 20 + 20, // 20-40%
      network: {
        incoming: Math.floor(Math.random() * 1000),
        outgoing: Math.floor(Math.random() * 2000)
      },
      timestamp: new Date().toISOString()
    };
  }

  /**
   * Block write operations in read-only mode
   */
  async updateAgent(id, data) {
    if (this.readOnly) {
      throw new Error('Backend is in READ-ONLY mode. Write operations are disabled.');
    }
    
    // Would implement write logic here when FEATURE_BACKEND_WRITE is enabled
    return {
      success: false,
      message: 'Write operations not yet implemented',
      readOnly: true
    };
  }

  /**
   * Block write operations in read-only mode
   */
  async setAgentStatus(id, status) {
    if (this.readOnly) {
      throw new Error('Backend is in READ-ONLY mode. Write operations are disabled.');
    }
    
    // Would implement write logic here when FEATURE_BACKEND_WRITE is enabled
    return {
      success: false,
      message: 'Write operations not yet implemented',
      readOnly: true
    };
  }
}

/**
 * Create backend client
 */
export function createBackendClient() {
  return new SentinelBackend();
}

/**
 * Simple mock API server for development
 * This simulates the backend responses
 */
export class MockAPIServer {
  constructor() {
    this.backend = new SentinelBackend();
    this.routes = new Map();
    this.setupRoutes();
  }

  setupRoutes() {
    // Health check
    this.routes.set('GET /api/v1/health', () => this.backend.getHealth());
    
    // System status
    this.routes.set('GET /api/v1/system/status', () => this.backend.getStatus());
    
    // Agents
    this.routes.set('GET /api/v1/agents', () => this.backend.listAgents());
    this.routes.set('GET /api/v1/agents/:id', (params) => this.backend.getAgent(params.id));
    
    // Metrics
    this.routes.set('GET /api/v1/monitoring/metrics', () => this.backend.getMetrics());
  }

  async handle(method, path, params = {}) {
    const routeKey = `${method} ${path}`;
    const handler = this.routes.get(routeKey);
    
    if (!handler) {
      // Try pattern matching for :id routes
      for (const [route, routeHandler] of this.routes.entries()) {
        if (route.includes(':id')) {
          const pattern = route.replace(':id', '([^/]+)');
          const regex = new RegExp(`^${pattern}$`);
          const match = `${method} ${path}`.match(regex);
          
          if (match) {
            return await routeHandler({ id: match[1] });
          }
        }
      }
      
      throw new Error(`Route not found: ${method} ${path}`);
    }
    
    return await handler(params);
  }
}

// Create global instance
const mockServer = new MockAPIServer();

/**
 * Fetch wrapper that uses mock server
 * This allows using standard fetch() calls that get intercepted
 */
export async function sentinelFetch(url, options = {}) {
  const method = options.method || 'GET';
  const path = url.replace(/^https?:\/\/[^/]+/, ''); // Remove domain
  
  try {
    const response = await mockServer.handle(method, path);
    return {
      ok: true,
      status: 200,
      json: async () => response
    };
  } catch (error) {
    return {
      ok: false,
      status: error.message.includes('not found') ? 404 : 500,
      json: async () => ({
        error: {
          code: 'API_ERROR',
          message: error.message
        }
      })
    };
  }
}

// Export global access
if (typeof window !== 'undefined') {
  window.SENTINEL_Backend = SentinelBackend;
  window.SENTINEL_createBackendClient = createBackendClient;
  window.SENTINEL_sentinelFetch = sentinelFetch;
  window.SENTINEL_mockServer = mockServer;
}

export default SentinelBackend;
