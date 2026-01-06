# Copilot Instructions for ktcheck

## Project Overview

ktcheck is a property-based testing library for Kotlin, inspired by QuickCheck/Hypothesis. It generates random test inputs and intelligently shrinks failing cases to minimal counterexamples.

### Architecture

**Core Components:**
- `Gen<T>`: Generator monad that produces values and shrink trees
- `ProducerTree`: Lazy binary tree structure driving value generation (left=data, right=continuation)
- `TestConfig`: Test execution configuration (iterations, seed, reporter)
- Generators in `gen/`: Int, Boolean, List, Set, OneOf, etc.

**Key Design Patterns:**
- **Left-Right Traversal**: Collections use left subtree for elements, right for continuation (see [ListGenerator.kt](src/main/kotlin/com/tamj0rd2/ktcheck/gen/ListGenerator.kt#L56-L79))
- **Shrinking via Tree Replacement**: Shrinks are ProducerTrees that replace subtrees, not direct values
- **Lazy Shrink Evaluation**: Shrinks are `Sequence<ProducerTree>` for memory efficiency
- **GenMode**: Distinguishes `Initial` generation from `Shrinking` (affects distinct collection handling)

### Generator Patterns

**Creating Generators:**
```kotlin
// Combine independent generators
val gen = Gen.int() + Gen.bool()  // Returns Gen<Tuple2<Int, Boolean>>

// Dependent generation (second depends on first)
val gen = Gen.int(1..10).flatMap { size -> Gen.int().list(size) }

// Map/transform
val gen = Gen.int().map { it * 2 }
```

**Shrinking Behavior:**
- Ints shrink toward 0 (or nearest range bound) by halving distance
- Booleans shrink true→false
- Lists shrink by size reduction (tail/head removal) then element-wise
- Sets handle duplicates during shrinking by accepting smaller sizes when in range

## Running Tests

### Command Pattern
```bash
./gradlew :test --tests "<fully.qualified.ClassName.testMethodName>"
```

### Examples
```bash
# Run all tests
./gradlew test

# Specific test class
./gradlew :test --tests "com.tamj0rd2.ktcheck.gen.SetGeneratorTest"

# Specific test method (quote if spaces)
./gradlew :test --tests "com.tamj0rd2.ktcheck.gen.SetGeneratorTest.shrinks a set of 3 elements"

# Pattern matching
./gradlew :test --tests "*SetGenerator*"
```

### Critical Testing Notes
- Test names must be fully qualified: `package.ClassName.methodName`
- Nested test classes use `$`: `ClassName$NestedClass`
- **Always read terminal output** - HTML reports are not accessible to agents
- Full exception format is enabled via `TestExceptionFormat.FULL`
- Test failures show: test name, assertion error, expected vs actual, stack trace

### Common Test Locations
- Generator tests: `com.tamj0rd2.ktcheck.gen.*GeneratorTest`
- Test framework: `com.tamj0rd2.ktcheck.testing.*Test`
- Shrinking challenges: `com.tamj0rd2.ktcheck.gen.ShrinkingChallenges`

## Testing Conventions

**Test Utilities:**
- `Gen.samples()`: Infinite sequence of generated values for distribution testing
- `Counter.withCounter {}`: Collect and verify percentage distributions
- `Gen.generateWithShrunkValues()`: Test helper exposing shrink tree (see [GenTests.kt](src/test/kotlin/com/tamj0rd2/ktcheck/gen/GenTests.kt#L102))
- `producerTree {}`: DSL for constructing predetermined test trees

**Property Test Patterns:**
```kotlin
// Boolean property
forAll(Gen.int()) { value -> value + 1 > value }

// Exception-based property  
checkAll(Gen.int()) { value -> expectThat(value).isGreaterThan(-1) }

// With configuration
forAll(TestConfig().withIterations(1000), Gen.int()) { /* test */ }
```

## Project Structure

```
src/main/kotlin/com/tamj0rd2/ktcheck/
├── gen/              # Generators (Int, List, Set, Bool, OneOf, Filter)
├── producer/         # ProducerTree, Seed, ValueProducer
├── testing/          # Test framework (forAll, checkAll, TestConfig)
├── stats/            # Counter for distribution verification
└── util/             # Tuple types for multi-arg properties
```

## Development Workflow

**Building:** `./gradlew build`
**Testing:** `./gradlew test` (JUnit 5, Strikt assertions, Java 21 toolchain)
**Publishing:** Jitpack (version 0.0.2)

**Environment:**
- `ktcheck.test.iterations` system property controls default iteration count (default: 1000)
- Seed can be hardcoded for reproducible debugging via `TestConfig.replay(seed, iteration)`

### TDD Approach

**Critical: When implementing new features or fixing bugs involving tests:**

1. **Verify test setup first** - Run the test to ensure it fails at the expected assertion, not due to setup issues
2. **Work line-by-line** - Validate each assertion fails/passes as expected before proceeding
3. **Fix test setup before production code** - If tests fail on unexpected lines, fix the test setup (e.g., tree
   construction with `producerTree {}`)
4. **Get feedback before changing production code** - Propose implementation approach and wait for confirmation
5. **Never assume test setup is correct** - Question whether the test is exercising the code path you think it is

**Example workflow:**

- Run test → fails at line 10 (expected value)
- Fix: adjust tree setup with `producerTree { left(expectedValue) }`
- Re-run → fails at line 15 (expected shrinks)
- Now ready to implement production code changes

