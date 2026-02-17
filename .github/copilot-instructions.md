# ktcheck - Property-Based Testing Library for Kotlin

A simple property-based testing library for Kotlin inspired by QuickCheck and similar tools.

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

## Code style

- We use strikt for assertions. Check similar tests to see imports and usage examples
- We write code with well named variables and functions. We don't use comments to explain what the code does.
- If you would ever write files to `/tmp/`, use the project's `./tmp/` folder instead
- Use available LSPs to find usages/definitions whenever appropriate
