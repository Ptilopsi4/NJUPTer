package com.example.njupter.ui.timetable.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.njupter.R

@Composable
fun EmptyGuidePlaceholder(
    onCreateTimetable: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Fallback to launcher icon if specific graphic not available
        Icon(
            painter = painterResource(id = R.drawable.ic_launcher_foreground), 
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.no_timetable_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.no_timetable_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onCreateTimetable,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.create_timetable))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyGuidePlaceholderPreview() {
    MaterialTheme {
        EmptyGuidePlaceholder(
            onCreateTimetable = {}
        )
    }
}

