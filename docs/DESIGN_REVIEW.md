# Branchloom UX Design Review

This review records the current UX risks and establishes the checklist for
future UI work. It complements the implementation-level rules in
[`DESIGN_STANDARDS.md`](DESIGN_STANDARDS.md). The goal is maintainable UX:
patterns should be easy for users to learn and easy for contributors or agents
to apply consistently.

## Executive Summary

Branchloom has a coherent foundation: FlatLaf supplies the look and feel,
`Theme` centralizes typography and semantic colors, `UiConstants` defines a 4
px spacing scale, and `UiFactory` centralizes common Swing behavior. The main
maintenance risk is allowing new views and panels to bypass those foundations
with local dimensions, colors, borders, keyboard handling, or state treatments.

The most important rule is therefore **reuse the existing FlatLaf-aligned
pattern before creating a new one**. Consistency across views is a usability
feature, not a cosmetic preference: it reduces relearning, makes keyboard
behavior predictable, and lets the codebase evolve through a small number of
shared helpers.

## Findings And Priorities

### P0: Core interaction and status accuracy

These issues can prevent a user from completing an action or can cause an
incorrect decision:

- **Inconsistent button activation.** Enter and Space must activate buttons and
  dialog actions consistently. Use `UiFactory` button helpers and track the
  related work in [#34](https://github.com/klcodanr/branchloom/issues/34).
- **Keyboard-inaccessible action menus.** Right-side and context actions must
  be reachable, navigable, and return focus correctly. Follow
  `UiFactory.showPopupMenu(...)` and track [#32](https://github.com/klcodanr/branchloom/issues/32).
- **Unreliable pull-request status.** Conflicted and already-merged pull
  requests must not be presented as ordinary open work. Track [#37](https://github.com/klcodanr/branchloom/issues/37).
- **Missing focus indicators.** Focus must remain visible for every interactive
  control, including icon buttons and menu invokers. Do not disable FlatLaf or
  Swing focus painting to improve appearance.

### P1: Consistency and comprehension

These issues increase cognitive load and make the product harder to maintain:

- **Inconsistent padding.** Use the documented 4 px scale and semantic aliases
  in `UiConstants`; do not add view-local pixel values. This addresses [#69](https://github.com/klcodanr/branchloom/issues/69).
- **Weak location context.** Main content should identify the current project,
  session, or view context. A breadcrumb is not required in every panel, but a
  user should be able to tell where an action applies.
- **Confusing terminal numbering.** Auto-numbered terminals should expose a
  meaningful project/session context and support predictable tab selection and
  closing. A number alone is not a durable identity.
- **Inconsistent empty states.** Distinguish no data, not yet loaded, load
  failure, and no search results. Use shared loading and empty-state patterns.
- **Ambiguous muted text.** Disabled controls and secondary information must
  not look identical. Disabled text communicates unavailable interaction;
  secondary text communicates context and must remain readable.
- **Small close targets.** Terminal tab close actions need a practical target,
  visible focus, an accessible name, and a keyboard alternative. Do not trade
  action discoverability for a few pixels of density.

## FlatLaf And Pattern Governance

FlatLaf is the baseline contract for Branchloom's UI. Existing theme defaults
and component properties should be treated as product decisions. New code
should use standard Swing components and let the active FlatLaf theme provide
the visual states. Branchloom-specific helpers should only add semantics that
FlatLaf cannot provide, such as the application's page/card borders, spacing
roles, status colors, shared icons, or keyboard conventions.

Before changing a view or panel:

1. Find a neighboring component with the same job.
2. Compare its construction, border, layout gaps, action placement, typography,
   enabled state, and loading/empty/error behavior.
3. Reuse `UiFactory`, `UiConstants`, `Theme`, and `UiIcons` before adding code.
4. If the existing pattern is wrong, fix the shared pattern and migrate the
   affected callers rather than creating a competing local version.
5. Review the complete change across all views and panels that use the pattern.

This governance prevents “almost the same” buttons, cards, dialogs, and status
labels from accumulating. It also gives coding agents a concrete search order
and a safe default when a requirement is underspecified.

## Design Consistency Checklist

### Structure and spacing

- [ ] The view has one clear owner for page margin, section padding, and card padding.
- [ ] Spacing uses the documented 4, 8, 12, 16, 20, or 24 px values.
- [ ] Semantic aliases such as `PAGE_MARGIN`, `SECTION_PADDING`, and
  `COMPONENT_GAP` are preferred over raw constants.
- [ ] Sibling controls use consistent gaps and align to a common baseline.
- [ ] A child panel does not duplicate its parent's outer margin.
- [ ] Long labels and narrow windows do not cause clipped or overlapping content.

### FlatLaf and visual language

- [ ] Standard controls inherit the active FlatLaf theme.
- [ ] Colors come from `UIManager` or `Theme`, not hard-coded theme-specific RGB values.
- [ ] Fonts come from `Theme.font(...)` or `Theme.terminalFont(...)`.
- [ ] Borders, arcs, and component heights match existing shared defaults.
- [ ] Icons come from the shared Lucide-based `UiIcons` set and use consistent sizing.
- [ ] Light, Dark, Mac Dark, and IntelliJ themes remain legible.

### Interaction and accessibility

- [ ] Every action is reachable without a mouse.
- [ ] Enter and Space behavior matches equivalent buttons elsewhere.
- [ ] Focus is visible and tab order is logical.
- [ ] Icon-only controls have an accessible name and tooltip.
- [ ] Menus can be opened, navigated, activated, and dismissed by keyboard.
- [ ] Status is not communicated by color alone.
- [ ] Dialogs provide a clear default action, cancel action, and Escape behavior.

### State and feedback

- [ ] Loading is distinct from empty and error states.
- [ ] Empty states explain what is absent and what the user can do next.
- [ ] Failures explain the problem and provide recovery when possible.
- [ ] Disabled controls look unavailable but remain distinguishable from muted context text.
- [ ] Destructive actions are visually and spatially distinct from routine actions.
- [ ] Background work provides progress or status without blocking unrelated navigation.

### Maintainability

- [ ] The implementation reuses the closest existing view or panel pattern.
- [ ] New reusable behavior is added to the appropriate shared helper, not copied locally.
- [ ] UI code reports user intent; services and coordinators own external work and state changes.
- [ ] Deterministic validation, formatting, and branching have focused tests.
- [ ] The change documents intentional deviations from these standards.

## UX Testing Guidelines

Every meaningful UI change should be tested at the level appropriate to its
risk. The following scenarios are the minimum regression pass for a new view,
dialog, panel, or action group:

1. Start with an empty, loading, populated, and failure state where those states
   apply.
2. Navigate the feature using only the keyboard. Verify focus visibility, tab
   order, Enter/Space activation, Escape dismissal, and menu arrow-key behavior.
3. Try long project, session, branch, pull-request, and error-message text.
4. Resize to a narrow usable window and verify that actions remain discoverable
   and content remains readable.
5. Run the feature with each supported FlatLaf theme and inspect foreground,
   background, border, focus, hover, disabled, and status colors.
6. Confirm that a screen reader or accessibility inspection tool exposes useful
   names and roles for custom or icon-only controls.
7. Revisit adjacent views and panels to make sure the new pattern did not create
   a competing version of an existing interaction.

Headless tests should cover pure validation, state mapping, keyboard action
configuration, and formatting logic. Display-backed tests are appropriate for
layout and integration behavior and should run through the configured
integration-test environment. Follow the build commands in
[`DEVELOPMENT.md`](DEVELOPMENT.md).

## Definition Of Done For UX Changes

A UX change is ready for review when:

- The user-facing behavior is described in terms of intent and states.
- The implementation follows `DESIGN_STANDARDS.md` and existing FlatLaf patterns.
- Keyboard, focus, accessible naming, and theme behavior have been checked.
- The design consistency checklist has been applied to every affected view or panel.
- Tests cover the new deterministic behavior and the required Gradle checks pass.
- Any intentional exception is documented with its reason and scope.
