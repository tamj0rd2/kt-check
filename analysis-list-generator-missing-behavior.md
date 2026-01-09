# Analysis: Missing List Generator Behavior

## Current Implementation Status

### ✅ What We Have Implemented

#### v1 (ProducerTree-based) ListGenerator

- ✅ Basic list generation with size range
- ✅ Fixed-size list generation
- ✅ **Recursive shrinking** - lists shrink to smaller lists that can be shrunk further
- ✅ Size shrinking (tail removal and head removal)
- ✅ Element-wise shrinking
- ✅ **Distinct elements (set generation)** - via `set()` method
- ✅ Duplicate handling during shrinking for distinct collections
- ✅ Stack overflow protection for large lists
- ✅ Error handling for impossible distinct collection sizes

#### v2 (GenResult-based) ListGenerator

- ✅ Basic list generation with size range
- ✅ Fixed-size list generation
- ✅ **Recursive shrinking** - lists shrink to smaller lists that can be shrunk further
- ✅ Size shrinking (tail removal and head removal)
- ✅ Element-wise shrinking
- ✅ **Distinct elements (set generation)** - via `set()` method and `list(distinct=true)`
- ✅ Duplicate handling during shrinking for distinct collections
- ✅ Stack overflow protection for large lists
- ✅ Error handling for impossible distinct collection sizes

### ✅ Feature Parity Achieved!

**v1 and v2 now have the same core features!** Both implementations support:

- Basic and fixed-size list generation
- Recursive shrinking
- Distinct element generation via `list(distinct=true)`
- Set generation via `set()` extension functions
- Proper duplicate handling during generation and shrinking
- Shared exception handling (`DistinctCollectionSizeImpossible`)

The only remaining differences are architectural (ProducerTree vs GenResult), not functional.

### 🤔 Potential Advanced Features (Not Currently Needed)

Looking at what other property-based testing libraries typically support:

**Note**: Many common patterns can already be achieved with `map`:

- **Sorted lists**: `Gen.int().list().map { it.sorted() }`
- **Non-empty lists**: `Gen.int().list(1..100)` or `Gen.int().list().map { if (it.isEmpty()) listOf(0) else it }`
- **Value constraints**: `Gen.int(1..100).list()` (constrain the element generator)

**Advanced features that could be added if requested**:

1. **Unique by property** ❌
    - Generate lists with elements that are distinct by some property
    - Example: `Gen.person().list().uniqueBy { it.id }`
    - More flexible than just `distinct`
   - **Status**: Defer until requested by users

2. **Shuffle/Permutations** ❌
    - Generate permutations of a given list
    - Example: `Gen.permutation(listOf(1, 2, 3, 4, 5))`
    - Useful for testing order-dependent code
   - **Status**: Niche use case, can wait

3. **Sub-lists** ❌
    - Generate sub-lists of a given list
    - Example: `Gen.sublist(listOf(1, 2, 3, 4, 5))`
    - Shrinks toward smaller sub-lists
   - **Status**: Can be achieved with other generators, low priority

## Priority Assessment

### ✅ Completed (Was High Priority)

~~**1. Set Generation / Distinct Elements**~~ **DONE!**

- ✅ v2 now has `distinct` parameter on `list()` methods
- ✅ v2 now has `set()` extension functions
- ✅ Duplicate handling during generation implemented
- ✅ Duplicate handling during shrinking implemented
- ✅ Exception handling for impossible distinct sizes implemented
- ✅ Shared `DistinctCollectionSizeImpossible` exception between v1 and v2
- ✅ Tests added to `ListGeneratorTestContract`:
   - `generates lists with distinct elements when distinct=true`
   - `distinct list shrinks maintain distinctness`
   - `throws when unable to generate enough distinct elements`

### 🟢 Optional - Advanced Features (Defer Until Requested)

**Unique By Property**
- **Why**: More flexible than plain `distinct`
- **Impact**: Niche use case
- **Effort**: High - complex shrinking behavior
- **Status**: Wait for user request

**Permutations/Shuffles**
- **Why**: Specific use case for testing order-dependent code
- **Impact**: Can be worked around
- **Effort**: Medium
- **Status**: Wait for user request

**Sub-lists**
- **Why**: Niche use case
- **Impact**: Can be implemented with other generators
- **Effort**: Medium
- **Status**: Wait for user request

**Note**: Common patterns like sorted lists and non-empty lists can already be achieved using `map`:

```kotlin
Gen.int().list().map { it.sorted() }        // Sorted lists
Gen.int().list(1..100)                       // Non-empty lists (size constraint)
```

## Recommendations

### ✅ Completed

1. ~~**Implement Set Generation in v2**~~ **DONE!**
   - ✅ Ported the `distinct` parameter and logic from v1
   - ✅ Added `set()` extension functions to v2
   - ✅ Handle duplicates during shrinking
   - ✅ Added error for impossible distinct sizes
   - ✅ Tests added to `ListGeneratorTestContract`

2. ~~**Update RecursiveListShrinkingContract Tests**~~ **DONE!**
   - ✅ Added `RecursiveShrinkNavigator` abstraction
   - ✅ Both v1 and v2 implement the abstraction
   - ✅ 6 comprehensive recursive shrinking tests in contract
   - ✅ All tests pass for both implementations

### Future Enhancements (Optional - Only if Requested)

Advanced features that could be added based on user demand:

- `uniqueBy` (distinct by property)
- Permutations/shuffles
- Sub-lists

**Note**: Not implementing now because:

- These are niche use cases
- No current need identified
- Can be added later without breaking changes

## Key Insight

**✅ Feature parity achieved!** Both v1 and v2 now have:

- Complete list generation with size ranges
- Recursive shrinking (depth-first traversal)
- Distinct element support via `list(distinct=true)`
- Set generation via `set()` extension functions
- Proper duplicate handling during generation and shrinking
- Shared exception handling

The implementations are now functionally equivalent. The only differences are:

- **v1**: Uses `ProducerTree` architecture
- **v2**: Uses `GenResult` architecture

Both approaches work correctly and pass the same comprehensive test suite via `ListGeneratorTestContract`.

## Open Questions

1. **Should we create a `SetGeneratorContract`?**
    - Would let us test set behavior consistently across v1/v2
    - Similar to ListGeneratorTestContract
   - **Decision**: Could be useful but not urgent since set tests are already in ListGeneratorTestContract

2. **How should `uniqueBy` shrinking work?**
    - If we shrink an element, might create duplicate by property
    - Need same retry logic as distinct?
    - Complex design decision
   - **Decision**: Defer until requested by users

## Test Coverage Analysis

### What's Tested

- ✅ Basic list generation
- ✅ Fixed-size lists
- ✅ Recursive shrinking (6 comprehensive tests via `RecursiveListShrinkingContract`)
- ✅ Stack overflow protection
- ✅ Distinct element generation (both v1 and v2)
- ✅ Distinct list shrinking behavior (maintains distinctness)
- ✅ Impossible distinct size error handling
- ✅ Set generation (both v1 and v2)

### What's Not Tested

- ❌ Recursive shrinking specifically for distinct lists (though it inherits from list implementation)
- ❌ Set-specific recursive shrinking tests
- ❌ Any advanced features (sorted, non-empty, etc.)

### Test Infrastructure

Both v1 and v2 share the same test suite via:

- **`ListGeneratorTestContract`** - 10 tests covering basic behavior, recursive shrinking, and distinct lists
- **`RecursiveShrinkNavigator`** abstraction - Allows testing recursive shrinking across different architectures
- **Shared exception** - `DistinctCollectionSizeImpossible` used by both implementations

Test coverage is comprehensive for core features. Advanced features (if added) would need their own tests.

## Conclusion

**✅ Feature Parity Achieved!**

v1 and v2 now have identical core functionality:

- ✅ List generation (basic, fixed-size, with ranges)
- ✅ Recursive shrinking
- ✅ Distinct element support
- ✅ Set generation
- ✅ Comprehensive test coverage via shared contract

The implementations differ only in their internal architecture (ProducerTree vs GenResult), not in their capabilities.

**Recommended Next Steps:**

1. **Documentation**:
   - Update README with examples of `list(distinct=true)` and `set()`
   - Document the distinction between v1 and v2 architectures
   - Add migration guide if v2 becomes the default
   - Show how to achieve common patterns with `map`:
      - Sorted lists: `Gen.int().list().map { it.sorted() }`
      - Non-empty lists: `Gen.int().list(1..100)`

2. **Advanced features** (only if requested):
   - `uniqueBy` (distinct by property)
   - Permutations/shuffles
   - Sub-lists
   - These can wait until users request them

**Bottom Line**: The core work is complete! Both implementations are now feature-complete for property-based testing
needs. Common patterns can be achieved with `map`, and advanced features can wait for user demand.

