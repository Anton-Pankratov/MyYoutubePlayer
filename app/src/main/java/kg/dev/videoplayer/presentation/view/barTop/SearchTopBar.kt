package kg.dev.videoplayer.presentation.view.barTop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.input_query)) },
                    colors = TextFieldDefaults.colors(
                        focusedPlaceholderColor = Color.Transparent,
                        unfocusedPlaceholderColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            } else {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        },
        actions = {
            IconTopBar(currentTab, onSearchIconClick)
        }
    )
}