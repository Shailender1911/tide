# Original Problematic Code

This is the exact code provided during the Tide Code Review interview. Your task is to identify all issues and suggest improvements.

## Context

> You need to review the REST API written in Java. Consider that this PR is raised by a very junior developer. Hence, assume that this code has lots of issues and improvement areas.

## The Code

```java
package vnd.credit.loans;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Creating a new version of the borrowing functionality that registers the loan 
 * in a 3rd party Loan Management System (wrapped in feign client service). 
 * Since we get a lot of errors from the 3rd party I've added a functionality 
 * to let our staff move the money on behalf of the user if they give us a call 
 * that something's not working properly!!!
 */
@RequestMapping("/v3/accounts/")
@RestController
public class LoanV3Controller {

    @Autowired
    public AccountService accountService;

    @Autowired
    public LoanManagementService loanManagementService;

    public static final Logger logger = LoggerFactory.getLogger(LoanV3Controller.class); 

    @PutMapping(value = "/new/{accountId}/v2/loans/borrow")
    public void borrowMoney(@PathVariable String accountId,
                            @RequestParam boolean isAdminAgent,
                            @RequestParam double loanAmount,
                            @RequestParam String sourceAccountId) {
      
        if (!isAdminAgent) {
            // for admins we don't need to check the ownership
            Account acc = accountService.getAccount(accountId);
            if (acc.getOwner() != AuthContext.getCurrentUserID()) {
                throw new InternalServerError();
            }
        }

        // make sure the user is allowed to borrow this amount
        if (loanAmount < loanManagementService.getCreditLimit(accountId)) {
      
            Account sourceAccount = accountService.getAccount(sourceAccountId);
            Account destinationAccount = accountService.getAccount(accountId);

            Optional.ofNullable(sourceAccount).orElseThrow();
            double balance = sourceAccount.getBalance();
      
            if (balance > loanAmount) {
                accountService.debit(sourceAccount, loanAmount);
                accountService.credit(destinationAccount, loanAmount);
                loanManagementService.registerLoan(new Random().nextInt(1000000), 
                    loanAmount, AuthContext.getCurrentUserID());
            } else {
                throw new InternalServerError();
            }
        }
    }
}
```

## Line-by-Line Reference

| Line | Code |
|------|------|
| 1-6 | Package declaration and imports |
| 8-13 | Class documentation (Javadoc comment) |
| 14-15 | Class annotations |
| 17-18 | AccountService field injection |
| 20-21 | LoanManagementService field injection |
| 23 | Logger declaration |
| 25 | Endpoint mapping annotation |
| 26-29 | Method signature with parameters |
| 31-37 | Admin bypass and ownership check |
| 39-40 | Credit limit check |
| 42-43 | Account retrieval |
| 45-46 | Source account null check and balance retrieval |
| 48-52 | Balance check and money transfer |
| 53-55 | Insufficient balance error handling |

## What You Should Do

1. **Identify all bugs** - Security, logic, design issues
2. **Prioritize** - Major issues have more weightage
3. **Suggest fixes** - Provide concrete improvements
4. **Reference standards** - Link to documentation where helpful

## Time Management

- **First 5-10 mins**: Read and understand the code flow
- **Next 25-30 mins**: Add comments identifying issues
- **Last 5 mins**: Review and prioritize your comments

---

See [BUGS_IDENTIFIED.md](BUGS_IDENTIFIED.md) for the complete analysis.

