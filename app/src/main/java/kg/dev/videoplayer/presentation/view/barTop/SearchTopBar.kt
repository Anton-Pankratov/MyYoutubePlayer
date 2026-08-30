package kg.dev.videoplayer.presentation.view.barTop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kg.dev.videoplayer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    currentTab: String,
    searchQuery: String,
    inSearching: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onSearchIconClick: (String) -> Unit
) {

    CenterAlignedTopAppBar(
        title = {
            if (inSearching) {
                InSearchingModeTopBar(searchQuery, onSearchQueryChanged)
            } else {
                NotInSearchingModeTopBar()
            }
        },
        actions = {
            IconTopBar(currentTab, onSearchIconClick)
        }
    )
}

@Composable
private fun NotInSearchingModeTopBar() {
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InSearchingModeTopBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isPositioned = remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                focusRequester.freeFocus()
                keyboard?.hide()
            }
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded.value,
            onExpandedChange = { expanded.value = it }
        ) {
            TextField(
                value = searchQuery,
                onValueChange = {
                    onSearchQueryChanged(it)
                    expanded.value = it.isNotBlank()
                },
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .focusRequester(focusRequester)
                    .onGloballyPositioned {
                        isPositioned.value = true
                    },
                placeholder = { Text(text = stringResource(R.string.input_query)) },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            if (expanded.value) {
                ExposedDropdownMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false }
                ) {
                    listOf("Apple", "Banana", "Cherry").forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                onSearchQueryChanged(suggestion)
                                expanded.value = false
                            }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isPositioned.value) {
        if (isPositioned.value) {
            focusRequester.requestFocus()
        }
    }
}