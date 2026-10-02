# Duplication Cleanup Checklist

This checklist tracks the agreed codebase cleanup. Keep the changes behavior-preserving unless a
specific item calls out a behavior or API change. Check each item only after the implementation and
its focused tests are complete.

## Scope And Guardrails

- [ ] Keep UI components focused by responsibility; do not replace `UiFactory` with another mixed utility god class.
- [ ] Preserve existing cinoibebt keyboard behavior, and sizing.
- [ ] Do not merge classes whose differences represent real domain ownership or lifecycle differences.
- [ ] Add or update focused tests for every extracted production component/helper.

## Immediate Safe Cleanup

- [ ] Remove the duplicate accessible-name and Enter-key setup in `UiFactory.button(String, Icon)` while it still exists.
- [ ] Remove the unused duplicate stream computation in `PasteSessionsDialog.submit()`.
- [ ] Replace repeated configured-agent combo-box setup with a real `AgentSelector` Swing component.
- [ ] Preserve caller-provided component names in `AgentSelector` for UI tests and accessibility.

## Remove `UiFactory`

Inventory and migration:

- [ ] Inventory every `UiFactory` method and all production/test call sites before deleting the class.
- [ ] Inventory static imports of `UiFactory.button` and `UiFactory.form`.
- [ ] Move button creation and keyboard activation behavior into a focused button component/helper.
- [ ] Move icon-button creation into a focused icon-button component/helper.
- [ ] Move link-button creation into a focused link component/helper.
- [ ] Move label and selectable-text creation into focused presentation components/helpers.
- [ ] Move page, section, content-area, and card borders into focused border definitions/helpers.
- [ ] Move form layout and `GridBagConstraints` construction into a focused form component/helper.
- [ ] Move loading and inline-loading panels into focused components.
- [ ] Move metric and empty-state panels into focused components.
- [ ] Move text-area traversal configuration into a focused focus/keyboard helper.
- [ ] Move dialog Escape handling into the shared dialog supertype or a focused dialog helper.
- [ ] Move popup-menu focus/selection behavior into a focused popup-menu helper.
- [ ] Move the custom `MenuIcon` into its own icon class.
- [ ] Update all production imports and static imports.
- [ ] Update or replace `UiFactoryTest` with tests named for the focused replacements.
- [ ] Delete `UiFactory.java` only after all references are gone.
- [ ] Verify no `UiFactory` references remain under `src`.

Recommended focused replacements are separate classes/components such as:

- `AgentSelector`
- `UiButtons` or concrete button components
- `UiLabels`
- `UiBorders`
- `FormPanel`
- `LoadingPanel` and `InlineLoadingPanel`
- `MetricPanel` and `EmptyStatePanel`
- `UiFocus`
- `UiPopupMenus`
- `MenuIcon`

Use the smallest structure that keeps each responsibility clear. Do not create a new catch-all
utility class containing all of these methods.

## Dialog Consolidation

- [ ] Finish the shared modal form-dialog supertype for `NewSessionDialog`, `BulkSessionDialog`, `PasteSessionsDialog`, `ReviewDialog`, and `ImportProjectDialog`.
- [ ] Centralize owner, title, application modality, Cancel behavior, primary-button behavior, footer alignment, Escape handling, default-button setup, packing, and owner-relative positioning.
- [ ] Align constructors around one consistent initialization pattern without retaining unnecessary overload variants.
- [ ] Keep each dialog's request type and domain-specific field setup separate.
- [ ] Keep each dialog's validation and submission logic separate where rules differ.
- [ ] Don't `NewSessionDialog`'s custom focus traversal policy as an explicit specialization.
- [ ] Replace direct generic button/footer construction in the five dialogs with the supertype lifecycle.
- [ ] Update dialog UI and validation tests for the new hierarchy and constructor signatures.

## Global Settings View

- [ ] Extract the repeated editor shell shared by filter, agent, and tool editors.
- [ ] Keep individual row builders separate where fields, tooltips, or model bookkeeping differ.
- [ ] Extract the repeated add-row plus `revalidate()`/`repaint()` action.
- [ ] Extract common configured-table header construction.
- [ ] Replace repeated editor and table-header `EmptyBorder` expressions with named local constants.
- [ ] Preserve row sizing, remove behavior, field-list synchronization, and test-visible component behavior.

## Async And Error Handling

- [ ] Audit every `exceptionally(...)` path in UI actions/components for the common background-work, EDT-update, error-display pattern.
- [ ] Route genuine error dialogs through `ErrorDialogs` so logging, headless behavior, and EDT dispatch are consistent.
- [ ] Leave confirmations, informational messages, and validation prompts as their appropriate dialog types.
- [ ] Remove redundant nested `SwingUtilities.invokeLater` calls where `BackgroundOperations.submit(...)` already completes on the EDT.
- [ ] Introduce a narrow shared async/error helper only where it removes real repetition without hiding threading semantics.
- [ ] Preserve ordering, cleanup, cancellation, and failure behavior in `ProgressOperation` and background jobs.

## Services And Persistence

- [ ] Consolidate `TerminalResources.residentMemory(...)` and `cpuTime(...)` around one shared `ps` execution/parsing pipeline.
- [ ] Preserve the semantic wrappers and their distinct value conversions.
- [ ] Add generic JSON read-or-default support to `PersistenceSupport`.
- [ ] Migrate `WindowStatePersistence` and `AppStatePersistence` to the shared persistence mechanics.
- [ ] Preserve class-specific logging and lifecycle behavior.
- [ ] Consolidate duplicated GitHub CLI execution between `CliTokenProvider` and `GitHubAuth`.
- [ ] Evaluate and, if appropriate, add a narrow synchronous process-result runner for Git, GitHub, and platform discovery.
- [ ] Keep `BackgroundOperations.runCommand(...)` as an async/streaming adapter rather than merging unrelated semantics into it.
- [ ] Preserve Git-specific, GitHub-specific, and platform-specific parsing/error behavior.
- [ ] Consolidate duplicated `AppStatePersistence` snapshot failure handling.

## Defaults And Presentation Text

- [ ] Establish one source of truth for the duplicated default worktree template.
- [ ] Review ownership of `DEFAULT_GROUP` without violating model/UI package boundaries.
- [ ] Consolidate only truly shared `Unavailable` presentation text; retain context-specific messages.

## Test Fixtures And Tests

- [ ] Replace exact empty `AppState` construction with `TestAppState.empty()` where the fixture is not documenting custom state.
- [ ] Add focused test-local factories for repeated `Project`/`Session` setup in `AgentContextTest`.
- [ ] Keep explicit state construction where it is part of the scenario being tested.
- [ ] Add tests for every extracted UI component/helper and preserve current accessibility/keyboard assertions.
- [ ] Run headless UI tests after each UI refactor batch.

## Verification Gate

- [ ] Run `gradle compileJava` after every production code change.
- [ ] Run `gradle spotlessCheck` after every production code change.
- [ ] Run focused tests for each changed area.
- [ ] Run `gradle check` before considering the cleanup complete.
- [ ] Fix PMD, SpotBugs, formatting, test, integration, and coverage failures rather than suppressing them.
- [ ] Confirm affected production additions remain covered by the project's coverage requirements.
- [ ] Review the final diff for accidental behavior changes and unrelated edits.
