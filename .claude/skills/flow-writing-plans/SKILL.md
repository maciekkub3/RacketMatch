---
name: flow-writing-plans
description: Use when you have a spec or requirements for a multi-step task, before touching code
---

# Writing Plans

## CRITICAL CONSTRAINTS — Read Before Anything Else

**You MUST NOT call `EnterPlanMode` or `ExitPlanMode` at any point during this skill.** This skill operates in normal mode and manages its own completion flow via `AskUserQuestion`. Calling `EnterPlanMode` traps the session in plan mode where Write/Edit are restricted. Calling `ExitPlanMode` breaks the workflow and skips the user's execution choice. If you feel the urge to call either, STOP — follow this skill's instructions instead.

## Overview

Write comprehensive implementation plans assuming the engineer has zero context for our codebase and questionable taste. Document everything they need to know: which files to touch for each task, code, testing, docs they might need to check, how to test it. Give them the whole plan as bite-sized tasks. DRY. YAGNI. TDD. Frequent commits.

Assume they are a skilled developer, but know almost nothing about our toolset or problem domain. Assume they don't know good test design very well.

**Announce at start:** "I'm using the flow-writing-plans skill to create the implementation plan."

**Context:** This should be run in a dedicated worktree (created by flow-brainstorming skill).

**Save plans to:** `docs/plans/YYYY-MM-DD-<feature-name>.md`

## Context Guard — Check After Every Major Step

**After each section of the plan is written, check context usage.**
If context remaining is **less than 70%**, you MUST:
1. Save whatever has been written to disk so far
2. Run `/handoff` to generate a handoff summary
3. Tell the user: "Context is below 70%. Please clear the session and paste the handoff summary to continue."
4. STOP — do not proceed until the user confirms the new session is ready

## REQUIRED FIRST STEP: Initialize Task Tracking

**BEFORE exploring code or writing the plan, you MUST:**

1. Call `TaskList` to check for existing tasks from brainstorming
2. If tasks exist: you will enhance them with implementation details as you write the plan
3. If no tasks: you will create them with `TaskCreate` as you write each plan task

**Do not proceed to exploration until TaskList has been called.**

```
TaskList
```

## Bite-Sized Task Granularity

**Each step is one action (2-5 minutes):**
- "Write the failing test" - step
- "Run it to make sure it fails" - step
- "Implement the minimal code to make the test pass" - step
- "Run the tests and make sure they pass" - step
- "Commit" - step

## Plan Document Header

**Every plan MUST start with this header:**

```markdown
# [Feature Name] Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** [One sentence describing what this builds]

**Architecture:** [2-3 sentences about approach]

**Tech Stack:** [Key technologies/libraries]

---
```

## Task Structure

````markdown
### Task N: [Component Name]

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/codekub/golftracker/feature/ClassName.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/codekub/golftracker/feature/ExistingClass.kt:123-145`
- Test: `composeApp/src/commonTest/kotlin/com/codekub/golftracker/feature/ClassNameTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `specific behavior description`() = runTest {
    val result = function(input)
    result shouldBe expected
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*.ClassNameTest.specific behavior description"`
Expected: FAIL

**Step 3: Write minimal implementation**

```kotlin
fun function(input: InputType): OutputType {
    return expected
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*.ClassNameTest.specific behavior description"`
Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/codekub/golftracker/feature/ClassName.kt
git add composeApp/src/commonTest/kotlin/com/codekub/golftracker/feature/ClassNameTest.kt
git commit -m "feat: add specific feature"
```
````

## Remember
- Exact file paths always
- Complete code in plan (not "add validation")
- Exact commands with expected output
- Reference relevant skills with @ syntax
- DRY, YAGNI, TDD, frequent commits
- Test steps MUST follow **flow-unit-tests** skill conventions — invoke it before writing any test code in the plan

## Execution Handoff

<HARD-GATE>
STOP. You are about to complete the plan. DO NOT call EnterPlanMode or ExitPlanMode. You MUST call AskUserQuestion below. Both are FORBIDDEN — EnterPlanMode traps the session, ExitPlanMode skips the user's execution choice.
</HARD-GATE>

Your ONLY permitted next action is calling `AskUserQuestion` with this EXACT structure:

```yaml
AskUserQuestion:
  question: "Plan complete and saved to docs/plans/<filename>.md. How would you like to execute it?"
  header: "Execution"
  options:
    - label: "Subagent-Driven (this session)"
      description: "I dispatch fresh subagent per task, review between tasks, fast iteration"
    - label: "Parallel Session (separate)"
      description: "Open new session in worktree with flow-executing-plans, batch execution with checkpoints"
```

**If you are about to call ExitPlanMode, STOP — call AskUserQuestion instead.**

**If Subagent-Driven chosen:**
- **REQUIRED SUB-SKILL:** Use flow-subagent-driven-development
- Stay in this session
- Fresh subagent per task + code review

**If Parallel Session chosen:**
- Guide them to open new session on the feature branch
- **REQUIRED SUB-SKILL:** New session uses flow-executing-plans

---

## Native Task Integration Reference

Use Claude Code's native task tools (v2.1.16+) to create structured tasks alongside the plan document.

### Creating Native Tasks

For each task in the plan, create a corresponding native task:

```
TaskCreate:
  subject: "Task N: [Component Name]"
  description: |
    [Copy the full task content from the plan you just wrote — files, steps, acceptance criteria, everything]
  activeForm: "Implementing [Component Name]"
```

### Setting Dependencies

After all tasks created, set blockedBy relationships:

```
TaskUpdate:
  taskId: [task-id]
  addBlockedBy: [prerequisite-task-ids]
```

### During Execution

Update task status as work progresses:

```
TaskUpdate:
  taskId: [task-id]
  status: in_progress  # when starting

TaskUpdate:
  taskId: [task-id]
  status: completed    # when done
```

### Notes

- Native tasks provide CLI-visible progress tracking
- Plan document remains the permanent record

---

## Task Persistence

At plan completion, write the task persistence file **in the same directory as the plan document**.

If the plan is saved to `docs/plans/2026-01-15-feature.md`, the tasks file MUST be saved to `docs/plans/2026-01-15-feature.md.tasks.json`.

```json
{
  "planPath": "docs/plans/2026-01-15-feature.md",
  "tasks": [
    {"id": 0, "subject": "Task 0: ...", "status": "pending"},
    {"id": 1, "subject": "Task 1: ...", "status": "pending", "blockedBy": [0]}
  ],
  "lastUpdated": "<timestamp>"
}
```

Both the plan `.md` and `.tasks.json` must be co-located in `docs/plans/`.

### Resuming Work

Any new session can resume by running the flow-executing-plans skill with the plan path.

The skill reads the `.tasks.json` file and continues from where it left off.
