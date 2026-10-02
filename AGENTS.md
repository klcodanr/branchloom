# Branchloom Architecture

## Package Boundaries

- `src/main/java/com/jagent/desktop/async` contains async handing code. All asynchronous operations should use these classes to ensure consistency.
- `src/main/java/com/jagent/desktop/models/` contains serializable domain data and small value objects. Models must not launch processes or depend on Swing.
- `src/main/java/com/jagent/desktop/services/` contains application services and all external I/O. `Store` owns persistence, `GitRepository`/`GitNative` own Git repository and native Git operations, and `GitHub` owns GitHub CLI/API operations.
- `src/main/java/com/jagent/desktop/ui/Branchloom.java` is the application bootstrap only. It configures the platform and launches `ui/views/AppView`.
- `src/main/java/com/jagent/desktop/ui/views/` contains application screens and view orchestration.
- `src/main/java/com/jagent/desktop/ui/components/` contains reusable Swing components and presentation helpers.
- `src/main/java/com/jagent/desktop/ui/dialogs/` contains focused modal dialogs and their input validation.

## Design Rules

- Keep classes small and focused on a single problem.
- Create classes with testability in mind: inject services and callbacks instead of reaching into global state or constructing process/persistence dependencies inside UI code.
- When widening method visibility for testability, prefer `public` or `protected` over package-private visibility.
- Keep process execution, filesystem access, Git operations, GitHub operations, and persistence out of views and dialogs.
- Prefer domain-level service methods over raw CLI command strings.
- Keep UI callbacks narrow: views should report user intent, while the application coordinator decides what state changes and service operations follow.
- Do not add unrelated behavior into a class; keep each class focused on its responsibility.
- When extending an existing area, preserve these boundaries rather than placing the quickest implementation in a large coordinator class.

## Testability Practices

- Write production code so important behavior can be exercised without a display, live process, filesystem, network service, or wall-clock delay.
- Keep validation, parsing, formatting, branching, and command construction in pure methods or small domain services. Test these directly with representative and boundary-case inputs.
- Inject external collaborators such as services, process runners, clocks, executors, filesystem access, and callbacks instead of constructing them inside behavior that needs unit coverage.
- Keep Swing views and dialogs responsible for presentation and user input. Delegate business decisions, state transitions, and external operations to testable services or injected callbacks.
- Avoid starting asynchronous work, launching processes, reading global state, or modifying application state from constructors. Prefer explicit start methods or callbacks that tests can control.
- Avoid static mutable state and hidden global dependencies. When static access is unavoidable, isolate it behind a narrow boundary and keep the surrounding logic deterministic.
- Provide focused tests for success, invalid input, empty input, cancellation, external failures, interruption, duplicate data, and cleanup paths. Do not rely on broad startup tests to cover these branches.
- Use headless tests for component logic and validation. Reserve display-backed tests for integration tests that explicitly require a graphical environment.
- UI tests use the autodetected `com.jagent.desktop.test.SwingThemeExtension` to apply the deterministic Light FlatLaf theme on the EDT; do not add per-test theme setup unless a test specifically exercises theme switching.

## Scope And Simplicity

- Make the smallest change that satisfies the request. Do not broaden a file-level request into an architectural refactor.
- Do not introduce indirection unless the request explicitly calls for it.
- Prefer direct concrete dependencies and direct method calls over string IDs, maps, factories, and intermediary objects.
- Keep simple transformations inline. Add a helper only when it is genuinely reused or makes a complex operation clearer.
- Do not add pass-through helpers with a single call site unless they remove meaningful complexity or are expected to be reused immediately.
- Prefer existing extension points and APIs before introducing new overloads or methods. Prefer compsing methods to adding new single-use methods. Only add new APIs when existing ones cannot satisfy the requirement cleanly.
- Avoid nested private classes. Prefer top-level classes unless a nested type is truly tiny, tightly scoped, and cannot be reused elsewhere.
- Do not reconstruct missing architecture from stale call sites.
- If the repository contains contradictory APIs or cannot establish which version is authoritative, stop and ask one focused clarification question instead of guessing.
- Do not introduce compaibility wrappers, shims or helpers when refactoring. Refactor the code cleanly.
- Treat explicit user constraints such as “no helpers,” “no indirection,” “only this file,” or “no functional changes” as hard requirements.
- Always use braces for conditional, loop, and control-flow bodies, including single-line bodies.

## Verification

- Run `gradle compileJava` after every code change.
- Run `gradle spotlessCheck` after every code change. Existing unrelated formatting violations should not be rewritten without a specific reason.
- Run `gradle check` before considering work complete.
- Do not change production behavior solely to satisfy tests. If tests fail because assumptions changed, prefer updating tests unless product behavior is actually incorrect.
- All new production files and additions to existing production files must have at least 85% line test coverage. Add or update tests in the same change, and verify the affected classes in the JaCoCo report rather than relying only on the global threshold.
- Treat every PMD, SpotBugs, test, formatting, and coverage failure reported by `gradle check` as work to fix; do not suppress, exclude, disable, or lower a check merely to make the build pass unless explictly requested.
- When `gradle check` fails, inspect the reported source and report, make the smallest real fix, and rerun `gradle check` until it passes.
- UI tests must run in the configured headless test environment unless the task explicitly requires a display-backed test.
