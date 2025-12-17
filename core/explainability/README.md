# Explainability Module

This directory contains the logic for making AI decisions explainable and transparent.

## Principles of Explainable AI (XAI)

1. **No Black Boxes**: Every decision must be explainable
2. **Human Language**: Explanations in plain, understandable terms
3. **Transparency**: Show the reasoning, not just the conclusion
4. **Auditability**: All decisions are logged with full context
5. **Reversibility**: Users can understand and challenge decisions

## How It Works

### 1. Rule-Based Decisions

All "AI" decisions are actually based on explicit, documented rules.

**Example**:
```
IF (permissions_count > 10 AND network_activity_high)
THEN severity = "WARNING"
BECAUSE "Combination of many permissions + high network = potential data leak"
```

### 2. Explanation Generation

For each decision, we generate:

- **What**: What decision was made
- **Why**: Why this decision was made
- **How**: What data led to this decision
- **Impact**: What this means for the user
- **Action**: What the user should do

### 3. Explanation Format

```json
{
  "decision": "Block recommended",
  "confidence": 0.85,
  "reasoning": [
    "Number matches known spam pattern",
    "Reported 127 times in public database",
    "Geographic location mismatch"
  ],
  "data_sources": [
    "ARCEP spam database",
    "Local heuristics",
    "User reports"
  ],
  "recommendation": "Block this number",
  "user_can_override": true,
  "explainability_score": 1.0
}
```

## Explainability Score

Each decision has an explainability score (0-1):

- **1.0**: Fully explainable with clear reasoning
- **0.8**: Mostly explainable with minor uncertainty
- **0.6**: Partially explainable
- **<0.5**: Not acceptable - decision should not be made

**Sentinel standard**: All decisions must have explainability_score >= 0.8

## Types of Explanations

### 1. Causal Explanations

"X happened BECAUSE Y and Z"

### 2. Counterfactual Explanations

"If condition A was different, result would be B"

### 3. Feature Importance

"These 3 factors were most important in this decision"

### 4. Examples

"Similar to these previous cases..."

## No Machine Learning Opacity

Sentinel does NOT use:

- ❌ Neural networks without interpretability
- ❌ Deep learning black boxes
- ❌ Opaque statistical models
- ❌ Proprietary algorithms

Sentinel DOES use:

- ✅ Explicit rule systems
- ✅ Decision trees (fully visible)
- ✅ Heuristics (documented)
- ✅ Pattern matching (explainable)

## Compliance

This approach ensures compliance with:

- ✅ GDPR Article 22 (right to explanation)
- ✅ GDPR Article 13-14 (transparency)
- ✅ Ethical AI principles
- ✅ Institutional requirements

## Example: Phone Call Decision

```
INPUT: Incoming call from +33 9 XX XX XX XX

ANALYSIS:
1. Check spam database → FOUND (spam list)
2. Check pattern → MATCHES premium rate prefix
3. Check frequency → 5 calls in last hour
4. Check user history → Never called before

REASONING:
- Known spam number (confidence: HIGH)
- Premium rate number pattern (confidence: HIGH)
- Suspicious call frequency (confidence: MEDIUM)
- No prior relationship (confidence: LOW)

DECISION: BLOCK RECOMMENDED

EXPLANATION: "This number is in the public spam database 
and uses a premium rate prefix. Blocking recommended to 
prevent potential charges. You can override this decision."

EXPLAINABILITY: 1.0 (fully explainable)
```

## User Rights

Users can:

- ✅ View full explanation for any decision
- ✅ Override decisions
- ✅ Provide feedback
- ✅ Request human review
- ✅ Disable automated decisions

## Continuous Improvement

We continuously improve explainability by:

1. User feedback on explanations
2. A/B testing explanation formats
3. Academic research on XAI
4. Compliance audits
5. User testing

---

**Remember**: If we can't explain it, we don't do it.
