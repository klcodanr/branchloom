# Branchloom Design Standards

This document is the implementation guide for Branchloom's Swing UI. It is
intended for contributors, coding agents, and reviewers. It describes the
smallest set of shared rules that keeps new views and panels feeling like one
application instead of a collection of independently styled screens.

## Design Principles

1. **Prefer the existing pattern over a local improvement.** Before adding a
   control, find the closest existing view, panel, dialog, or `UiFactory`
   method. Copy its structure and extend the shared helper only when the
   behavior is genuinely reusable.
2. **Let FlatLaf do the visual work.** FlatLaf is the application's look and
   feel, not merely a dependency. Use its component defaults, colors, fonts,
   focus treatment, arcs, and sizing. Do not hard-code colors or repaint
   standard controls to resemble a different toolkit without a documented
   product reason.
3. **Use semantic values, not unexplained numbers.** Layout dimensions belong
   in `UiConstants`; typography belongs in `Theme`; common components belong in
   `UiFactory`; shared icons belong in `UiIcons`.
4. **Consistency is part of usability.** A button, menu, empty state, loading
   state, status color, or panel border must behave and look the same wherever
   it appears. A view-specific exception must be intentional and reviewable.
5. **Make every state understandable.** Loading, empty, error, disabled,
   selected, modified, and completed states need explicit visual and textual
   treatment. Never rely on color alone or leave a blank panel that could mean
   either “no data” or “still loading.”
6. **Design for keyboard and screen readers.** Every action must be reachable
   with the keyboard, show focus, have a meaningful accessible name, and use
   the same activation behavior as equivalent controls elsewhere.

## Source Of Truth

| Concern | Source of truth | Use it for |
| --- | --- | --- |
| Look and feel | FlatLaf and `Theme` | Colors, fonts, component defaults, theme selection |
| Spacing and dimensions | `UiConstants` | Gaps, margins, padding, insets, shared component sizes |
| Common Swing construction | `UiFactory` | Buttons, labels, forms, borders, loading and empty states |
| Icons | `UiIcons` and the bundled Lucide SVGs | Consistent icon family, size, and theme-aware color |
| Screen composition | Existing views and panels | Layout hierarchy, action placement, state transitions |

When a requirement cannot be met by these sources, document why and update the
appropriate shared source rather than adding a one-off value to a view.

## Spacing Scale

Branchloom uses a 4 px base unit. These values are the complete standard scale:

| Constant | Pixels | Semantic meaning |
| --- | ---: | --- |
| `SPACING_XS` | 4 | Tight relationship inside a control or between a label and its value |
| `SPACING_SM` | 8 | Standard control padding, card padding, and compact sibling gaps |
| `SPACING_MD` | 12 | Gap between related controls or rows |
| `SPACING_LG` | 16 | Page margin, section padding, and primary group separation |
| `SPACING_XL` | 20 | Spacious loading or emphasis treatment |
| `SPACING_2XL` | 24 | Large empty/loading state inset or major visual separation |

Use the semantic aliases when the role is known:

| Alias | Current value | Meaning |
| --- | ---: | --- |
| `PAGE_MARGIN` | 16 | Outer inset of a view's main content |
| `SECTION_PADDING` | 16 | Inset around a logical section or form |
| `COMPONENT_GAP` | 12 | Space between related controls or stacked content |
| `CONTENT_PADDING` | 8 | Inset around content inside a control or form cell |
| `TAB_INSET` | 8 | Inset for terminal/tab content |
| `CARD_PADDING` | 8 | Inset inside a card |
| `TERMINAL_PADDING` | 8 | Inset around terminal content |

The scale is intentionally limited. Do not introduce `13`, `18`, or another
nearby value because a local layout looks slightly better. If a repeated
pattern needs a new semantic role, add an alias to `UiConstants`; if the scale
itself is insufficient, discuss that change in the review rather than silently
expanding it.

## FlatLaf Alignment

FlatLaf provides the baseline for buttons, fields, lists, tables, tabs, menus,
dialogs, scroll panes, focus indicators, and theme colors. Branchloom currently
applies shared defaults in `Theme.applySwingDefaults()`, including component
arcs, button height and margin, text-field margins, text-area margins, and
scrollbar width.

Follow these rules:

- Create standard controls with Swing and `UiFactory`; allow the active FlatLaf
  theme to supply their background, foreground, border, font family, hover,
  pressed, disabled, and focus visuals.
- Read theme colors from `UIManager` or the color helpers in `Theme`. Do not
  embed light-theme colors that become unreadable in Dark or Mac Dark mode.
- Keep the FlatLaf component properties already established by the factory.
  For example, toolbar icon buttons use the `toolBarButton` type, and links use
  the `borderless` type.
- Use `Theme.font(...)` for application typography. Use `Theme.terminalFont(...)`
  only for terminal or code content.
- Use `UiIcons` for actions and keep icons at the established 16 px size. Every
  icon-only control needs a tooltip and accessible name.
- Do not use `setOpaque(true)`, custom painting, or a custom border to override
  FlatLaf unless the component is a documented Branchloom surface such as a
  card, content area, or terminal.
- Do not use disabled foreground as a generic “less important” color. Disabled
  controls must be unavailable; secondary information should use a deliberate
  secondary treatment that remains readable.

Useful references:

- [FlatLaf documentation](https://www.formdev.com/flatlaf/)
- [FlatLaf properties](https://www.formdev.com/flatlaf/properties/)
- [Swing accessibility tutorial](https://docs.oracle.com/javase/tutorial/uiswing/misc/access.html)

## Layout Patterns

### View shell

Use one outer content surface with `UiFactory.pageBorder()` when the view owns
the page margin. A view header should have a clear title, optional location or
context, and actions grouped consistently on the same edge as comparable views.
Do not add a second page margin inside a child panel.

```java
final JPanel content = UiFactory.panel();
content.setBorder(UiFactory.pageBorder());
content.setLayout(new BorderLayout(UiConstants.COMPONENT_GAP, UiConstants.COMPONENT_GAP));
content.add(header, BorderLayout.NORTH);
content.add(body, BorderLayout.CENTER);
```

### Sections and cards

Use `UiFactory.sectionBorder()` for a logical group and `UiFactory.cardBorder()`
for a compact card. A section owns its internal padding; its parent owns the
gap between sections. Avoid padding both the section and every child unless a
child is a distinct surface.

### Forms

Use `UiFactory.form(...)` for label/control rows and
`UiFactory.formConstraints()` when a custom form is unavoidable. Keep labels
aligned, let the control column grow, and use `CONTENT_PADDING` rather than
handwritten insets.

```java
final JPanel form = UiFactory.form(
        "Agent", agentComboBox,
        "Prompt", promptField);
```

### Toolbars and action groups

Use text buttons for primary or destructive actions when space permits. Use
`UiFactory.iconButton(icon, accessibleName)` for compact toolbar actions. Keep
related actions together, separate destructive actions from routine actions,
and do not make an icon-only button the only discoverable way to perform a
critical operation.

### Loading and empty states

Use `UiFactory.loading(...)` for a screen or region that is awaiting data and
`UiFactory.inlineLoading(...)` for a small status region. Use
`UiFactory.empty(title, detail)` when the data is known to be absent. An error
state should explain what failed and offer a recovery action where possible.

The message must describe the state, not the implementation: “Loading pull
requests...” is useful; “Calling GitHub...” is not necessarily useful to a
user.

## Interaction And Accessibility

- Use `UiFactory.button(...)`, `UiFactory.iconButton(...)`, and
  `UiFactory.link(...)` so Enter and Space activation, focus painting, and
  accessible naming remain consistent.
- Give every icon-only button a tooltip and accessible name. The name should
  describe the action, such as “Refresh pull requests,” not “Refresh icon.”
- Keep focus visible. Never disable focus painting to remove a visual artifact.
- Make popup menu actions reachable with arrow keys and return focus to the
  invoking control when the menu closes. Follow `UiFactory.showPopupMenu(...)`.
- Provide a keyboard path for every mouse action, including tab close buttons,
  context menus, tree actions, and dialog actions.
- Use labels, icons, and text together for status where a color-only signal
  could be ambiguous. Success, warning, danger, merge, and queue colors should
  come from `Theme` helpers.
- Ensure dialogs have a clear default action, a cancel action, and Escape
  behavior consistent with `UiFactory.configureDialogCloseOnEscape(...)`.
- Preserve tab order and do not place a text area or custom component in the
  way of normal traversal without explicitly configuring traversal keys.

## Agent Implementation Workflow

Agents making UI changes should follow this sequence:

1. Read this document, `docs/DESIGN_REVIEW.md`, and the target view before
   editing.
2. Identify the closest existing pattern and name it in the implementation
   notes or pull request description.
3. Search `UiFactory`, `UiConstants`, `Theme`, and `UiIcons` before introducing
   a new helper, dimension, color, font, or icon.
4. Implement the smallest change that reuses the existing pattern across the
   complete state lifecycle: initial, loading, success, empty, error, disabled,
   and recovery states as applicable.
5. Check light, dark, Mac Dark, and IntelliJ themes. Check narrow window sizes
   and long user-provided names or messages.
6. Exercise the change with keyboard-only navigation and verify focus,
   activation, accessible names, and popup-menu behavior.
7. Add or update headless tests for deterministic behavior and run the required
   Gradle checks described in `docs/DEVELOPMENT.md`.

Do not add a new UI abstraction merely to avoid reading the neighboring code.
Shared helpers are justified when they enforce a repeated behavior, not when
they hide a one-line layout choice.

## Examples

### Correct

```java
final JButton refresh = UiFactory.iconButton(UiIcons.refresh(), "Refresh pull requests");
refresh.addActionListener(event -> refreshPullRequests());

final JPanel body = UiFactory.panel();
body.setBorder(UiFactory.sectionBorder());
body.setLayout(new BorderLayout(UiConstants.COMPONENT_GAP, UiConstants.COMPONENT_GAP));
```

### Incorrect

```java
final JButton refresh = new JButton(new ImageIcon("refresh.png"));
refresh.setMargin(new Insets(3, 7, 3, 7));
refresh.setBackground(new Color(230, 230, 230));

final JPanel body = new JPanel();
body.setBorder(new EmptyBorder(13, 19, 13, 19));
```

The first example inherits FlatLaf behavior, shared icon sizing, keyboard
activation, focus painting, accessibility naming, and semantic spacing. The
second example creates a theme-specific, inaccessible, locally sized control.

## Review Checklist

- [ ] The closest existing view or panel pattern was identified and reused.
- [ ] Standard controls are created through `UiFactory` where a matching helper exists.
- [ ] Dimensions use `UiConstants` semantic aliases or an explicitly justified new alias.
- [ ] Typography uses `Theme` and colors use `UIManager` or `Theme` helpers.
- [ ] Icons use `UiIcons`; icon-only controls have tooltips and accessible names.
- [ ] FlatLaf hover, pressed, disabled, and focus states remain visible.
- [ ] The layout uses one clear owner for each margin and padding boundary.
- [ ] Loading, empty, error, disabled, and recovery states are distinguishable.
- [ ] The feature works with keyboard-only navigation and sensible tab order.
- [ ] Long text, narrow windows, and all supported themes were considered.
- [ ] Tests cover new branching or validation behavior, and `gradle check` passes.
