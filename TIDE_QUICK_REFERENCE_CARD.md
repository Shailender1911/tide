# 🚀 Tide Interview - Quick Reference Card

## 🏆 PRIMARY: DLS NACH Service

### **30-Second Pitch**
> "I designed and built the DLS NACH Service from scratch - a microservice handling NACH mandate creation for multiple types (UPI, API, Physical) using Strategy and Factory patterns. Integrated with Digio platform, uses Kafka for async webhook delivery, and HMAC-SHA256 for callback security."

### **Key Points**
| Aspect | Details |
|--------|---------|
| **Patterns** | Strategy (NACH types), Factory (runtime selection) |
| **NACH Types** | UPI Mandate, API Mandate (e-NACH), Physical NACH |
| **Integration** | Digio platform |
| **Async** | Kafka for guaranteed webhook delivery |
| **Security** | HMAC-SHA256 callback validation |
| **Multi-tenant** | Supports multiple lending partners |

### **Why Strategy Pattern?**
- Different NACH types = Different processing logic
- Open/Closed principle - Add new types without modifying existing code
- Each strategy independently testable

### **Why Factory Pattern?**
- Runtime selection of appropriate strategy
- Decouples strategy creation from usage
- Easy to add new NACH types

---

## 📈 SECONDARY: State Machine

### **30-Second Pitch**
> "Designed state machine with 180+ application stages automating loan workflows from creation to disbursement. Implemented read-write separation achieving 10x query improvement, batch processing and rate-limiting reducing server load by 40%."

### **Key Metrics**
| Metric | Result |
|--------|--------|
| **Query Performance** | 10x improvement (500ms → 50ms) |
| **Server Load** | 40% reduction |
| **Partner Onboarding** | 30% faster |
| **Application Stages** | 180+ automated stages |

### **How Read-Write Separation?**
1. Custom `@ReadOnlyDataSource` annotation
2. AOP aspect routes to read replica
3. Separate connection pools for read/write
4. 80% traffic goes to replicas

---

## 🔧 SUPPORTING: Orchestration

### **Key Enhancements**
| Feature | Impact |
|---------|--------|
| **Redis Caching** | 20% latency reduction |
| **Webhook Retry** | 20% reliability improvement |
| **Partner Integrations** | GPay, PhonePe, BharatPe, Paytm, Swiggy |

### **Redis Caching Flow**
1. Check cache for validated token
2. If miss → Call HUB → Cache result (5 min TTL)
3. 85% cache hit ratio

### **Webhook Retry**
- Exponential backoff (2^n × 60 seconds)
- Max 5 attempts over 2 days
- Dead letter queue for failures

---

## 🎯 Design Patterns Summary

### **Strategy Pattern**
```
Interface → Multiple Implementations
   ↓              ↓
NachService → UPIMandateStrategy
              APIManadateStrategy
              PhysicalNachStrategy
```

### **Factory Pattern**
```
Client → Factory.getStrategy(type) → Appropriate Strategy
```

### **Why Both?**
- Factory **creates** the right strategy
- Strategy **executes** the type-specific logic

---

## ❓ Quick Q&A

**Q: Why Strategy for NACH?**
> Different NACH types have completely different workflows. Strategy allows each type to have its own implementation while sharing a common interface.

**Q: How do you secure callbacks?**
> HMAC-SHA256 signature validation + timestamp check to prevent replay attacks + IP whitelisting.

**Q: How did you achieve 10x improvement?**
> Read-write separation with read replicas. 80% of read traffic goes to replicas, freeing primary for writes.

**Q: How do you ensure webhook delivery?**
> Kafka for async processing + exponential backoff retry + dead letter queue for failures.

**Q: Challenge in NACH service?**
> Multiple NACH types with different flows. Solved with Strategy pattern - each type has own strategy, Factory selects at runtime.

---

## 📊 Numbers to Remember

| Project | Metric | Value |
|---------|--------|-------|
| NACH Service | NACH Types | 3 (UPI, API, Physical) |
| State Machine | Stages | 180+ |
| State Machine | Query Improvement | **10x** |
| State Machine | Server Load | **40% ↓** |
| State Machine | Onboarding Time | **30% ↓** |
| Orchestration | API Latency | **20% ↓** |
| Orchestration | Webhook Reliability | **20% ↑** |

---

## 🏗️ Architecture Keywords

- **DLS NACH**: Strategy, Factory, Kafka, Digio, HMAC-SHA256, Multi-tenant
- **State Machine**: 180+ stages, Read-Write Separation, Batch Processing, Rate Limiting
- **Orchestration**: API Gateway, Redis Caching, OAuth2, Webhook Retry, Partner Integration

---

## 💡 Interview Tips

1. **Start with problem** → What was broken?
2. **Explain solution** → High-level architecture
3. **Go deep on patterns** → Why Strategy/Factory?
4. **Show metrics** → 10x, 40%, 20%
5. **Discuss challenges** → Bank validation, security, reliability

**Remember**: You built NACH service from scratch. Show ownership and technical depth!

---

**Good luck! 🚀**

