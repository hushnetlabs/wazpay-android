package com.zeny.wazpay.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun TransactionProcessingScreen(error: String? = null, onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (error != null) {
            Icon(
                Icons.Default.ErrorOutline, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.error, 
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Transaction Failed", 
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                error, 
                style = MaterialTheme.typography.bodyLarge, 
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text("Try Again", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = "Processing",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                "Processing", 
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            BouncingDotsText(text = "Automating USSD")
            
            Spacer(modifier = Modifier.height(80.dp))
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .width(200.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun BouncingDotsText(text: String) {
    var dotCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = (dotCount + 1) % 4
        }
    }

    val dots = ".".repeat(dotCount)
    
    Text(
        text = "$text$dots", 
        style = MaterialTheme.typography.bodyLarge, 
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(200.dp)
    )
}
