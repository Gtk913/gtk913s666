package com.tcodeplayer.app.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class Protocol { LOCAL, SMB, FTP, WEBDAV }

data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val protocol: Protocol = Protocol.LOCAL,
)

/** 列表/网格切换、面包屑导航、协议图标、媒体类型图标。文件浏览逻辑由 mpvEx 提供。 */
@Composable
fun FileBrowserScreen(
    entries: List<FileEntry>,
    breadcrumbs: List<String>,
    gridMode: Boolean,
    onToggleViewMode: () -> Unit,
    onCrumbClick: (Int) -> Unit,
    onOpen: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f)) {
                breadcrumbs.forEachIndexed { index, crumb ->
                    Text(
                        text = if (index == breadcrumbs.lastIndex) crumb else "$crumb / ",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clickable { onCrumbClick(index) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
            IconButton(onClick = onToggleViewMode, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(
                    imageVector = if (gridMode) Icons.Filled.List else Icons.Filled.GridView,
                    contentDescription = "列表/网格切换",
                )
            }
        }

        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.name }) { entry ->
                    FileTile(entry = entry, onClick = { onOpen(entry) })
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.name }) { entry ->
                    FileRow(entry = entry, onClick = { onOpen(entry) })
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .sizeIn(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconFor(entry), contentDescription = null)
        Text(entry.name, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Icon(protocolIcon(entry.protocol), contentDescription = entry.protocol.name)
    }
}

@Composable
private fun FileTile(entry: FileEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(12.dp)
            .sizeIn(minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(iconFor(entry), contentDescription = null, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
        Text(entry.name, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        Icon(protocolIcon(entry.protocol), contentDescription = entry.protocol.name)
    }
}

private fun iconFor(entry: FileEntry): ImageVector = when {
    entry.isDirectory -> Icons.Filled.Folder
    entry.isVideo -> Icons.Filled.Movie
    entry.isAudio -> Icons.Filled.AudioFile
    else -> Icons.Filled.InsertDriveFile
}

private fun protocolIcon(protocol: Protocol): ImageVector = when (protocol) {
    Protocol.LOCAL -> Icons.Filled.Storage
    Protocol.SMB -> Icons.Filled.Cloud
    Protocol.FTP -> Icons.Filled.Cloud
    Protocol.WEBDAV -> Icons.Filled.Language
}
