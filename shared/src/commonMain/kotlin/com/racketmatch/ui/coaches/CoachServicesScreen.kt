package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.PricingType
import com.racketmatch.presentation.viewmodel.CoachServicesEvent
import com.racketmatch.presentation.viewmodel.CoachServicesState
import com.racketmatch.presentation.viewmodel.CoachServicesViewModel
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.IconSquare
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object CoachServicesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachServicesViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        // The sheet hosts both add (editing = null) and edit (editing != null)
        // flows. Single composable, one save surface, consistent UX.
        var editing by remember { mutableStateOf<CoachService?>(null) }
        var showSheet by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { viewModel.onEvent(CoachServicesEvent.Refresh) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // ─ Editorial header ─
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 14.dp),
            ) {
                IconCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wstecz",
                    onClick = { navigator.pop() },
                )
                Spacer(Modifier.height(14.dp))
                Eyebrow("Katalog treningów")
                Spacer(Modifier.height(6.dp))
                H1("Moje usługi")
            }

            when (val s = state) {
                CoachServicesState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                CoachServicesState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Błąd ładowania usług",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        color = ProCircuit.OnSurface,
                    )
                }

                is CoachServicesState.Content -> {
                    val active = s.services.count { it.isActive }
                    val avgPrice = s.services
                        .filter { it.isActive }
                        .map { it.priceCents }
                        .takeIf { it.isNotEmpty() }
                        ?.average()
                        ?.toInt()
                        ?.div(100)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (s.services.isNotEmpty()) {
                            item { OverviewCard(activeCount = active, totalCount = s.services.size, avgPricePln = avgPrice) }
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                        items(s.services, key = { it.id }) { service ->
                            ServiceCard(
                                service = service,
                                onTap = {
                                    editing = service
                                    showSheet = true
                                },
                                onToggleActive = { checked ->
                                    viewModel.onEvent(
                                        CoachServicesEvent.ToggleActive(service.id, checked)
                                    )
                                },
                            )
                        }
                        if (s.services.isEmpty()) {
                            item { ServicesEmptyState(onAdd = { editing = null; showSheet = true }) }
                        } else {
                            item {
                                Spacer(Modifier.height(4.dp))
                                AddServiceInlineButton(onClick = { editing = null; showSheet = true })
                            }
                        }
                    }
                }
            }
        }

        if (showSheet) {
            ServiceEditSheet(
                editing = editing,
                onDismiss = { showSheet = false },
                onSave = { name, description, pricingType, priceCents, isActive ->
                    val existing = editing
                    if (existing == null) {
                        viewModel.onEvent(
                            CoachServicesEvent.AddService(name, description, pricingType, priceCents)
                        )
                    } else {
                        viewModel.onEvent(
                            CoachServicesEvent.UpdateService(
                                serviceId = existing.id,
                                name = name,
                                description = description,
                                pricingType = pricingType,
                                priceCents = priceCents,
                                isActive = isActive,
                            )
                        )
                    }
                    showSheet = false
                },
                onDelete = { existing ->
                    viewModel.onEvent(CoachServicesEvent.DeactivateService(existing.id))
                    showSheet = false
                },
            )
        }
    }
}

// ─── Overview card ────────────────────────────────────────────────────────

@Composable
private fun OverviewCard(activeCount: Int, totalCount: Int, avgPricePln: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ProCircuit.Forest)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OverviewStat(
            label = "AKTYWNE",
            value = activeCount.toString(),
            hint = "z $totalCount",
        )
        VerticalThinDivider()
        OverviewStat(
            label = "ŚR. CENA",
            value = avgPricePln?.let { "$it zł" } ?: "—",
            hint = "za usługę",
        )
    }
}

@Composable
private fun OverviewStat(label: String, value: String, hint: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = ProCircuit.ForestInk.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            letterSpacing = (-0.5).sp,
            color = ProCircuit.Lime,
        )
        Text(
            text = hint,
            fontFamily = AppBodyFontFamily,
            fontSize = 11.sp,
            color = ProCircuit.ForestInk.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun VerticalThinDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 56.dp)
            .background(ProCircuit.ForestInk.copy(alpha = 0.15f)),
    )
}

// ─── Service card ────────────────────────────────────────────────────────

@Composable
private fun ServiceCard(
    service: CoachService,
    onTap: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
) {
    val priceLabel = when (service.pricingType) {
        PricingType.PER_HOUR -> "${service.priceCents / 100} zł/h"
        PricingType.FIXED -> "${service.priceCents / 100} zł"
        PricingType.PER_PERSON -> "${service.priceCents / 100} zł/os"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (service.isActive) ProCircuit.SurfaceLow else ProCircuit.SurfaceLow.copy(alpha = 0.5f))
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconSquare(
            icon = Icons.Default.SportsTennis,
            contentDescription = null,
            background = if (service.isActive) ProCircuit.Lime.copy(alpha = 0.14f)
            else ProCircuit.SurfaceHigh,
            contentColor = if (service.isActive) ProCircuit.Lime else ProCircuit.OnSurface,
            size = 44.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = service.name,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (service.isActive) ProCircuit.OnBg else ProCircuit.OnSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = priceLabel,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = if (service.isActive) ProCircuit.Lime else ProCircuit.OnSurface,
                )
                if (!service.description.isNullOrBlank()) {
                    Text(
                        text = "·",
                        fontFamily = AppFontFamily,
                        fontSize = 12.sp,
                        color = ProCircuit.OnSurface,
                    )
                    Text(
                        text = service.description,
                        fontFamily = AppBodyFontFamily,
                        fontSize = 12.sp,
                        color = ProCircuit.OnSurface,
                        maxLines = 1,
                    )
                }
            }
        }
        Switch(
            checked = service.isActive,
            onCheckedChange = onToggleActive,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ProCircuit.LimeInk,
                checkedTrackColor = ProCircuit.Lime,
                uncheckedThumbColor = ProCircuit.OnSurface,
                uncheckedTrackColor = ProCircuit.SurfaceHigh,
            ),
        )
    }
}

// ─── Empty + add-inline ──────────────────────────────────────────────────

@Composable
private fun ServicesEmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🎾", fontSize = 48.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Brak usług",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Dodaj pierwszy trening, cennik i opis.\nUczniowie zobaczą je w Twoim profilu.",
            fontFamily = AppBodyFontFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = ProCircuit.OnSurface,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ProCircuit.Lime)
                .clickable(onClick = onAdd)
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Text(
                text = "+ DODAJ USŁUGĘ",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
                color = ProCircuit.LimeInk,
            )
        }
    }
}

@Composable
private fun AddServiceInlineButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconSquare(
            icon = Icons.Default.Add,
            contentDescription = null,
            background = ProCircuit.Lime.copy(alpha = 0.14f),
            contentColor = ProCircuit.Lime,
            size = 36.dp,
        )
        Text(
            text = "Dodaj kolejną usługę",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = ProCircuit.OnBg,
        )
    }
}

// ─── Edit / Add sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ServiceEditSheet(
    editing: CoachService?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, pricingType: String, priceCents: Int, isActive: Boolean) -> Unit,
    onDelete: (CoachService) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var description by remember(editing) { mutableStateOf(editing?.description ?: "") }
    var pricingType by remember(editing) { mutableStateOf(editing?.pricingType?.name ?: PricingType.PER_HOUR.name) }
    // Price in whole złoty for display + editing. Convert to cents on save.
    var priceText by remember(editing) {
        mutableStateOf(editing?.let { (it.priceCents / 100).toString() } ?: "")
    }
    var isActive by remember(editing) { mutableStateOf(editing?.isActive ?: true) }

    val priceValue = priceText.toIntOrNull() ?: 0
    val canSave = name.isNotBlank() && priceValue > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ProCircuit.Outline),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (editing == null) "Nowa usługa" else "Edytuj usługę",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = ProCircuit.OnBg,
            )

            SheetSectionLabel("NAZWA")
            PlainTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "np. Trening indywidualny 60 min",
                keyboardType = KeyboardType.Text,
            )

            SheetSectionLabel("OPIS (OPCJONALNIE)")
            PlainTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = "Krótki opis dla uczniów",
                keyboardType = KeyboardType.Text,
            )

            SheetSectionLabel("MODEL CENY")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PricingType.entries.forEach { type ->
                    SheetChip(
                        label = when (type) {
                            PricingType.PER_HOUR -> "Za godzinę"
                            PricingType.FIXED -> "Stała cena"
                            PricingType.PER_PERSON -> "Za osobę"
                        },
                        selected = pricingType == type.name,
                        onClick = { pricingType = type.name },
                    )
                }
            }

            SheetSectionLabel("CENA")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PlainTextField(
                        value = priceText,
                        onValueChange = { new -> priceText = new.filter { it.isDigit() } },
                        placeholder = "np. 150",
                        keyboardType = KeyboardType.Number,
                    )
                }
                Text(
                    text = "zł",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ProCircuit.OnSurface,
                )
            }
            // Quick-pick price chips — common round numbers.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(80, 100, 120, 150, 180, 200, 250).forEach { preset ->
                    SheetChip(
                        label = "$preset zł",
                        selected = priceValue == preset,
                        onClick = { priceText = preset.toString() },
                    )
                }
            }

            if (editing != null) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.SurfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Widoczna dla uczniów",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ProCircuit.OnBg,
                        )
                        Text(
                            text = if (isActive) "Aktywna — pojawia się w Twoim profilu" else "Nieaktywna — ukryta",
                            fontFamily = AppBodyFontFamily,
                            fontSize = 11.sp,
                            color = ProCircuit.OnSurface,
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ProCircuit.LimeInk,
                            checkedTrackColor = ProCircuit.Lime,
                            uncheckedThumbColor = ProCircuit.OnSurface,
                            uncheckedTrackColor = ProCircuit.SurfaceHigh,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                    .clickable(enabled = canSave) {
                        val cents = priceValue * 100
                        onSave(
                            name.trim(),
                            description.trim().ifBlank { null },
                            pricingType,
                            cents,
                            isActive,
                        )
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (editing == null) "DODAJ USŁUGĘ" else "ZAPISZ ZMIANY",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.4.sp,
                    color = if (canSave) ProCircuit.LimeInk else ProCircuit.OnSurface,
                )
            }

            if (editing != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDelete(editing) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Usuń usługę",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.LossRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        color = ProCircuit.OnSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (selected) ProCircuit.LimeInk else ProCircuit.OnBg,
        )
    }
}

@Composable
private fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.5f),
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ProCircuit.OnBg,
            unfocusedTextColor = ProCircuit.OnBg,
            focusedBorderColor = ProCircuit.Lime,
            unfocusedBorderColor = ProCircuit.OnSurface.copy(alpha = 0.3f),
            cursorColor = ProCircuit.Lime,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
