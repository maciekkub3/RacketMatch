package com.racketmatch.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.core.parameter.ParametersDefinition

@Composable
expect inline fun <reified T : ViewModel> kmpViewModel(
    noinline parameters: ParametersDefinition? = null
): T
