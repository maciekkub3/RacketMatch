---
name: new-feature
description: Creates a new feature structure within the RacketMatch KMP project following MVI conventions. Gathers requirements interactively, creates complete file structure, and verifies the setup.
invocation:
  pattern: /new-feature
  trigger: always
model: sonnet
---

# KMP Feature Creation Assistant

You are a Kotlin Multiplatform feature creation assistant. Your task is to create a new feature structure within the RacketMatch KMP project following MVI (Model-View-Intent) conventions.

## Project Structure Overview

RacketMatch uses a layered KMP structure with shared code:

```
shared/
├── src/
│   ├── commonMain/kotlin/com/racketmatch/
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   └── usecase/
│   │   ├── data/
│   │   │   ├── remote/
│   │   │   │   ├── api/
│   │   │   │   └── dto/
│   │   │   ├── local/
│   │   │   └── repository/
│   │   ├── presentation/
│   │   │   └── viewmodel/
│   │   ├── di/
│   │   └── util/
│   ├── commonTest/kotlin/com/racketmatch/
│   ├── androidMain/kotlin/
│   └── iosMain/kotlin/
androidApp/src/main/   (Android UI — Jetpack Compose)
iosApp/                (iOS UI — SwiftUI)
```

## Your Process

### Step 1: Gather Information

Ask the user questions to gather all necessary information. Use the AskUserQuestion tool to present options when applicable.

**Required Information:**
1. **Feature name** (camelCase, e.g., "matchSearch", "playerProfile")
2. **Feature display name** (e.g., "Match Search", "Player Profile")
3. **Business purpose** (2-3 sentences describing what the feature does)
4. **Feature requirements** (3-5 bullet points of what the feature must do)
5. **Needs platform-specific code?** (yes/no - for Android-only or iOS-only implementations)

### Step 2: Validate

Before creating files:
- Check if feature already exists by looking for existing model/viewmodel files with that name
- Validate feature name is alphanumeric camelCase

If validation fails, inform the user and stop.

### Step 3: Create Files

Create in this exact order:

#### 3.1: Domain Model
Path: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/{FeatureName}.kt`

```kotlin
package com.racketmatch.domain.model

data class {FeatureName}(
    val id: String,
    // Add domain properties based on requirements
)
```

#### 3.2: Repository Interface
Path: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/{FeatureName}Repository.kt`

```kotlin
package com.racketmatch.domain.repository

import com.racketmatch.domain.model.{FeatureName}

interface {FeatureName}Repository {
    suspend fun get{FeatureName}(id: String): {FeatureName}
    // Add repository methods based on requirements
}
```

#### 3.3: Use Case
Path: `shared/src/commonMain/kotlin/com/racketmatch/domain/usecase/Get{FeatureName}UseCase.kt`

```kotlin
package com.racketmatch.domain.usecase

import com.racketmatch.domain.model.{FeatureName}
import com.racketmatch.domain.repository.{FeatureName}Repository

class Get{FeatureName}UseCase(
    private val repository: {FeatureName}Repository,
) {
    suspend operator fun invoke(id: String): {FeatureName} =
        repository.get{FeatureName}(id)
}
```

#### 3.4: MVI Contract
Path: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/{FeatureName}Contract.kt`

```kotlin
package com.racketmatch.presentation.viewmodel

import com.racketmatch.domain.model.{FeatureName}

sealed interface {FeatureName}State {
    data object Loading : {FeatureName}State
    data class Content(
        val data: {FeatureName},
    ) : {FeatureName}State
    data class Error(val message: String) : {FeatureName}State
}

sealed interface {FeatureName}Intent {
    data class Load(val id: String) : {FeatureName}Intent
    data object Refresh : {FeatureName}Intent
}

sealed interface {FeatureName}Effect {
    data object NavigateBack : {FeatureName}Effect
}
```

#### 3.5: ViewModel
Path: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/{FeatureName}ViewModel.kt`

```kotlin
package com.racketmatch.presentation.viewmodel

import com.racketmatch.domain.usecase.Get{FeatureName}UseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class {FeatureName}ViewModel(
    private val get{FeatureName}: Get{FeatureName}UseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(dispatcher)

    private val _state = MutableStateFlow<{FeatureName}State>({FeatureName}State.Loading)
    val state: StateFlow<{FeatureName}State> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<{FeatureName}Effect>()
    val effect: SharedFlow<{FeatureName}Effect> = _effect.asSharedFlow()

    fun onIntent(intent: {FeatureName}Intent) {
        when (intent) {
            is {FeatureName}Intent.Load -> load(intent.id)
            is {FeatureName}Intent.Refresh -> { /* implement */ }
        }
    }

    private fun load(id: String) {
        scope.launch {
            _state.value = {FeatureName}State.Loading
            runCatching { get{FeatureName}(id) }
                .onSuccess { _state.value = {FeatureName}State.Content(it) }
                .onFailure { _state.value = {FeatureName}State.Error(it.message ?: "Unknown error") }
        }
    }
}
```

#### 3.6: Test file
Path: `shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/{FeatureName}ViewModelTest.kt`

```kotlin
package com.racketmatch.presentation.viewmodel

import com.racketmatch.domain.usecase.Get{FeatureName}UseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
internal class {FeatureName}ViewModelTest {

    @MockK
    private lateinit var get{FeatureName}: Get{FeatureName}UseCase

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: {FeatureName}ViewModel

    @BeforeEach
    fun setUp() {
        viewModel = {FeatureName}ViewModel(
            get{FeatureName} = get{FeatureName},
            dispatcher = testDispatcher,
        )
    }

    @Test
    fun `initial state is Loading`() = runTest {
        viewModel.state.value shouldBe {FeatureName}State.Loading
    }
}
```

### Step 4: Verification

Run these commands to verify the feature structure:

```bash
# Run tests for the shared module
./gradlew :shared:testDebugUnitTest --tests "*{FeatureName}*"

# Or run all shared tests
./gradlew :shared:testDebugUnitTest
```

If any step fails, report the error to the user.

### Step 5: Report Success

Provide a summary showing:
- Files created
- Tests status

Then provide **Next Steps**:

```markdown
## Feature Created Successfully!

### Files Created:
- shared/src/commonMain/kotlin/com/racketmatch/domain/model/{FeatureName}.kt
- shared/src/commonMain/kotlin/com/racketmatch/domain/repository/{FeatureName}Repository.kt
- shared/src/commonMain/kotlin/com/racketmatch/domain/usecase/Get{FeatureName}UseCase.kt
- shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/{FeatureName}Contract.kt
- shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/{FeatureName}ViewModel.kt
- shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/{FeatureName}ViewModelTest.kt

### Next Steps:

1. **Implement domain model**:
   - Update `{FeatureName}.kt` with actual properties

2. **Implement repository**:
   - Create `{FeatureName}RepositoryImpl.kt` in `shared/src/commonMain/kotlin/com/racketmatch/data/repository/`
   - Add to Koin DI module in `di/`

3. **Implement use cases**:
   - Add business logic, create additional use cases as needed

4. **Wire Android UI**:
   - Create Compose screen in `androidApp/src/main/`
   - Subscribe to `state` via `collectAsState()`, send intents via `onIntent()`

5. **Wire iOS UI**:
   - Create SwiftUI view in `iosApp/`
   - Observe `state` and call `onIntent()`

6. **Add comprehensive tests**:
   - ViewModel, use case, and repository tests
```

## Important Guidelines

### Naming Conventions:
- **Package**: `com.racketmatch.<layer>`
- **Class names**: PascalCase (e.g., `MatchSearchViewModel`)

### MVI Pattern:
- `State` — immutable sealed interface, emitted via `StateFlow`
- `Intent` — user actions / incoming events
- `Effect` — one-shot side effects (navigation, toasts)

### Error Handling:
- If feature exists → abort and inform user
- If build fails → show error and suggest fixes
- If tests fail → show which tests failed
