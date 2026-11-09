---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0009"
category: playbooks
title: "Agent Workflow Playbooks"
status: planned
owner: ""
created: '2026-10-28'
updated: '2026-11-05'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, playbooks]
---

# Agent Workflow Playbooks

> **(verify)** — team-owned; not extractable from the codebase
>
> Expected answers when this section is filled:
> - [ ] Recipe: build + install debug APK on the Nova
> - [ ] Recipe: capture new `es_log.txt` fixtures from the device
> - [ ] Recipe: add a new naming rule + test to `NameResolver`
>
> Look at: `PLAN.md` §7, §9.

## Device Verification Workflow

- **Do:** run every device check once as a dry run right after the first install, fix everything it finds, and only then install once more for the recorded pass. **Do not** record evidence check-by-check as you go — each install stops the service and needs a new consent tap, the menu has to be relaunched, and a fix made between checks invalidates evidence already captured, since the subject under test is the whole checkout, not each individual check.
