---
description: Generate continuation prompt for new session with task context
---

Generate a comprehensive handoff prompt for continuing the current work in a new Claude Code session. Include:

**Current Task Summary**:
- What we're working on (main objective)
- Current status/progress

**Work Completed**:
- List what has been implemented/fixed
- What approaches worked well
- Any important discoveries or patterns established

**What Didn't Work** (if relevant for future):
- Approaches that failed and why
- Issues encountered and resolved
- Things to avoid

**Key Files Modified/Created**:
- List all relevant file paths with brief descriptions
- Include both source files and test files
- Note any configuration changes

**Task List**:
- ✅ Completed tasks (brief list)
- ⬜ Remaining tasks to do
- Include priority/order if relevant

**Context & Decisions**:
- Any architectural decisions made
- Important context needed to continue
- Dependencies or constraints to be aware of

**Next Steps**:
- Immediate next action to take
- Any blockers or questions to address

Format the output as a ready-to-use prompt that can be pasted into a new session. After generating the prompt, execute this command to copy it to the Windows clipboard:

```bash
clip
```

Provide the prompt text both as visible output AND pipe it to clip so it's ready to paste.
