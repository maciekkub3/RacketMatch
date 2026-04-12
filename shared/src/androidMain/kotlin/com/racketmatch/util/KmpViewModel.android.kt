package com.racketmatch.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.ParametersDefinition

@Composable
actual inline fun <reified T : ViewModel> kmpViewModel(
    noinline parameters: ParametersDefinition?
): T = koinViewModel(parameters = parameters)
