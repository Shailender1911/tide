# 🚀 STARTUP & FOUNDER-SPECIFIC QUESTIONS

## Questions Co-Founders Typically Ask

---

## 1. "Why Bachatt? Why not stay at PayU?"

### Your Answer:
"Three reasons:

**1. Technical Alignment**: At PayU, I've built NACH mandate systems, UPI integrations, and loan disbursement flows. Bachatt's UPI AutoPay for daily savings uses the same underlying technology - but for wealth creation instead of debt. I want to apply my expertise to help people save, not just borrow.

**2. Scale Challenge**: PayU Lending processes thousands of loans. Bachatt's daily savings model means potentially millions of micro-transactions daily. I want to solve that scale problem - idempotency, retry mechanisms, reconciliation at that volume.

**3. Startup Impact**: I've made 1,066+ commits at PayU, but in a large org, impact is distributed. At Bachatt, I can be closer to product decisions, ship faster, and see direct user impact. I built ConfigNexus from scratch using AI tools - that's the kind of ownership I want."

---

## 2. "Tell me about a time you failed"

### Your Real Story:
"When fixing GPay SFTP upload failures, I made a mistake. The issue was a BouncyCastle cryptographic library version conflict.

**My Mistake**: I updated only the affected service without checking the dependency tree across all modules. It worked in dev but broke in production because other services had different versions.

**What I Learned**:
- Always check transitive dependencies
- Test with production-like environment
- Document dependency versions in parent POM

**How I Fixed It**: I unified all modules to BouncyCastle 1.70 and added dependency management in the parent POM. Now we have a single source of truth for cryptographic libraries."

---

## 3. "How do you handle ambiguity?"

### Your Real Story:
"When building the Insurance Consent feature for BharatPe (LENDING-7698), the requirements were vague - 'add insurance option to offers.'

**What I Did**:
1. **Asked clarifying questions**: What insurance types? What rates? Per tenant or global?
2. **Built configurable**: Created `InsuranceConfig.java` with tenant-specific rates
3. **Made it extensible**: Used enum `AddOnType` (LOAN_INSURANCE, LIFE_INSURANCE, HEALTH_INSURANCE)
4. **Added validation**: Max 5 add-ons, max 3 opted-in, no duplicates

**Result**: When requirements changed (add GST, change rates), I only changed config - no code changes needed."

---

## 4. "How do you prioritize when everything is urgent?"

### Your Real Story:
"During Swiggy EWI launch, we had a hard deadline with multiple features pending:
- CAM Report trigger
- UPI Mandate status tracking
- State machine updates
- Partner callbacks

**My Prioritization**:
1. **Critical Path First**: What blocks the user journey? → Mandate status tracking
2. **Parallel Work**: While waiting for API specs, built configurable components
3. **Reusable Solutions**: Built status tracking that worked for both API and UPI mandates

**Communication**: Daily standups with clear blockers, proactive escalation when dependencies delayed.

**Result**: Launched on time with all critical features."

---

## 5. "What's your biggest technical achievement?"

### Your Answer:
"The Auto-Disbursal Factory Pattern implementation (LENDING-7707).

**Problem**: Adding Meesho auto-disbursal logic. Team wanted simple if-else in LoanServiceImpl.

**My Push**: I disagreed because:
- Each new partner would require modifying core loan service
- Testing would become complex
- Violated Open-Closed principle

**What I Built**:
```
AutoDisbursalFactory.java        → Selects handler based on channel
AutoDisbursalHandler.java        → Interface for partner logic
MeeshoAutoDisbursalHandler.java  → Meesho-specific implementation
```

**Business Logic**: Two-tier auto-disbursal:
- If offer amount < lower limit → auto-disbursal = true
- If offer amount >= lower limit → check loan amount <= upper limit

**Impact**: Pattern adopted, now extensible for all partners. Adding new partner = new handler class only."

---

## 6. "How do you stay updated with technology?"

### Your Answer:
"I actively use AI tools in my development workflow:

**ConfigNexus MCP Server**: I built an MCP (Model Context Protocol) server with 32 tools that integrates with Cursor AI. It lets AI agents:
- Search configurations across services
- Query databases
- Create GitLab merge requests
- Analyze lending analytics

**Daily Usage**:
- When debugging production issues, I ask Cursor to search configs
- AI reads JIRA tickets, analyzes code, creates development plans
- Reduced config lookup time from 10 mins to 30 seconds

**Learning**: I read about new AI developments, experiment with tools, and build practical applications."

---

## 7. "What would you do in your first 30 days?"

### Your Answer:
"**Week 1**: Understand the system
- Read codebase, understand UPI AutoPay flow
- Understand AMC integrations architecture
- Meet team members, understand pain points

**Week 2**: Small wins
- Pick up a bug or small feature
- Ship something to production
- Understand deployment process

**Week 3-4**: Meaningful contribution
- Take ownership of a feature
- Identify areas where my lending experience applies
- Start contributing to technical discussions

**Key Focus**: I'd look for where my NACH/mandate experience can help with UPI AutoPay reliability."

---

## 8. "How do you handle disagreements with senior engineers?"

### Your Real Story:
"In the Auto-Disbursal implementation, I disagreed with the team's approach of adding if-else conditions directly in LoanServiceImpl.

**How I Handled It**:
1. **Data, not opinions**: I showed how BusinessProofHandlerFactory already existed in our codebase and worked well
2. **Demonstrated impact**: Showed that adding 5 partners would mean 500+ lines in one method
3. **Proposed alternative**: Factory Pattern with clear benefits
4. **Offered to implement**: I said 'Let me build a POC, if it doesn't work, we go with the original approach'

**Result**: POC worked, pattern adopted. Now it's the standard for partner-specific logic."

---

## 9. "What's your weakness?"

### Your Answer:
"I sometimes over-engineer solutions. For example, when building the Insurance Consent feature, I initially designed for 10 insurance types when we only needed 3.

**How I'm Improving**:
- I now ask 'What's the minimum viable solution?'
- I build for current requirements with hooks for extension
- I time-box design discussions

**Example**: In the race condition fix, I could have built a complex distributed lock system. Instead, I used simple retry with exponential backoff - solved the problem with 50 lines instead of 500."

---

## 10. "Where do you see yourself in 3 years?"

### Your Answer:
"In 3 years, I want to be a technical leader who:

1. **Owns critical systems**: Like I own NACH/mandate systems at PayU, I want to own core payment infrastructure at Bachatt

2. **Mentors engineers**: I've already started - I created development workflow rules that help junior engineers follow patterns

3. **Influences product**: I want to be in rooms where product decisions are made, bringing technical perspective

4. **Builds for scale**: Bachatt's daily savings model will grow. I want to be the person who ensures the system scales."

---

## 🎯 FOUNDER-SPECIFIC SIGNALS THEY LOOK FOR

| Signal | How You Demonstrate |
|--------|---------------------|
| **Ownership** | "I pushed back and proposed Factory Pattern" |
| **Velocity** | "1,066+ commits, multiple partner integrations" |
| **Judgment** | "I prioritized critical path during Swiggy launch" |
| **Learning** | "Built ConfigNexus MCP with AI tools" |
| **Impact** | "Race condition fix = zero failures after" |
| **Culture Fit** | "I want startup impact, not corporate comfort" |

---

## ❌ RED FLAGS TO AVOID

1. **Don't badmouth PayU**: "Great learning, but want more impact"
2. **Don't sound desperate**: "Bachatt's mission aligns with my goals"
3. **Don't be vague**: Use specific commits, JIRA IDs, numbers
4. **Don't oversell**: "I made mistakes, here's what I learned"
5. **Don't be passive**: "I pushed back", "I proposed", "I built"

---

## ✅ GREEN FLAGS TO SHOW

1. **Ownership**: "I owned the entire Insurance Consent feature"
2. **Technical depth**: "Root cause was cache returning stale data"
3. **Business understanding**: "This impacted loan disbursement SLA"
4. **Collaboration**: "I convinced the team by showing existing patterns"
5. **Growth mindset**: "I learned to check dependency trees"

---

**Remember: Founders want people who ACT LIKE OWNERS, not employees waiting for instructions.**

**Good luck! 🚀**
