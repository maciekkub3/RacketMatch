---
name: flow-unit-tests
description: unit-tests writing guide for llm
license: MIT
---

# RacketMatch - Unit Testing Guidelines

**Skill for writing high-quality unit tests following project conventions**

---

## Overview

This skill provides comprehensive guidelines for writing unit tests in the RacketMatch Kotlin Multiplatform project. The project uses JUnit 5, MockK, Kotest assertions, and Turbine for Flow testing.

---

## Running Tests

### Running Tests for the KMP Project

Use Gradle to run tests:

```bash
# Run all unit tests
./gradlew :shared:testDebugUnitTest

# Run with stacktrace for debugging
./gradlew :shared:testDebugUnitTest --stacktrace

# Run a specific test class
./gradlew :shared:testDebugUnitTest --tests "*.AuthViewModelTest"

# Run a specific test method
./gradlew :shared:testDebugUnitTest --tests "*.AuthViewModelTest.initial state is correct"
```

### IDE Integration

Tests can also be run directly from Android Studio / IntelliJ IDEA:
- Right-click on test class → "Run 'ClassNameTest'"
- Right-click on test method → "Run 'test method name'"
- Right-click on test package → "Run Tests in 'package.name'"

---

## Testing Framework Stack

### Core Libraries
- **JUnit 5** (`org.junit.jupiter.api`) - Test framework
- **MockK** (`io.mockk`) - Mocking framework for Kotlin
- **Kotest** (`io.kotest`) - Assertion library
- **Kotlin Coroutines Test** - Testing coroutines and flows
- **Turbine** (`app.cash.turbine`) - Flow testing library

---

## Test Class Structure

### 1. ViewModel Tests

**Template:**

```kotlin
@ExtendWith(MockKExtension::class)
internal class AuthViewModelTest {

    // Mocks - use appropriate annotation
    @MockK  // Strict mock - requires explicit stubbing
    private lateinit var login: LoginUseCase

    @RelaxedMockK  // Relaxed mock - returns default values
    private lateinit var logger: Logger

    // Test fixtures
    private val testDispatcher = StandardTestDispatcher()

    // System under test
    private lateinit var viewModel: AuthViewModel

    // Test data
    private val testPlayer = Player(id = "1", name = "Jan Kowalski", elo = 1200)

    @BeforeEach
    fun setUp() {
        viewModel = AuthViewModel(
            login = login,
            logger = logger,
            dispatcher = testDispatcher,
        )
    }

    @Test
    fun `initial state is correct`() = runTest {
        viewModel.state.value shouldBe AuthState.Idle
    }

    // ... more tests
}
```

### 2. Repository Tests

**Template:**

```kotlin
@ExtendWith(MockKExtension::class)
internal class PlayerRepositoryTest {

    @MockK
    private lateinit var dataSource: PlayerDataSource

    @MockK
    private lateinit var mapper: PlayerMapper

    private lateinit var repository: PlayerRepository

    @BeforeEach
    fun setup() {
        repository = PlayerRepositoryImpl(
            dataSource = dataSource,
            mapper = mapper,
        )
    }

    @Test
    fun `returns mapped data on success`() = runTest {
        val remoteData = PlayerDto(id = "1", name = "Jan Kowalski")
        val expected = Player(id = "1", name = "Jan Kowalski", elo = 1200)

        coEvery { dataSource.getPlayer(any()) } returns remoteData
        every { mapper.map(remoteData) } returns expected

        val result = repository.getPlayer("1")

        result shouldBe expected
        verify { mapper.map(remoteData) }
    }

    // ... more tests
}
```

### 3. Mapper Tests (Using @TestFactory)

**Template:**

```kotlin
internal class SportMapperTest {

    @TestFactory
    fun `toDomain maps correctly`() = listOf(
        "TENNIS" to Sport.TENNIS,
        "PADEL" to Sport.PADEL,
    ).map { (dto, domain) ->
        dynamicTest("maps $dto to $domain correctly") {
            val result = dto.toDomain()
            result shouldBe domain
        }
    }
}
```

---

## Flow Testing Patterns

### Testing Flow Emissions with Turbine

```kotlin
@Test
fun `effect emits NavigateBack`() = runTest {
    viewModel.effect.test {
        viewModel.onIntent(AuthIntent.Logout)

        testDispatcher.scheduler.advanceUntilIdle()

        awaitItem() shouldBe AuthEffect.NavigateBack
    }
}
```

### Testing State Changes

```kotlin
@Test
fun `state updates when player is loaded`() = runTest {
    coEvery { getPlayer(any()) } returns testPlayer

    viewModel.state.test {
        skipItems(1) // Skip initial state
        viewModel.onIntent(PlayerIntent.Load("1"))
        awaitItem() shouldBe PlayerState.Content(player = testPlayer)
    }
}
```

### Testing Error States

```kotlin
@Test
fun `shows error on failure`() = runTest {
    val exception = RuntimeException("Network error")
    coEvery { getPlayer(any()) } throws exception

    viewModel.state.test {
        skipItems(1) // Skip initial state
        viewModel.onIntent(PlayerIntent.Load("1"))
        awaitItem() shouldBe PlayerState.Error(exception.message ?: "")
    }
}
```

---

## MockK Patterns

### Stubbing

```kotlin
// Suspend function
coEvery { repository.getPlayer(any()) } returns testPlayer
coEvery { repository.getPlayer(any()) } throws Exception("error")

// Regular function
every { mapper.map(any()) } returns testData

// Multiple return values
coEvery { repository.getPlayer(any()) } returnsMany listOf(player1, player2)

// Answer with lambda
coEvery { repository.saveMatch(any()) } answers {
    val param = firstArg<Match>()
    param.copy(id = "generated-id")
}

// No-op completion
justRun { analytics.trackEvent(any()) }
coJustRun { repository.deleteMatch(any()) }
```

### Verification

```kotlin
// Verify exact number of calls
verify(exactly = 1) { mapper.map(testData) }
verify(exactly = 0) { errorHandler.handle(any()) }

// Coroutine verification
coVerify(exactly = 1) { repository.getPlayer(any()) }

// Verify with argument matching
coVerify {
    repository.save(
        match { it.id == "123" },
        match { it.sport == Sport.PADEL },
    )
}
```

### Mock Annotations

```kotlin
@MockK  // Strict mock - must stub all calls
private lateinit var repository: PlayerRepository

@RelaxedMockK  // Auto-returns defaults (Unit, 0, "", null, emptyList, etc.)
private lateinit var logger: Logger

@InjectMockKs  // Auto-inject all @MockK fields as constructor params
private lateinit var useCase: GetPlayerUseCase
```

---

## Test Naming Conventions

### ViewModel Tests

**Pattern:** `` `descriptive sentence about behavior` ``

```kotlin
@Test
fun `initial state is Idle`() = runTest { }

@Test
fun `onIntent navigates back when OnCloseClick`() = runTest { }

@Test
fun `onIntent fetches player successfully on Load`() = runTest { }

@Test
fun `onIntent updates to Error on fetch failure`() = runTest { }
```

### Mapper Tests

**Pattern:** `` `function name maps correctly` `` or `` `function name description` ``

```kotlin
@TestFactory
fun `toDomain maps Sport correctly`() = ...

@TestFactory
fun `toUi should map domain to UI model`() = ...
```

### Repository Tests

**Pattern:** `` `returns/throws description on condition` ``

```kotlin
@Test
fun `returns mapped data on success`() = runTest { }

@Test
fun `throws exception on network error`() = runTest { }
```

---

## Assertion Patterns (Kotest)

### Basic Assertions

```kotlin
result shouldBe expected
result shouldBe null
list.shouldBeEmpty()
state.isLoading shouldBe true
```

### Exception Assertions

```kotlin
val thrown = shouldThrow<NotFoundException> {
    repository.getPlayer(invalidId)
}
thrown shouldBe expectedException
thrown.message shouldBe "expected message"
```

---

## Common Test Scenarios

### 1. Testing Loading → Content Flow

```kotlin
@Test
fun `fetches data successfully`() = runTest {
    coEvery { repository.getPlayer(any()) } returns testPlayer

    viewModel.state.test {
        skipItems(1)  // Skip initial state
        viewModel.onIntent(PlayerIntent.Load("1"))
        awaitItem() shouldBe PlayerState.Content(player = testPlayer)
    }

    coVerify(exactly = 1) { repository.getPlayer(any()) }
}
```

### 2. Testing Error Handling

```kotlin
@Test
fun `shows error on failure`() = runTest {
    val exception = RuntimeException("Network error")
    coEvery { repository.getPlayer(any()) } throws exception

    viewModel.state.test {
        skipItems(1)  // Skip initial state
        viewModel.onIntent(PlayerIntent.Load("1"))
        awaitItem() shouldBe PlayerState.Error(exception.message ?: "")
    }
}
```

### 3. Testing Navigation Effects

```kotlin
@Test
fun `navigates to match detail on item click`() = runTest {
    viewModel.effect.test {
        viewModel.onIntent(PlayerIntent.MatchClicked(matchId = "m1"))
        awaitItem() shouldBe PlayerEffect.NavigateToMatch("m1")
    }
}
```

---

## Best Practices

### DO
1. **Use descriptive test names** with backticks for readability
2. **Use @RelaxedMockK for logger/analytics** - they don't affect behavior
3. **Use @MockK for critical dependencies** - catch missing stubs early
4. **Verify interactions** with `verify(exactly = N)` or `coVerify(exactly = N)`
5. **Use Kotest assertions** (`shouldBe`, `shouldBeEmpty()`, etc.)
6. **Test error scenarios** alongside happy paths
7. **Use skipItems()** to skip initial state in Flow tests
8. **Test initial state** separately when needed

### DON'T
1. **Don't use println** for debugging - use logger or assertions
2. **Don't test implementation details** - test behavior
3. **Don't use Thread.sleep** - use `testDispatcher.scheduler.advanceUntilIdle()`
4. **Don't stub relaxed mocks** - defeats the purpose
5. **Don't test private methods** - test through public API
6. **Don't mock data classes** - use real instances
7. **Don't use production dispatchers** - use StandardTestDispatcher

---

## Quick Reference

### Essential Imports

```kotlin
// JUnit 5
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest

// MockK
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension

// Kotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.assertions.throwables.shouldThrow

// Coroutines Test
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher

// Turbine (Flow testing)
import app.cash.turbine.test
```

---

## File Locations (KMP Structure)

**Test files location pattern:**
- Common tests: `shared/src/commonTest/kotlin/`
- Android-specific tests: `shared/src/androidUnitTest/kotlin/`
- Mirror the main source package structure

**Example:**
- Main: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/AuthViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/AuthViewModelTest.kt`

---

## Summary

When writing tests in RacketMatch:
1. **Use MockK** for mocking with `@MockK`/`@RelaxedMockK`
2. **Follow naming conventions** with backticks for readability
3. **Stub with MockK** using `coEvery`/`every`
4. **Assert with Kotest** using `shouldBe`
5. **Verify interactions** with `verify(exactly = N)`
6. **Test both happy path and errors**
7. **Use StandardTestDispatcher** for all ViewModels
8. **Use @TestFactory** for parameterized tests
9. **Write descriptive test names** that explain behavior
10. **Run tests** using `./gradlew :shared:testDebugUnitTest`

This ensures tests are consistent, maintainable, and follow the project's established patterns.
