/**
 * OpenTelemetry Web Instrumentation
 * 
 * This module provides opt-in OpenTelemetry instrumentation for web applications.
 * Telemetry is only initialized when window.__ENABLE_TELEMETRY === true
 * to avoid performance overhead by default.
 */

import { WebTracerProvider } from '@opentelemetry/sdk-trace-web';
import { getWebAutoInstrumentations } from '@opentelemetry/auto-instrumentations-web';
import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { Resource } from '@opentelemetry/resources';
import { SEMRESATTRS_SERVICE_NAME, SEMRESATTRS_SERVICE_VERSION } from '@opentelemetry/semantic-conventions';

declare global {
  interface Window {
    __ENABLE_TELEMETRY?: boolean;
    __OTEL_ENDPOINT?: string;
  }
}

let isInitialized = false;

export interface TelemetryConfig {
  serviceName?: string;
  serviceVersion?: string;
  endpoint?: string;
}

/**
 * Initialize OpenTelemetry web instrumentation
 * Only runs if window.__ENABLE_TELEMETRY === true
 */
export function initTelemetry(config: TelemetryConfig = {}): void {
  // Guard: Only initialize if explicitly enabled
  if (!window.__ENABLE_TELEMETRY) {
    console.info('[Telemetry] Not enabled. Set window.__ENABLE_TELEMETRY=true to enable.');
    return;
  }

  // Prevent double initialization
  if (isInitialized) {
    console.warn('[Telemetry] Already initialized.');
    return;
  }

  try {
    const {
      serviceName = 'sentinel-quantum-vanguard',
      serviceVersion = '1.0.0',
      endpoint = window.__OTEL_ENDPOINT || 'http://localhost:4318/v1/traces',
    } = config;

    // Create resource with service information
    const resource = new Resource({
      [SEMRESATTRS_SERVICE_NAME]: serviceName,
      [SEMRESATTRS_SERVICE_VERSION]: serviceVersion,
    });

    // Create tracer provider
    const provider = new WebTracerProvider({
      resource,
    });

    // Configure OTLP exporter
    const exporter = new OTLPTraceExporter({
      url: endpoint,
    });

    // Add batch span processor
    provider.addSpanProcessor(new BatchSpanProcessor(exporter));

    // Register the provider
    provider.register();

    // Auto-instrument web APIs
    registerInstrumentations({
      instrumentations: [
        getWebAutoInstrumentations({
          '@opentelemetry/instrumentation-document-load': {},
          '@opentelemetry/instrumentation-user-interaction': {},
          '@opentelemetry/instrumentation-fetch': {},
          '@opentelemetry/instrumentation-xml-http-request': {},
        }),
      ],
    });

    isInitialized = true;
    console.info('[Telemetry] Initialized successfully');
  } catch (error) {
    console.error('[Telemetry] Failed to initialize:', error);
  }
}

/**
 * Check if telemetry is enabled
 */
export function isTelemetryEnabled(): boolean {
  return Boolean(window.__ENABLE_TELEMETRY);
}

/**
 * Check if telemetry is initialized
 */
export function isTelemetryInitialized(): boolean {
  return isInitialized;
}
