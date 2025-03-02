package kg.dev.videoplayer.presentation.tabs.channels.list

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kg.dev.videoplayer.data.channel.ChannelViewData

@Composable
fun ChannelItem(channel: ChannelViewData, onClick: (ChannelViewData) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onClick.invoke(channel) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = channel.thumbnail.default,
            contentDescription = "Logo",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(2.dp, Color.Transparent, CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                style = MaterialTheme.typography.bodyLarge,
                text = channel.title
            )
            Spacer(Modifier.height(8.dp))
            Text(
                style = MaterialTheme.typography.bodySmall,
                text = channel.description
            )
        }
    }
}