package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.PricingType
import com.racketmatch.presentation.viewmodel.CoachServicesEvent
import com.racketmatch.presentation.viewmodel.CoachServicesState
import com.racketmatch.presentation.viewmodel.CoachServicesViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object CoachServicesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachServicesViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        var showAddSheet by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { viewModel.onEvent(CoachServicesEvent.Refresh) }

        Scaffold(
            containerColor = ProCircuit.Bg,
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.Bg
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj usługę")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Text(
                    "Moje usługi",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = ProCircuit.OnBg,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )

                when (val s = state) {
                    CoachServicesState.Loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    CoachServicesState.Error -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Błąd ładowania usług", color = ProCircuit.OnSurface)
                    }
                    is CoachServicesState.Content -> {
                        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                            items(s.services) { service ->
                                ServiceCard(
                                    service = service,
                                    onDeactivate = {
                                        viewModel.onEvent(CoachServicesEvent.DeactivateService(service.id))
                                    }
                                )
                            }
                            if (s.services.isEmpty()) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🎾", fontSize = 40.sp)
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                "Dodaj swoją pierwszą usługę",
                                                fontFamily = AppFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = ProCircuit.OnSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddSheet) {
            AddServiceSheet(
                onDismiss = { showAddSheet = false },
                onConfirm = { name, desc, type, price ->
                    viewModel.onEvent(CoachServicesEvent.AddService(name, desc, type, price))
                    showAddSheet = false
                }
            )
        }
    }
}

@Composable
private fun ServiceCard(service: CoachService, onDeactivate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    service.name,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ProCircuit.OnBg
                )
                service.description?.let {
                    Text(
                        it,
                        fontFamily = AppBodyFontFamily,
                        fontSize = 12.sp,
                        color = ProCircuit.OnSurface,
                        maxLines = 2
                    )
                }
            }
            val priceLabel = when (service.pricingType) {
                PricingType.PER_HOUR -> "${service.priceCents / 100} zł/h"
                PricingType.FIXED -> "${service.priceCents / 100} zł"
                PricingType.PER_PERSON -> "${service.priceCents / 100} zł/os"
            }
            Text(
                priceLabel,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = ProCircuit.Lime
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDeactivate, contentPadding = PaddingValues(0.dp)) {
            Text(
                "Dezaktywuj",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = ProCircuit.OnSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServiceSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pricingType by remember { mutableStateOf("PER_HOUR") }
    var priceInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Nowa usługa",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = ProCircuit.OnBg
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Opis (opcjonalnie)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "PER_HOUR" to "za godzinę",
                    "FIXED" to "stała cena",
                    "PER_PERSON" to "za osobę"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = pricingType == type,
                        onClick = { pricingType = type },
                        label = { Text(label, fontFamily = AppFontFamily, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it },
                label = { Text("Cena (zł)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val priceCents = (priceInput.toDoubleOrNull() ?: 0.0).times(100).toInt()
                    if (name.isNotBlank() && priceCents > 0) {
                        onConfirm(name, description.ifBlank { null }, pricingType, priceCents)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.Bg
                )
            ) {
                Text(
                    "DODAJ USŁUGĘ",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}
