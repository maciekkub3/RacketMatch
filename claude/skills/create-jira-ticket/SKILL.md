---
title: "jira-tickets: Creating and managing JIRA tickets via CLI"
name: jira-tickets
description: JIRA ticket templates (User Story, Bug) + JIRA CLI documentation
tags:
  - jira
  - project-management
custom_fields:
  skill_category: utility
  development_status: active
---

# jira-tickets

## When to use this skill

- Creating new tickets (User Story, Bug)
- Viewing and editing existing tickets
- Searching tickets in JIRA
- Changing ticket status

---

## JIRA CLI - Key Commands

### Viewing tickets

```bash
# List recent tickets
jira issue list

# List with limit
jira issue list --paginate 20

# Filter by status
jira issue list -s "In Progress"
jira issue list -s "Open" -s "In Development"

# Filter by type
jira issue list -t Story
jira issue list -t Bug

# Filter by priority
jira issue list -y High

# Text search
jira issue list "keyword"

# My tickets (assigned to me)
jira issue list -a $(jira me)

# Tickets created by me
jira issue list -r $(jira me)

# Combined filters
jira issue list -t Bug -s "Open" -y High

# Display in text mode (no interactive UI)
jira issue list --plain

# Export to CSV
jira issue list --csv > tickets.csv

# Raw JSON
jira issue list --raw
```

### Ticket details

```bash
# View ticket
jira issue view {PROJECT_KEY}-12345

# With comments
jira issue view {PROJECT_KEY}-12345 --comments 5

# Raw JSON
jira issue view {PROJECT_KEY}-12345 --raw

# Open in browser
jira open {PROJECT_KEY}-12345
```

### Creating tickets

```bash
# Interactive creator
jira issue create

# Full command with parameters
jira issue create \
  -t Story \
  -s "[Platform] Add feature description" \
  -b "h2. Context
Description of the task...

h2. Acceptance Criteria
* Criterion 1
* Criterion 2" \
  -y Medium \
  -l mobile \
  -l feature

# From template file
jira issue create -t Story -s "Summary" --template /path/to/template.txt

# Bug with high priority
jira issue create \
  -t Bug \
  -s "[Platform] [BUG] Crash description" \
  -y High \
  -l mobile \
  -l crash
```

### Editing tickets

```bash
# Interactive edit
jira issue edit {PROJECT_KEY}-12345

# Change summary
jira issue edit {PROJECT_KEY}-12345 -s "New title" --no-input

# Change description
jira issue edit {PROJECT_KEY}-12345 -b "New description" --no-input

# Add labels
jira issue edit {PROJECT_KEY}-12345 -l mobile -l urgent

# Remove label (prefix -)
jira issue edit {PROJECT_KEY}-12345 -l -urgent

# Change priority
jira issue edit {PROJECT_KEY}-12345 -y High --no-input

# Assign to person
jira issue edit {PROJECT_KEY}-12345 -a "user@example.com" --no-input
```

### Changing status

```bash
# Change status
jira issue move {PROJECT_KEY}-12345 "In Progress"
jira issue move {PROJECT_KEY}-12345 "Done"
jira issue move {PROJECT_KEY}-12345 "In Development"

# With comment
jira issue move {PROJECT_KEY}-12345 "Done" --comment "Ready for review"

# With assignment
jira issue move {PROJECT_KEY}-12345 "In Progress" -a "user@example.com"
```

### Comments

```bash
# Add comment (interactively)
jira issue comment add {PROJECT_KEY}-12345

# Add comment with content
jira issue comment add {PROJECT_KEY}-12345 -b "Comment content"
```

### Sprints

```bash
# List sprints
jira sprint list

# Tickets in current sprint
jira sprint list --current

# Tickets in previous sprint
jira sprint list --prev
```

### Assignment

```bash
# Assign ticket
jira issue assign {PROJECT_KEY}-12345 "user@example.com"

# Assign to self
jira issue assign {PROJECT_KEY}-12345 $(jira me)
```

---

## Ticket Templates

### User Story

**Summary:**
```
[Platform] {Short feature title}
```

**Description (JIRA Wiki Markup):**
```
h2. Context
{Why are we doing this? What problem are we solving?}

h2. What needs to be done
* {Task 1}
* {Task 2}

h2. Links
* Figma: [Design|https://figma.com/...]
* Documentation: [Specs|https://docs.google.com/...]

h2. Acceptance Criteria
* {Criterion 1}
* {Criterion 2}

h2. How to test
# {Step 1}
# {Step 2}
*Expected result:* {description}
```

**Optional sections** - skip if you don't have the information:
- Links - if no design/documentation available
- How to test - if AC are sufficient

---

### Bug

**Summary:**
```
[Platform] [BUG] {Short problem description}
```

**Description (JIRA Wiki Markup):**
```
h2. Problem description
{What's not working?}

h2. Steps to reproduce
# {Step 1}
# {Step 2}
# {Step 3}

h2. Expected behavior
{What should happen?}

h2. Actual behavior
{What happens instead?}

h2. Environment
* *App version:* {e.g., 5.2.1}
* *OS version:* {e.g., Android 14, iOS 17}
* *Device:* {e.g., Pixel 8, iPhone 15 Pro}
* *Environment:* {DEV/UAT/PROD}

h2. Materials
* Screenshot/Video: {link}
* Logs: {if available}

h2. How to test the fix
# {Step 1}
# {Step 2}
*Expected result:* {description}
```

**Optional sections** - skip if you don't have the information:
- Materials - if no screenshots/logs available
- How to test the fix - if reproduction steps are sufficient

---

## Example Filled Tickets

### User Story Example

**Summary:**
```
[Android] Add PESEL number validation in personal data form
```

**Description:**
```
h2. Context
Users can enter invalid PESEL numbers, causing errors during identity verification. We need client-side validation.

h2. What needs to be done
* Add PESEL format validation (11 digits)
* Add PESEL checksum validation
* Display error message below the field
* Block "Next" button when PESEL is invalid

h2. Links
* Figma: [Personal Details Design|https://figma.com/file/xxx/personal-details]
* Algorithm: [PESEL Documentation|https://docs.google.com/document/d/xxx]

h2. Acceptance Criteria
* PESEL field accepts only 11 digits
* Checksum validation matches the algorithm
* "Invalid PESEL number" message displays below field
* "Next" button is inactive when PESEL is invalid
* Validation works offline

h2. How to test
# Open Personal Details screen
# Enter invalid PESEL (e.g., 12345678901)
# Check error message and "Next" button state
# Enter valid PESEL (e.g., 44051401359)
# Check no error and active button

*Expected result:* Validation works correctly for both cases
```

---

### Bug Example

**Summary:**
```
[Android] [BUG] Crash when opening transaction history on Android 14
```

**Description:**
```
h2. Problem description
App crashes when trying to open transaction history screen on devices with Android 14. Problem doesn't occur on Android 13+.

h2. Steps to reproduce
# Log in to the app on Android 14 device
# Go to "Wallet" tab
# Click "Transaction history"
# App crashes

h2. Expected behavior
Transaction history screen should open and display the list of transactions.

h2. Actual behavior
App crashes with error:
{code}
java.lang.NullPointerException: Attempt to invoke virtual method on null object reference
{code}

h2. Environment
* *App version:* 5.2.1 (build 1234)
* *Android:* 14
* *Device:* Google Pixel 8
* *Environment:* UAT

h2. Materials
* Crash log: [^crash_log_android.txt]
* Video: [Problem recording|https://drive.google.com/file/xxx]

h2. How to test the fix
# Install new version on Android 14 device
# Execute reproduction steps
# Also verify on Android 13 (regression)

*Expected result:* Screen opens correctly without crash
```

---

## JIRA Wiki Markup - Cheatsheet

| Element | Syntax |
|---------|--------|
| Heading | `h1.` `h2.` `h3.` |
| Bullet list | `* item` |
| Numbered list | `# item` |
| Bold | `*text*` |
| Italic | `_text_` |
| Link | `[Label\|https://url]` |
| Code block | `{code}...{code}` |
| Inline code | `{{code}}` |
| Attachment | `[^filename.txt]` |
| Line | `----` |

---

## Tips

### Summary (title)
* Short and specific (max 80 characters)
* Prefix `[Platform]` for platform, `[Platform] [BUG]` for bugs
* Use action nouns: "Add", "Fix", "Update"

### Acceptance Criteria
* Specific and verifiable
* One point = one thing to check
* No implementation details

### Optional sections
* Don't add empty sections
* Skip a section if you have no content for it

---

## JIRA CLI - Known Issues and Workarounds

### Issue 1: Escaping dashes

JIRA CLI escapes dashes (`-` → `\-`) in ticket descriptions when using `-b` or `--template`.

**Solution:** Use pipe editing instead of `-b` parameter:

```bash
# 1. Create ticket without description
TICKET=$(jira issue create -t Story -s "[Platform] Title" --no-input 2>&1 | grep -oE '{PROJECT_KEY}-[0-9]+')

# 2. Add description via pipe (doesn't escape dashes)
cat << 'EOF' | jira issue edit $TICKET --no-input
h2. Goal
Description with dashes - works correctly.

h2. List
* Element A - with dash
* Element B
EOF
```

### Issue 2: Custom fields don't work via CLI

Custom fields passed via `--custom` are ignored with warning:
```
Some custom fields are not configured and will be ignored.
```

**Workaround:** Set custom fields via curl API after creating the ticket.

---

## Custom Fields Configuration

To configure custom fields for your project, you'll need to:

1. Find your field IDs via JIRA API or admin console
2. Configure them in your workflow

**Setting custom fields via curl:**

```bash
curl -s -X PUT \
  -H "Authorization: Bearer ${JIRA_API_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "fields": {
      "customfield_XXXXX": "value",
      "components": [{"id": "COMPONENT_ID"}]
    }
  }' \
  "https://{YOUR_JIRA_DOMAIN}/rest/api/2/issue/$TICKET"
```

---

## Full Workflow for Creating a Ticket

```bash
#!/bin/bash
# Creating a ticket with all fields

# 1. Create ticket
TICKET=$(jira issue create -t Story -s "[Platform] Ticket title" --no-input 2>&1 | grep -oE '{PROJECT_KEY}-[0-9]+')
echo "Created: $TICKET"

# 2. Add description via pipe
cat << 'EOF' | jira issue edit $TICKET --no-input
h2. Goal
Task description - what we're doing and why.

h2. What needs to be done
* Task 1
* Task 2

h2. Acceptance Criteria
* Criterion 1
* Criterion 2
EOF

# 3. Set custom fields via API (if needed)
# curl -s -X PUT \
#   -H "Authorization: Bearer ${JIRA_API_TOKEN}" \
#   -H "Content-Type: application/json" \
#   -d '{ "fields": { ... } }' \
#   "https://{YOUR_JIRA_DOMAIN}/rest/api/2/issue/$TICKET"

echo "Ticket ready: https://{YOUR_JIRA_DOMAIN}/browse/$TICKET"
```

---

## Configuration Placeholders

Replace these placeholders with your project values:

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `{YOUR_JIRA_DOMAIN}` | Your JIRA instance URL | `jira.example.com` |
| `{PROJECT_KEY}` | Your project key | `PROJ`, `MOBILE` |
| `{JIRA_API_TOKEN}` | Environment variable for API token | Set in your shell |
