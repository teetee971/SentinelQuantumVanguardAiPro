/**
 * AgentLatencyMonitor - AI Agent Response Speed Monitor
 */
export class AgentLatencyMonitor {
  constructor() {
    this.status = 'active';
    this.measurements = [];
  }

  async measureLatency(agentId, operation) {
    const start = Date.now();
    const result = await operation();
    const latency = Date.now() - start;
    
    this.measurements.push({
      agentId,
      latency,
      timestamp: new Date().toISOString()
    });
    
    return { result, latency };
  }

  getStatus() {
    return { status: this.status, measurements: this.measurements.length };
  }
}
export default AgentLatencyMonitor;
