package com.chess.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chess.app.data.Profile

private val SKILL_LABELS = listOf("Beginner","Easy","Medium","Hard","Expert")
private val SKILL_VALUES = listOf(0, 5, 10, 15, 20)
private val TIME_LABELS  = listOf("1 min","5 min","10 min","30 min","Unlimited")
private val TIME_VALUES  = listOf(1, 5, 10, 30, 0)

@Composable
fun ProfileScreen(
    vm: ProfileViewModel = viewModel(),
    onPlay: (Profile) -> Unit
) {
    val profiles by vm.profiles.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text("Chess", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Select a profile to play", color = Color(0xFF8888AA), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(profiles) { p ->
                ProfileCard(
                    profile = p,
                    onPlay = { onPlay(p) },
                    onEdit = { editing = p; showDialog = true },
                    onDelete = { vm.delete(p) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) { Text("New Profile") }
    }

    if (showDialog) {
        ProfileDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { vm.save(it); showDialog = false }
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile, onPlay: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val skillLabel = SKILL_LABELS[SKILL_VALUES.indexOf(profile.skillLevel).coerceAtLeast(0)]
    val timeLabel  = TIME_LABELS[TIME_VALUES.indexOf(profile.timeControlMinutes).coerceAtLeast(0)]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A4A)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("$skillLabel  |  $timeLabel", color = Color(0xFF8888AA), fontSize = 12.sp)
            }
            TextButton(onClick = onEdit) { Text("Edit", color = Color(0xFF6A9FD8)) }
            TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFE74C3C)) }
            Button(onClick = onPlay, shape = RoundedCornerShape(6.dp)) { Text("Play") }
        }
    }
}

@Composable
private fun ProfileDialog(initial: Profile?, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var skillIdx by remember { mutableIntStateOf(
        SKILL_VALUES.indexOf(initial?.skillLevel ?: 10).coerceAtLeast(0)
    ) }
    var timeIdx by remember { mutableIntStateOf(
        TIME_VALUES.indexOf(initial?.timeControlMinutes ?: 10).coerceAtLeast(0)
    ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2A2A4A),
        title = { Text(if (initial == null) "New Profile" else "Edit Profile", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color(0xFF8888AA)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Text("Difficulty", color = Color(0xFF8888AA), fontSize = 12.sp)
                ChipRow(SKILL_LABELS, skillIdx) { skillIdx = it }
                Text("Time Control", color = Color(0xFF8888AA), fontSize = 12.sp)
                ChipRow(TIME_LABELS, timeIdx) { timeIdx = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onSave(
                        (initial ?: Profile(name = "")).copy(
                            name = name.trim(),
                            skillLevel = SKILL_VALUES[skillIdx],
                            timeControlMinutes = TIME_VALUES[timeIdx]
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8888AA)) } }
    )
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1A1A2E),
                        RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF444466), RoundedCornerShape(16.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(label, color = Color.White, fontSize = 11.sp)
            }
        }
    }
}
