import { useState, useEffect } from "react";

/**
 * MonetizerAI - MODULE 12
 * Module de prévision et de monétisation IA
 * Optimise les modèles d'abonnement, d'affiliation et de partenariat
 * via analyse prédictive et comportementale
 */

export function useMonetizerAI() {
  const [revenueData, setRevenueData] = useState({
    currentRevenue: 0,
    predictedRevenue: 0,
    monthlyGrowth: 0,
    activeSubscriptions: 0,
    partnerRevenue: 0,
  });

  const [subscriptionPlans, setSubscriptionPlans] = useState([
    {
      id: "free",
      name: "Free",
      price: 0,
      features: ["Basic scan", "5 alerts/day", "Community support"],
      subscribers: 1250,
    },
    {
      id: "pro",
      name: "Professional",
      price: 29.99,
      features: ["Unlimited scans", "Real-time alerts", "Priority support", "API access"],
      subscribers: 342,
    },
    {
      id: "enterprise",
      name: "Enterprise",
      price: 199.99,
      features: ["All Pro features", "Custom branding", "Dedicated support", "Multi-user"],
      subscribers: 89,
    },
  ]);

  const [partners, setPartners] = useState([
    { id: 1, name: "SecurityCorp", revenue: 5420, status: "active" },
    { id: 2, name: "CyberDefense Inc", revenue: 3890, status: "active" },
    { id: 3, name: "TechShield", revenue: 2150, status: "pending" },
  ]);

  // Revenue Predictor
  const predictRevenue = () => {
    const currentMonthly = subscriptionPlans.reduce(
      (sum, plan) => sum + plan.price * plan.subscribers,
      0
    );

    const growthRate = 0.15; // 15% monthly growth
    const predicted = currentMonthly * (1 + growthRate);

    return {
      current: currentMonthly,
      predicted: predicted,
      growth: growthRate * 100,
    };
  };

  // Subscription Optimizer
  const optimizePricing = (planId) => {
    const plan = subscriptionPlans.find((p) => p.id === planId);
    if (!plan) return null;

    // AI-based pricing optimization simulation
    const demandElasticity = 1.2;
    const optimalPrice = plan.price * (1 + 0.1); // 10% increase
    const expectedSubscribers = Math.floor(
      plan.subscribers * (1 - 0.1 / demandElasticity)
    );

    return {
      currentPrice: plan.price,
      optimalPrice: optimalPrice.toFixed(2),
      currentRevenue: plan.price * plan.subscribers,
      projectedRevenue: (optimalPrice * expectedSubscribers).toFixed(2),
      recommendation: optimalPrice > plan.price ? "increase" : "decrease",
    };
  };

  // Dynamic Pricing AI
  const calculateDynamicPrice = (basePrice, demand, competition) => {
    // Simulate AI-based dynamic pricing
    const demandFactor = 1 + (demand - 50) / 100; // Centered at 50% demand
    const competitionFactor = 1 - (competition / 100) * 0.2; // Max 20% discount
    const dynamicPrice = basePrice * demandFactor * competitionFactor;

    return Math.max(dynamicPrice, basePrice * 0.7); // Never go below 70% of base
  };

  // Partner Tracker
  const trackPartnerRevenue = () => {
    return partners.reduce((sum, partner) => {
      return partner.status === "active" ? sum + partner.revenue : sum;
    }, 0);
  };

  useEffect(() => {
    // Update revenue data
    const prediction = predictRevenue();
    const partnerRev = trackPartnerRevenue();

    setRevenueData({
      currentRevenue: prediction.current,
      predictedRevenue: prediction.predicted,
      monthlyGrowth: prediction.growth,
      activeSubscriptions: subscriptionPlans.reduce(
        (sum, plan) => sum + plan.subscribers,
        0
      ),
      partnerRevenue: partnerRev,
    });

    // Simulate real-time updates
    const interval = setInterval(() => {
      const newPrediction = predictRevenue();
      setRevenueData((prev) => ({
        ...prev,
        currentRevenue: newPrediction.current,
        predictedRevenue: newPrediction.predicted,
      }));
    }, 10000); // Update every 10 seconds

    return () => clearInterval(interval);
  }, [subscriptionPlans, partners]);

  const getRevenueBreakdown = () => {
    return {
      subscriptions: subscriptionPlans.reduce(
        (sum, plan) => sum + plan.price * plan.subscribers,
        0
      ),
      partners: trackPartnerRevenue(),
      total:
        subscriptionPlans.reduce(
          (sum, plan) => sum + plan.price * plan.subscribers,
          0
        ) + trackPartnerRevenue(),
    };
  };

  const getMonetizationInsights = () => {
    const breakdown = getRevenueBreakdown();
    const totalRevenue = breakdown.total;

    return {
      topPlan: subscriptionPlans.reduce((max, plan) =>
        plan.price * plan.subscribers > max.price * max.subscribers ? plan : max
      ),
      avgRevenuePerUser:
        totalRevenue / revenueData.activeSubscriptions || 0,
      conversionRate: (
        (subscriptionPlans
          .filter((p) => p.id !== "free")
          .reduce((sum, p) => sum + p.subscribers, 0) /
          revenueData.activeSubscriptions) *
        100
      ).toFixed(2),
      projectedAnnualRevenue: revenueData.predictedRevenue * 12,
    };
  };

  return {
    revenueData,
    subscriptionPlans,
    partners,
    predictRevenue,
    optimizePricing,
    calculateDynamicPrice,
    trackPartnerRevenue,
    getRevenueBreakdown,
    getMonetizationInsights,
  };
}

export default function MonetizerAI({ children, showDashboard = false }) {
  const {
    revenueData,
    subscriptionPlans,
    partners,
    optimizePricing,
    getRevenueBreakdown,
    getMonetizationInsights,
  } = useMonetizerAI();

  if (!showDashboard) {
    return <>{children}</>;
  }

  const breakdown = getRevenueBreakdown();
  const insights = getMonetizationInsights();

  return (
    <div>
      {children}
      <div className="fixed bottom-4 left-4 bg-zinc-900 border border-zinc-800 rounded-lg p-4 text-xs max-w-sm">
        <div className="flex items-center justify-between mb-3">
          <span className="font-semibold text-zinc-300 flex items-center">
            <span className="mr-2">💰</span>
            MonetizerAI
          </span>
          <span className="text-green-400 text-xs">
            +{revenueData.monthlyGrowth.toFixed(1)}%
          </span>
        </div>

        <div className="space-y-2 text-zinc-500">
          <div className="flex justify-between">
            <span>Revenu actuel:</span>
            <span className="text-zinc-300 font-semibold">
              ${revenueData.currentRevenue.toFixed(0)}
            </span>
          </div>
          <div className="flex justify-between">
            <span>Prévu:</span>
            <span className="text-green-400 font-semibold">
              ${revenueData.predictedRevenue.toFixed(0)}
            </span>
          </div>
          <div className="flex justify-between">
            <span>Abonnements:</span>
            <span className="text-zinc-300">
              {revenueData.activeSubscriptions}
            </span>
          </div>
          <div className="flex justify-between">
            <span>Partenaires:</span>
            <span className="text-zinc-300">
              ${revenueData.partnerRevenue.toFixed(0)}
            </span>
          </div>
        </div>

        <div className="mt-3 pt-3 border-t border-zinc-800">
          <div className="text-xs text-zinc-400">
            Taux conversion: {insights.conversionRate}%
          </div>
          <div className="text-xs text-zinc-400">
            ARR projeté: ${insights.projectedAnnualRevenue.toFixed(0)}
          </div>
        </div>
      </div>
    </div>
  );
}
