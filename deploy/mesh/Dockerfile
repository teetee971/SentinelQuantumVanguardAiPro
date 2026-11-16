FROM node:22-bookworm-slim

ENV NODE_ENV=production \
    MESH_BIND_HOST=127.0.0.1 \
    MESH_PORT=8788 \
    MESH_NAT_PROBE_ENABLED=false \
    MESH_RELAY_ENABLED=false \
    MESH_STATE_PATH=/var/lib/sentinel-mesh/state.json

WORKDIR /app

RUN groupadd --system sentinel \
    && useradd --system --gid sentinel --no-create-home --shell /usr/sbin/nologin sentinel \
    && mkdir -p /var/lib/sentinel-mesh \
    && chown sentinel:sentinel /var/lib/sentinel-mesh

COPY --chown=sentinel:sentinel package.json ./package.json
COPY --chown=sentinel:sentinel network/mesh ./network/mesh

USER sentinel

EXPOSE 8788/tcp 3479/udp 3480/udp

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.MESH_PORT||'8788')+'/health/live',{signal:AbortSignal.timeout(2000)}).then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"

CMD ["node", "network/mesh/server.js"]
