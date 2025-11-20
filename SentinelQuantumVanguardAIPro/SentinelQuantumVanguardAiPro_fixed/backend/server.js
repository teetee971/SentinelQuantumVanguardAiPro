const fastify = require('fastify')({ logger: true });

fastify.get('/health', async (request, reply) => {
  return { status: 'ok' };
});

async function start() {
  try {
    await fastify.listen({ port: process.env.PORT || 3000, host: '0.0.0.0' });
    console.log('Server running');
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
}
start();
