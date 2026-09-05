# Branchloom Architecture

## Package Boundaries

- `src/main/java/com/jagent/desktop/models/` contains serializable domain data and small value objects. Models must not launch processes or depend on Swing.
- `src/main/java/com/jagent/desktop/services/` contains application services and all external I/O. `Store` owns persistence, `Git` owns Git CLI and worktree operations, and `GitHub` owns GitHub CLI/API operations.
- `src/main/java/com/jagent/desktop/ui/Branchloom.java` is the application bootstrap only. It configures the platform and launches `ui/views/AppView`.
- `src/main/java/com/jagent/desktop/ui/views/` contains application screens and view orchestration.
- `src/main/java/com/jagent/desktop/ui/components/` contains reusable Swing components and presentation helpers.
- `src/main/java/com/jagent/desktop/ui/dialogs/` contains focused modal dialogs and their input validation.

## Design Rules

- Keep classes small and focused on a single problem.
- Create classes with testability in mind: inject services and callbacks instead of reaching into global state or constructing process/persistence dependencies inside UI code.
- When widening method visibility for testability, prefer `protected` over package-private visibility.
- Keep process execution, filesystem access, Git operations, GitHub operations, and persistence out of views and dialogs.
- Prefer domain-level service methods over raw CLI command strings in UI classes.
- Keep UI callbacks narrow: views should report user intent, while the application coordinator decides what state changes and service operations follow.
- Do not add unrelated behavior into a class; keep each class focused on its responsibility.
- When extending an existing area, preserve these boundaries rather than placing the quickest implementation in a large coordinator class.

## Testability Practices

- Write production code so important behavior can be exercised without a display, live process, filesystem, network service, or wall-clock delay.
- Keep validation, parsing, formatting, branching, and command construction in pure methods or small domain services. Test these directly with representative and boundary-case inputs.
- Inject external collaborators such as services, process runners, clocks, executors, filesystem access, and callbacks instead of constructing them inside behavior that needs unit coverage.
- Keep Swing views and dialogs responsible for presentation and user input. Delegate business decisions, state transitions, and external operations to testable services or injected callbacks.
- Avoid starting asynchronous work, launching processes, reading global state, or modifying application state from constructors. Prefer explicit start methods or callbacks that tests can control.
- Separate asynchronous work from thread dispatch. Inject or isolate the executor and keep EDT dispatch in a thin adapter so success, failure, cancellation, and callback ordering can be tested deterministically.
- Avoid static mutable state and hidden global dependencies. When static access is unavoidable, isolate it behind a narrow boundary and keep the surrounding logic deterministic.
- Provide focused tests for success, invalid input, empty input, cancellation, external failures, interruption, duplicate data, and cleanup paths. Do not rely on broad startup tests to cover these branches.
- Use headless tests for component logic and validation. Reserve display-backed tests for integration tests that explicitly require a graphical environment.
- When widening visibility only to test deterministic behavior, prefer `protected` seams and document the testing purpose; do not expose unrelated implementation details publicly.

## Scope And Simplicity

- Make the smallest change that satisfies the request. Do not broaden a file-level request into an architectural refactor.
- Do not introduce wrapper records, registries, helper classes, lookup layers, compatibility APIs, or indirection unless the request explicitly requires them or the existing design already uses them for that purpose.
- Prefer direct concrete dependencies and direct method calls over string IDs, maps, factories, and intermediary objects when there are only a few known actions or collaborators.
- Keep simple transformations inline. Add a helper only when it is genuinely reused or makes a complex operation clearer.
- Do not move existing logic between classes merely to make an implementation feel cleaner. Preserve ownership unless the requested change specifically changes that ownership.
- Do not change constructors, public APIs, persistence formats, or unrelated models unless the request requires it.
- If a requested class is already in a partial migration, update it to the API that is explicitly present in the surrounding current code. Do not reconstruct missing architecture from stale call sites.
- If the repository contains contradictory APIs or cannot establish which version is authoritative, stop and ask one focused clarification question instead of guessing.
- Treat explicit user constraints such as “no helpers,” “no indirection,” “only this file,” or “no functional changes” as hard requirements.
- Always use braces for conditional, loop, and control-flow bodies, including single-line bodies.

## Verification

- Run `gradle compileJava` after every code change.
- Run `gradle spotlessCheck` after every code change. Existing unrelated formatting violations should not be rewritten without a specific reason.
- Run `gradle check` before considering work complete.
- All new production files and additions to existing production files must have at least 85% line test coverage. Add or update tests in the same change, and verify the affected classes in the JaCoCo report rather than relying only on the global threshold.
- Treat every PMD, SpotBugs, test, formatting, and coverage failure reported by `gradle check` as work to fix; do not suppress, exclude, disable, or lower a check merely to make the build pass.
- When `gradle check` fails, inspect the reported source and report, make the smallest real fix, and rerun `gradle check` until it passes.
- UI tests must run in the configured headless test environment unless the task explicitly requires a display-backed test.
