# Combined Reviewer Prompt Template

Use this template when dispatching the reviewer subagent after each task implementation.

**Purpose:** Verify the implementation matches its spec (nothing more, nothing less) AND is well-built — in a single pass.

**Order is mandatory:** Check spec compliance first. Only proceed to code quality after spec is confirmed correct. This prevents quality concerns from masking missing or extra requirements.

```
Agent tool (general-purpose):
  description: "Review Task N — spec compliance + code quality"
  prompt: |
    You are reviewing an implementation for both spec compliance and code quality in a single pass.

    ## What Was Requested

    [FULL TEXT of task requirements]

    ## What Implementer Claims They Built

    [From implementer's report]

    ## CRITICAL: Do Not Trust the Report

    The implementer finished suspiciously quickly. Their report may be incomplete,
    inaccurate, or optimistic. You MUST verify everything independently by reading the actual code.

    ---

    ## PART 1 — Spec Compliance (check this first)

    Read the implementation code and verify:

    **Missing requirements:**
    - Did they implement everything that was requested?
    - Are there requirements they skipped or missed?
    - Did they claim something works but didn't actually implement it?

    **Extra/unneeded work:**
    - Did they build things that weren't requested?
    - Did they over-engineer or add unnecessary features?
    - Did they add "nice to haves" that weren't in spec?

    **Misunderstandings:**
    - Did they interpret requirements differently than intended?
    - Did they solve the wrong problem?
    - Did they implement the right feature but wrong way?

    If PART 1 has issues → list them and stop. Do not proceed to PART 2.
    The implementer must fix spec issues before quality is assessed.

    ---

    ## PART 2 — Code Quality (only if PART 1 passed)

    Review the implementation using the template at: flow-requesting-code-review/code-reviewer.md

    WHAT_WAS_IMPLEMENTED: [from implementer's report]
    PLAN_OR_REQUIREMENTS: Task N from [plan-file]
    BASE_SHA: [commit before task]
    HEAD_SHA: [current commit]
    DESCRIPTION: [task summary]

    ---

    ## Verdict

    Return ONE of:

    ✅ Approved — spec compliant, code quality acceptable

    ❌ Issues found:
    [SPEC] List any spec compliance issues with file:line references
    [QUALITY] List any code quality issues (Critical/Important/Minor)
```
