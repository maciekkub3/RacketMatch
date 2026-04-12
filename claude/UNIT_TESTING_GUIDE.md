# RacketMatch - Unit Testing Guide

> **Purpose**: This guide documents the comprehensive unit testing conventions used in the RacketMatch Kotlin Multiplatform codebase. Follow these patterns to ensure consistency across all test files.

---

## Quick Reference Checklist

When writing a unit test, ensure:

- [ ] Test file named `{ClassName}Test.kt`
- [ ] Class is `internal`
- [ ] Use `@ExtendWith(MockKExtension::class)`
- [ ] **Mock names are simple camelCase (NO "mock" prefix/suffix)**
- [ ] Use `@RelaxedMockK` for loggers, analytics, formatters
- [ ] Use `@MockK` for use cases, repositories, data providers
- [ ] Test names use backticks with descriptive sentences
- [ ] Proper spacing: blank lines between setup/action/assert
- [ ] Use `runTest` wrapper for coroutine tests
- [ ] Use `coEvery` for suspend functions, `every` for regular
- [ ] Use Kotest matchers (`shouldBe`, `shouldThrow`)
- [ ] Use Turbine's `test { }` for Flow testing
- [ ] Always use named parameters in constructors and function calls
- [ ] Use `@TestFactory` with `dynamicTest` for parameterized tests

---

## 1. Test File Conventions

### File Naming
- **Pattern**: `{ClassName}Test.kt`
- **Examples**:
  - `AuthViewModelTest.kt`
  - `EloEngineTest.kt`
  - `GetPlayerUseCaseTest.kt`
  - `PlayerMapperTest.kt`

### File Location (KMP Structure)
- Common tests: `shared/src/commonTest/kotlin/`
- Android-specific tests: `shared/src/androidUnitTest/kotlin/`
- Package structure matches source code exactly

### Class Declaration
```kotlin
@ExtendWith(MockKExtension::class)
internal class AuthViewModelTest {
```

**Key Elements**:
- Use `@ExtendWith(MockKExtension::class)` for all tests
- Class visibility: `internal`

---

## 2. Mock Object Naming - **CRITICAL CONVENTION**

### The Golden Rule: NO PREFIXES OR SUFFIXES

Mock variable names are **simple camelCase** versions of the class/interface name, with `I` prefix dropped for interfaces.

### CORRECT Examples

```kotlin
@MockK
private lateinit var repository: PlayerRepository

@MockK
private lateinit var calculateElo: CalculateEloUseCase

@MockK
private lateinit var getPlayer: GetPlayerUseCase

@RelaxedMockK
private lateinit var logger: Logger
```

### WRONG Examples

```kotlin
@MockK
private lateinit var mockRepository: PlayerRepository  // WRONG - no "mock" prefix

@MockK
private lateinit var repositoryMock: PlayerRepository  // WRONG - no "mock" suffix

@MockK
private lateinit var fakeLogger: Logger  // WRONG - no "fake" prefix
```

---

## 3. Test Class Structure and Organization

### Standard Template

```kotlin
@ExtendWith(MockKExtension::class)
internal class AuthViewModelTest {

    @MockK
    private lateinit var login: LoginUseCase

    @MockK
    private lateinit var register: RegisterUseCase

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: AuthViewModel

    @BeforeEach
    fun setUp() {
        viewModel = AuthViewModel(
            login = login,
            register = register,
            dispatcher = testDispatcher,
        )
    }

    @Test
    fun `initial state is correct`() = runTest {
        viewModel.state.value shouldBe AuthState.Idle
    }
}
```

### Property Declaration Order

1. **@MockK / @RelaxedMockK dependencies** (mocks first)
2. **Test fixtures** (non-mock values like `StandardTestDispatcher`)
3. **System under test** (`lateinit var viewModel`, `lateinit var useCase`, etc.)
4. **@BeforeEach setup method**
5. **@Test methods**

### Spacing Between Sections

- **NO blank line** after class declaration
- **Single blank line** between property groups
- **Single blank line** between `@BeforeEach` and first test
- **Single blank line** between test methods

---

## 4. @MockK vs @RelaxedMockK

### Use @RelaxedMockK for:
- Loggers
- Analytics
- String formatters
- Dependencies where return value doesn't affect test logic

### Use @MockK for:
- Use cases
- Repositories
- Mappers
- Data providers
- Any dependency whose return values matter for test assertions

### Use @InjectMockKs for:
- System under test when using constructor injection
- Automatically injects other @MockK dependencies

```kotlin
@MockK
private lateinit var repository: PlayerRepository

@InjectMockKs
private lateinit var useCase: GetPlayerUseCase
```

---

## 5. Test Method Naming

### Pattern: Backticks with Descriptive Sentences

```kotlin
@Test
fun `initial state is correct`() = runTest { }

@Test
fun `onIntent should emit error effect when login fails`() = runTest { }

@Test
fun `getPlayer should throw NotFoundException when repository returns null`() = runTest { }

@Test
fun `NavigateTo effect navigates to direction when effect is NavigateTo`() = runTest { }

@Test
fun `invoke returns player data based on repository`() = runTest { }
```

### Naming Patterns:
- Use **backticks** for test names
- Write in **natural language**
- Include **WHEN** condition and **THEN** expectation when applicable
- Format: `{action/method} should {expected behavior} when {condition}`

---

## 6. Spacing and Formatting Within Tests

### Basic Pattern: Setup → Action → Assert

```kotlin
@Test
fun `NavigateTo effect navigates to direction`() = runTest {
    val direction = Back

    viewModel.onIntent(NavigateTo(direction))

    viewModel.effect.test {
        expectMostRecentItem() shouldBe AuthEffect.NavigateBack
    }
}
```

**Spacing Rules**:
- **NO blank line** after function signature
- **Single blank line** between setup and action
- **Single blank line** between action and assertion
- **Single blank line** between logical sections in complex tests

---

## 7. MockK Stubbing Patterns

### Suspend Functions: `coEvery`

```kotlin
coEvery { repository.getPlayer(any()) } returns player
coEvery { getPlayer(any()) } returns player
```

### Regular Functions: `every`

```kotlin
every { mapper.map(response) } returns domainModel
every { eloEngine.calculate(any(), any()) } returns 1250
```

### Unit-Returning Functions

```kotlin
// Suspend
coJustRun { repository.saveMatch(any()) }

// Regular
justRun { analytics.trackMatchStart() }
```

### Chained Returns with `andThen`

```kotlin
coEvery { repository.getPlayer(any()) } throws Exception() andThen player
```

### Answer-Based Stubbing

```kotlin
coEvery {
    repository.savePlayer(any())
} coAnswers { firstArg<Player>().copy(id = "generated-id") }
```

---

## 8. Assertion Patterns

### Kotest Matchers (Primary)

```kotlin
// Equality
viewModel.state.value shouldBe AuthState.Idle
result shouldBe expected

// Type checking
awaitError().shouldBeInstanceOf<NotFoundException>()
```

### Exception Testing

```kotlin
shouldThrow<NotFoundException> {
    repository.getPlayer(invalidId)
}

shouldThrow<Exception> {
    useCase.invoke(id = id)
}.apply {
    message shouldBe exception.message
}

shouldNotThrowAny {
    repository.saveMatch(match)
}
```

---

## 9. Flow Testing with Turbine

### Basic Flow Testing

```kotlin
viewModel.effect.test {
    expectMostRecentItem() shouldBe AuthEffect.NavigateBack
}

viewModel.state.test {
    expectMostRecentItem() shouldBe AuthState.Idle
}
```

### Multi-Item Flow Testing

```kotlin
repository.observeMatches().test {
    awaitItem() shouldBe initialMatches
    awaitItem() shouldBe updatedMatches
    awaitComplete()
}
```

---

## 10. ViewModel Testing Patterns

### Common ViewModel Tests

```kotlin
// Initial state
@Test
fun `initial state is correct`() = runTest {
    viewModel.state.value shouldBe AuthState.Idle
}

// State updates
@Test
fun `state updates when player is loaded`() = runTest {
    coEvery { getPlayer(any()) } returns player

    viewModel.state.test {
        viewModel.onIntent(PlayerIntent.Load("id"))

        skipItems(1) // Skip initial state
        awaitItem() shouldBe PlayerState.Content(player = player)
    }
}
```

---

## 11. UseCase Testing Patterns

### Simple Delegation UseCase

```kotlin
@ExtendWith(MockKExtension::class)
internal class GetPlayerUseCaseTest {

    @MockK
    private lateinit var repository: PlayerRepository

    @InjectMockKs
    private lateinit var useCase: GetPlayerUseCase

    @Test
    fun `invoke should return Player`() = runTest {
        val player = Player(
            id = "playerId",
            name = "Jan Kowalski",
            elo = 1200,
        )
        coEvery { repository.getPlayer(any()) } returns player

        useCase("playerId") shouldBe player
    }
}
```

### Exception Handling Tests

```kotlin
@Test
fun `invoke should throw exception when repository throws exception`() = runTest {
    coEvery { repository.getPlayer(any()) } throws Exception()

    shouldThrow<Exception> { useCase("invalidId") }
}
```

---

## 12. Parameterized Testing

### @TestFactory with dynamicTest

```kotlin
@TestFactory
fun `calculateElo returns correct delta`() =
    listOf(
        Triple(1200, 1200, 0.5) to 0,
        Triple(1200, 1000, 0.5) to 12,
        Triple(1000, 1200, 0.5) to -12,
    ).map { (input, expected) ->
        val (ratingA, ratingB, score) = input
        dynamicTest(
            "when ratingA=$ratingA ratingB=$ratingB score=$score should return $expected",
        ) {
            runTest {
                eloEngine.calculateDelta(ratingA, ratingB, score) shouldBe expected
            }
        }
    }
```

---

## 13. MockK Verification

### Basic Verification

```kotlin
verify(exactly = 1) { analytics.trackMatchStart() }

coVerify(exactly = 1) { repository.saveMatch(any()) }

verify { logger.d(any()) }
```

### Ordered Verification

```kotlin
coVerifyOrder {
    repository.loadPlayer(playerId)
    repository.saveMatch(match)
}
```

---

## 14. Running Tests

### Running Tests for the KMP Project

Use Gradle to run tests:

```bash
# Run all shared module tests
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

## 15. Common Test Libraries and Imports

```kotlin
// JUnit 5
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest

// MockK
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension

// Kotest
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.types.shouldBeInstanceOf

// Turbine
import app.cash.turbine.test

// Coroutines Test
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
```

---

## 16. File Locations (KMP Structure)

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
