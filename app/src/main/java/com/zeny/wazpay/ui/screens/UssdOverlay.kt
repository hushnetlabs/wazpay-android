package com.zeny.wazpay.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun UssdOverlayContent(isBalanceCheck: Boolean, onClose: () -> Unit) {
    var isTakingLong by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(12000)
        isTakingLong = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "OverlayPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)) // Solid minimalist dark background
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Close Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close overlay",
                tint = Color.White.copy(alpha = 0.7f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Minimalist Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBalanceCheck) Icons.Rounded.AccountBalanceWallet else Icons.Rounded.Payments,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(48.dp))

            Text(
                text = if (isBalanceCheck) "Checking Balance" else "Processing Payment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))
            
            OverlayBouncingDotsText()

            Spacer(Modifier.height(48.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = "Secure",
                    tint = if (isTakingLong) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isTakingLong) "This is taking longer than usual.\nTry going back and trying again." else "Securely communicating with your carrier\nPlease do not close the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTakingLong) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f
                )
            }
        }
    }
}

@Composable
fun OverlayBouncingDotsText() {
    var dotCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            dotCount = (dotCount + 1) % 4
        }
    }

    val dots = ".".repeat(dotCount)
    
    Text(
        text = "Automating USSD requests$dots", 
        style = MaterialTheme.typography.bodyLarge, 
        color = Color.White.copy(alpha = 0.8f),
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(260.dp)
    )
}
