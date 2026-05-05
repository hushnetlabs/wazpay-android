package com.zeny.wazpay.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BalanceCheckingScreen(error: String? = null, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (error != null) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Balance Check Failed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Make sure *99# works on your SIM by dialling it manually, and that your bank is linked to this number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "BalanceTransition")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "Rotation"
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
            Text("Checking Balance", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Automating USSD request...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.width(200.dp).height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                if (error != null) "Go Back" else "Cancel",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun BalanceResultScreen(balance: String, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                "ACCOUNT BALANCE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier.padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        balance,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Done", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
