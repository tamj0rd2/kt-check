# ktcheck - Property-Based Testing Library for Kotlin

A simple property-based testing library for Kotlin inspired by QuickCheck and similar tools.

## Development Workflow

### Test-Driven Development (TDD)

**This project follows strict TDD practices. All changes MUST follow the Red-Green-Refactor cycle.**

#### Process

1. **Red - Write a failing test**
    - Start with a test that specifies the desired behavior
   - **STOP and get feedback** - After writing a test, ALWAYS pause and wait for user feedback
       - Do NOT run the test yet
       - Do NOT implement the solution yet
       - Present the test for review
       - This ensures we agree on what the test does
       - Provides opportunity to review requirements or design
       - Allows iteration on the test itself if needed
       - Only proceed after receiving explicit approval
   - After approval, run the test to verify it fails
    - **Important**: Ensure the test fails for the *correct reason* (e.g., missing method, wrong behavior, not
      compilation error)
    - If the test fails for the wrong reason, fix the test until it fails correctly

2. **Green - Make it pass with the smallest step**
    - Write the *minimum* code needed to make the test pass
    - Run the test again to verify it passes
    - If it doesn't pass, take another small step
    - **Loop** this step until the test passes

3. **Refactor - Improve the code**
    - Clean up duplication, improve names, extract methods
    - Run tests after each refactor to ensure nothing broke

#### Rules

- **Never write a full implementation upfront** - take small, incremental steps
- Always run tests between each step
- Each commit should represent a complete Red-Green-Refactor cycle
- If multiple test cases are needed, add them one at a time

#### Testing for Flakiness

When a test appears flaky or you need to verify it passes consistently:

- **Don't run the gradle command multiple times** - this is slow and unreliable
- **Use `@RepeatedTest(N)` annotation** on the test method (e.g., `@RepeatedTest(100)`)
- This runs the test N times in a single test execution
- More efficient and shows clear success/failure counts
- Example: If testing for race conditions or random generation edge cases

## Build, Test, and Lint

### Build

```bash
./gradlew build
```

### Run Tests

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.tamj0rd2.ktcheck.current.IntGeneratorTest"

# Run tests in a specific package
./gradlew test --tests "com.tamj0rd2.ktcheck.current.*"
```

### Clean Build

```bash
./gradlew clean build
```

## Architecture

### Core Concepts

**Generator System (`Gen<T>`)**

- `Gen<T>` is the main interface for creating generators that produce random test values
- `GenImpl<T>` is the internal implementation that handles generation and shrinking
- Generators support:
    - `sample(seed)`: Generate a single value
    - `samples(seed)`: Generate an infinite sequence of values
    - Combinators: `map`, `flatMap`, `combineWith`, `filter`
    - Collection builders: `list()`, `set()`

**Shrinking**

- `GenResultV2<T>` represents a generated value plus its shrink tree
- When a test fails, the library automatically finds simpler counterexamples
- Shrinkers live in `core.shrinkers` (e.g., `IntShrinker`)

**Property Testing**

- `forAll(gen, property)`: Test that a property holds for all generated values (returns boolean)
- `checkAll(gen, property)`: Test that a property holds (uses assertions)
- `PropertyFalsifiedException`: Thrown when a property fails, includes both original and shrunk counterexamples

**Random Tree**

- `RandomTree` (`current/RandomTree.kt`) is the internal structure for deterministic random generation
- Allows reproducible test failures via `Seed`
- `Seed.sequence(seed)` creates an infinite sequence of deterministic seeds

### Package Structure

- `com.tamj0rd2.ktcheck` - Public API (`Gen`, `Gens`, `forAll`, `checkAll`)
- `com.tamj0rd2.ktcheck.current` - Current stable implementation (GenImpl, specific generators)
- `com.tamj0rd2.ktcheck.core` - Core data structures (`Seed`, `Tree`, `Tuple`, shrinkers)
- `com.tamj0rd2.ktcheck.incubating` - Experimental features (if present)

### Test Structure

**Contract-Based Testing**

- Tests are organized using contracts (`contracts/` package) and implementations (`current/` package)
- Contracts define the specification (e.g., `IntGeneratorContract`)
- Implementations test specific generator implementations (e.g., `IntGeneratorTest` extends `BaseContractImpl` and
  implements `IntGeneratorContract`)
- `BaseContract` provides test utilities: `tree()`, `Gen<T>.generate()`, `Gen<T>.generating(value)`

## Key Conventions

### Generator Builders

- Use `Gens` object for creating generators (e.g., `Gens.int()`, `Gens.bool()`, `Gens.oneOf()`)
- `GenV2Builders` is the internal implementation that `Gens` delegates to

### Combining Generators

- Use `combineWith` to create dependent generators
- Use `zip` (via `Gens.zip()`) to combine independent generators into tuples
- Use `flatMap` when the next generator depends on a previous value

### Filtering and Exception Handling

- `filter(threshold, predicate)`: Filters generated values (default threshold: 100 attempts)
- `ignoreExceptions(klass, threshold)`: Retries generation when specific exceptions occur
- Both have performance implications; prefer specialized generators when possible

### Test Configuration

- `TestConfig` controls test behavior (iterations, reporters, etc.)
- `NoOpTestReporter` can be used in tests to suppress output
- Custom reporters can be created by implementing `TestReporter`

### Experimental Features

- Features marked with `@Experimental` annotation are subject to change
- Check `Experimental.kt` for current experimental APIs

## Implementation Notes

### When adding new generators:

1. Create the generator class in `current/` package extending `GenImpl<T>`
2. Implement `generate(tree: RandomTree): GenResultV2<T>`
3. Optionally override `edgeCases()` for important boundary values
4. Create a contract in `contracts/` defining the generator's specification
5. Create a test in `current/` that implements the contract

### When adding new shrinking strategies:

1. Create a shrinker in `core/shrinkers/` (see `IntShrinker` as example)
2. Generate shrinks as a `Sequence<GenResultV2<T>>` for lazy evaluation
3. Order shrinks from simplest to most complex

### Determinism

- All generation is deterministic given a seed
- Use `Seed.random()` for non-deterministic seeds in tests
- Property test failures include the seed for reproduction

## Common Pitfalls

### Missing Imports

- If you get compilation errors about unresolved references or methods not being applicable, check if you need to add
  imports
- Common Kotlin/Strikt imports that are easy to miss:
    - `strikt.assertions.contains` - for collection contains assertions
    - `strikt.assertions.containsAll` - for checking multiple elements
    - `strikt.assertions.containsExactlyInAnyOrder` - for exact collection matching
- Always check similar test files to see what imports they use
- Don't assume an error is a wrong API usage - check imports first

### Using Strikt for Collection Assertions

- **Don't use forEach with expectThat inside** - use Strikt's `.all { }` instead
- Bad: `result.forEach { expectThat(it).isPositive() }`
- Good: `expectThat(result).all { isPositive() }`
- For nested collections: `expectThat(result).all { all { isPositive() } }`
- This provides better error messages and follows Strikt's fluent API pattern

## Code Style

### Comments

- **Only comment code that needs clarification** - do not comment code that is self-explanatory
- Prefer writing clear, self-documenting code through:
    - Well-named variables and functions
    - Simple, focused methods
    - Extracting complex logic into named functions
- Comments should explain *why*, not *what* the code does
- If you find yourself writing a comment to explain what code does, refactor the code to be clearer instead
