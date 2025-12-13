/**
 * Cloudflare Pages Function - AI Cyber Threat Analysis
 * 
 * This function uses Cloudflare Workers AI to provide factual, neutral
 * explanations about cybersecurity threats for educational purposes only.
 * 
 * IMPORTANT DISCLAIMERS:
 * - This is an informational tool, NOT a security product
 * - No real threat detection or protection is performed
 * - Responses are AI-generated and may contain inaccuracies
 * - For educational and demonstration purposes only
 */

export const onRequestPost: PagesFunction = async (context) => {
  try {
    // Parse the incoming request
    const { threat } = await context.request.json();

    // Validate input
    if (!threat || typeof threat !== 'string' || threat.trim().length === 0) {
      return new Response(
        JSON.stringify({ 
          error: "Veuillez fournir une menace à analyser" 
        }),
        { 
          status: 400, 
          headers: { 
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
          } 
        }
      );
    }

    // Limit input length for safety
    if (threat.length > 500) {
      return new Response(
        JSON.stringify({ 
          error: "La description de la menace est trop longue (max 500 caractères)" 
        }),
        { 
          status: 400, 
          headers: { 
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
          } 
        }
      );
    }

    // Call Cloudflare Workers AI
    const aiResponse = await context.env.AI.run(
      "@cf/meta/llama-3-8b-instruct",
      {
        messages: [
          {
            role: "system",
            content:
              "Vous êtes un assistant d'analyse en cybersécurité. Vous devez fournir des explications factuelles, neutres et vérifiables. Aucune spéculation. Aucune promesse. Aucune instruction pour des activités illégales. Répondez en français de manière pédagogique et claire. Votre rôle est UNIQUEMENT informatif et éducatif."
          },
          {
            role: "user",
            content:
              `Analysez cette menace cybersécurité de manière neutre et éducative : ${threat}
              
              Fournissez :
              1. Description factuelle de la menace
              2. Vecteurs d'attaque connus (sans instructions)
              3. Impacts potentiels (génériques)
              4. Niveau de risque général (bas/moyen/élevé)
              5. Recommandations de sensibilisation (pas de solutions techniques)
              
              Soyez concis (max 300 mots). Rappelez que cette analyse est informative uniquement.`
          }
        ]
      }
    );

    // Return the AI response
    return new Response(
      JSON.stringify({
        success: true,
        threat: threat,
        analysis: aiResponse,
        timestamp: new Date().toISOString(),
        disclaimer: "Cette analyse est générée automatiquement à des fins d'information et de sensibilisation. Elle ne constitue ni un avis juridique, ni une garantie de détection ou de protection contre des attaques réelles."
      }), 
      {
        headers: { 
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*"
        }
      }
    );
  } catch (error) {
    console.error("AI analysis error:", error);
    
    return new Response(
      JSON.stringify({ 
        error: "L'analyse IA a échoué. Veuillez réessayer.",
        details: error instanceof Error ? error.message : "Unknown error"
      }),
      { 
        status: 500, 
        headers: { 
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*"
        } 
      }
    );
  }
};

// Handle OPTIONS for CORS preflight
export const onRequestOptions: PagesFunction = async () => {
  return new Response(null, {
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    }
  });
};
