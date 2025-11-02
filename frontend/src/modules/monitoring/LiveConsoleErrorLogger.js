/**
 * LiveConsoleErrorLogger - Global Error Journal
 */
export class LiveConsoleErrorLogger {
  constructor() {
    this.status = 'active';
    this.errors = [];
  }

  async captureError(error) {
    const captured = {
      id: `error-${Date.now()}`,
      timestamp: new Date().toISOString(),
      message: error.message,
      stack: error.stack,
      severity: this.classifySeverity(error)
    };
    this.errors.push(captured);
    return captured;
  }

  classifySeverity(error) {
    if (error.message.includes('critical')) return 'critical';
    if (error.message.includes('warning')) return 'warning';
    return 'info';
  }

  getStatus() {
    return { status: this.status, errors: this.errors.length };
  }
}
export default LiveConsoleErrorLogger;
