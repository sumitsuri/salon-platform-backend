# AI gateway

A gateway between people and language models, in the spirit of an API gateway: every chat passes through it on the way
in and on the way out, so security, compliance and cost rules live in one place instead of in each feature.

**Today** it protects one feature, Ask Antra, as an in-process library. **Later** it is meant to be lifted out into its
own service that any chat with any AI platform (ChatGPT, Claude, Gemini, internal tools) can be routed through.

## The one rule: it depends on nothing in the application

Everything under `com.salonplatform.aigateway` may use the JDK, Spring and Jackson-free plain Java, and nothing from the
rest of `com.salonplatform`. `ModuleIndependenceTest` fails the build if that is broken. The application plugs in through
three small interfaces:

| Interface | What the application provides | Today |
|---|---|---|
| `Profile` (a bean) | The rules for one chat surface: vocabulary, refused intents, fixed answers, sensitive-data actions, budgets | `AskAntraProfile` |
| `GatewayLedger` | Where decisions and costs are recorded and read back | `JdbcGatewayLedger` (Postgres table `ai_gateway_events`); in-memory by default |
| `ScopeClassifier` | An optional second opinion on questions the rules cannot place | `LlmScopeClassifier` (Claude Haiku, a fraction of a paisa per call) |

Nothing here knows about salons, tenants' tables or HTTP. Subjects are plain strings (`tenantId`, `userId`, `role`).

## What happens to a request

`AiGateway.inspectInbound(request)` runs these checks, cheapest first, so a refusal costs nothing:

1. **Normalise** the text: Unicode folding, zero-width and control characters removed (so a secret cannot hide).
2. **Cool-down**: too many refused attempts within an hour pauses that person (`LIMIT / COOLDOWN`).
3. **Prompt injection**: weighted signals ("ignore previous instructions", "reveal your system prompt", chat-template tokens,
   jailbreak modes). Forged chat-template tokens in stored "assistant" turns are caught too, since the client sends history.
4. **Secrets and personal data**, over the whole conversation: API keys and tokens, JWTs, passwords given in a sentence, private
   keys, connection strings, card numbers (Luhn), Aadhaar (Verhoeff), PAN, bank accounts, phone numbers, emails.
   Credentials and cards are **blocked** (and the browser is told to forget the message); personal data is **redacted**.
5. **Length and topic** (profiles with a scope guard): greetings and "who are you" are answered from fixed text; general-assistant
   requests (code, poems, translation, general knowledge, news, homework, recipes, ...) are refused; a question with a strong domain
   word, two weak ones, or one of the subject's own names is on topic; the rest is *ambiguous*.
6. **Budgets**: questions per person per day, per business per day, spend per business per day and per month (in rupees, from token
   usage and a price table).
7. **Classifier** for ambiguous questions (only now, so refused and over-budget requests never pay for it). No signal at all and no
   classifier: refused. One weak signal and no classifier: allowed, because refusing a real question is worse than a cheap refusal
   by the main model.

Then, around the model call: `sanitizeToolResult` (redacts sensitive data in tool output before the model sees it),
`inspectOutbound` (redacts secrets in the answer, replaces an answer that recites the hidden instructions) and `recordUsage`
(tokens and cost against the business).

## The audit trail

One row per decision in `ai_gateway_events`: who, what was decided, why, which *kinds* of sensitive data were seen, tokens, rupees,
latency. **Never message text.** `ModuleIndependenceTest`'s sibling, `AiGatewayTest.theAuditTrailNeverHoldsTheMessageText`, guards that.
Platform admins read per-business totals at `GET /api/v1/platform/ai-gateway/usage?days=30`; owners read their allowance at
`GET /api/v1/copilot/usage`.

## Adding another chat surface

1. Define a `Profile` bean (see `AskAntraProfile`) and pick a name.
2. Build a `GatewayRequest` with that name, the subject, the model and the messages, and call the four methods above.
3. Map `InboundDecision.Kind`: `ALLOW` send `messages()` on; `BLOCK` or `ANSWERED` show `userMessage()`; `LIMIT` answer "too many requests".

`Profile.protectionOnly(name)` gives secrets, personal-data and injection protection with no topic rule and no limits, which is what a
general-purpose chat (an employee using any AI tool) would start from.

## Honest limits

- Detection is rule-based. It will miss secret formats it has no rule for, and it does not recognise personal names or addresses in
  free text. It is a strong first line, not a complete DLP product.
- The topic guard is deterministic plus a small-model classifier. A determined user can sometimes pad a general question with business
  words; the system prompt, the daily and monthly spend caps and the cool-down are the backstops.
- Limits are read from the ledger per request, so two simultaneous requests can both pass just under a cap.
- A secret in a model's *answer* is caught after streaming ends; the browser is then told to replace the text.
- Vocabulary is English and Hinglish. Other Indian languages rely on the classifier.
- No retention job for `ai_gateway_events` yet, and no platform screen for the usage API.

## Becoming a standalone service

The seams are already the right ones: `AiGateway` is a pure facade over value types, so a service would expose the same four
operations over HTTP (`/inbound`, `/tool-result`, `/outbound`, `/usage`), keep its own ledger, and load profiles from configuration
instead of Spring beans. Moving the package into its own Maven module is mechanical because nothing else is imported.
