# Cloudflare Pages Functions - AI Cyber Threat Analysis

This directory contains Cloudflare Pages Functions that enable real-time AI-powered cybersecurity threat analysis using Cloudflare Workers AI.

## 📁 Structure

```
functions/
└── api/
    └── analyze.ts    # AI threat analysis endpoint
```

## 🚀 API Endpoint

### POST /api/analyze

Analyzes a cybersecurity threat using Cloudflare Workers AI (Llama 3 model).

**Request:**
```json
{
  "threat": "Ransomware LockBit"
}
```

**Response:**
```json
{
  "success": true,
  "threat": "Ransomware LockBit",
  "analysis": {
    "response": "... AI-generated analysis ..."
  },
  "timestamp": "2025-12-13T15:00:00.000Z",
  "disclaimer": "Cette analyse est générée automatiquement..."
}
```

## ⚙️ Cloudflare Configuration Required

### 1. Enable Workers AI

In Cloudflare Dashboard:
1. Go to **Workers & Pages** → **AI**
2. Enable **Workers AI**
3. Verify models are available

### 2. Bind AI to Pages Project

In your Pages project settings:
1. Go to **Settings** → **Functions**
2. Click **Add binding**
3. Type: **AI**
4. Variable name: **AI**
5. Save

This binding is **REQUIRED** for the function to work. Without it, you'll get errors.

## 🔒 Security & Compliance

### Input Validation
- Maximum threat description length: 500 characters
- Input sanitization and HTML escaping
- No execution of user code

### AI Prompting
- System prompt enforces: factual, neutral, educational responses
- Explicit prohibition of speculation and illegal activity instructions
- French language responses for pedagogical clarity

### Data Privacy
- ✅ No personal data collection
- ✅ No data storage
- ✅ No external API calls
- ✅ CORS enabled for frontend access
- ✅ All processing on Cloudflare edge

### Legal Disclaimers
- Clear "informational only" disclaimers in responses
- No security promises
- No threat detection guarantees
- Educational purpose explicitly stated

## 🧪 Testing

### Local Testing (Wrangler)

```bash
# Install Wrangler
npm install -g wrangler

# Test function locally
wrangler pages dev public --binding AI=@cf/meta/llama-3-8b-instruct

# Make test request
curl -X POST http://localhost:8788/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"threat":"Phishing bancaire"}'
```

### Production Testing

Once deployed on Cloudflare Pages:

```bash
curl -X POST https://your-project.pages.dev/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"threat":"Ransomware LockBit"}'
```

## 📝 Implementation Details

### AI Model
- Model: `@cf/meta/llama-3-8b-instruct`
- Provider: Meta via Cloudflare Workers AI
- Language: French (primary), English (fallback)
- Max tokens: Default (managed by Cloudflare)

### Error Handling
- Input validation errors: HTTP 400
- AI processing errors: HTTP 500
- All errors return JSON with error messages
- Graceful degradation

### CORS Configuration
- Allow origin: `*` (public API)
- Allow methods: `POST, OPTIONS`
- Allow headers: `Content-Type`
- Preflight requests handled via OPTIONS

## 🎯 Use Cases

### Educational
- Understanding threat mechanisms
- Cybersecurity awareness training
- Risk level assessment
- Attack vector explanation

### Informational
- Public threat database queries
- Security concept explanations
- Industry standard references
- Terminology clarification

### ⚠️ NOT for
- Real-time threat detection
- Active system protection
- Legal/compliance advice
- Technical security solutions

## 📖 Frontend Integration

See `public/ai-threat-analysis.html` for complete frontend implementation.

**Key points:**
- Fetch API for HTTP requests
- HTML escaping for XSS prevention
- Loading states and error handling
- Clear disclaimers displayed to users

## 🔄 Deployment

### Automatic Deployment

Cloudflare Pages automatically detects and deploys functions when you push to your repository:

```bash
git add functions/
git commit -m "feat: add AI threat analysis function"
git push origin main
```

### Build Configuration

No build step required:
- Framework: None
- Build command: (empty)
- Build output directory: `/public`
- Functions directory: `/functions` (auto-detected)

### Environment Variables

No environment variables needed. The AI binding is configured via Cloudflare Dashboard.

## 📊 Monitoring

### Cloudflare Analytics

Track function usage in Cloudflare Dashboard:
- **Workers & Pages** → Your project → **Analytics**
- Requests per second
- Error rates
- Latency percentiles
- AI model usage

### Logs

View real-time logs:
```bash
wrangler pages deployment tail
```

## 🛠️ Troubleshooting

### Error: "AI is not defined"

**Cause:** AI binding not configured in Cloudflare Pages
**Solution:** Add AI binding in project settings (see Configuration section)

### Error: "Model not found"

**Cause:** Workers AI not enabled or model unavailable
**Solution:** Enable Workers AI in Cloudflare Dashboard

### Error: "CORS error"

**Cause:** Missing OPTIONS handler or CORS headers
**Solution:** Verify `onRequestOptions` function exists and returns correct headers

## 📚 Resources

- [Cloudflare Workers AI Documentation](https://developers.cloudflare.com/workers-ai/)
- [Pages Functions Guide](https://developers.cloudflare.com/pages/platform/functions/)
- [Available AI Models](https://developers.cloudflare.com/workers-ai/models/)
- [Llama 3 Model Card](https://developers.cloudflare.com/workers-ai/models/llama-3-8b-instruct/)

## ⚖️ License & Attribution

- Function code: MIT License (project license)
- AI Model: Meta Llama 3 (via Cloudflare)
- Complies with all project transparency requirements
- No proprietary code or closed-source dependencies

---

**Last Updated:** December 2025  
**Version:** 1.0.0  
**Status:** Production Ready ✅
