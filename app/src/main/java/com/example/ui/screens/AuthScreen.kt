package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.formatE164PhoneNumber
import com.example.data.sendPhoneOtp
import com.example.data.verifyPhoneOtp
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var countryCode by rememberSaveable { mutableStateOf("+91") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var sentPhoneE164 by rememberSaveable { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var resendSecondsLeft by rememberSaveable { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resendSecondsLeft) {
        if (resendSecondsLeft > 0) {
            delay(1000L)
            resendSecondsLeft -= 1
        }
    }

    fun triggerSendOtp(isResend: Boolean = false) {
        errorMessage = null
        val formatted = if (isResend && !sentPhoneE164.isNullOrBlank()) {
            sentPhoneE164
        } else {
            formatE164PhoneNumber(countryCode, phoneNumber)
        }
        if (formatted == null) {
            errorMessage = "Please enter a valid 10-digit mobile number."
            return
        }
        isLoading = true
        sendPhoneOtp(
            context = context,
            e164PhoneNumber = formatted,
            scope = scope,
            resendToken = if (isResend) resendToken else null,
            onCodeSent = { verId, token ->
                isLoading = false
                verificationId = verId
                sentPhoneE164 = formatted
                resendToken = token
                resendSecondsLeft = 60
                otpCode = ""
            },
            onAutoVerified = {
                isLoading = false
                onAuthSuccess()
            },
            onError = { msg ->
                isLoading = false
                errorMessage = msg
            }
        )
    }

    fun triggerVerifyOtp() {
        val verId = verificationId
        if (verId.isNullOrBlank()) {
            errorMessage = "Please request an OTP first."
            return
        }
        errorMessage = null
        isLoading = true
        verifyPhoneOtp(
            verificationId = verId,
            otpCode = otpCode,
            scope = scope,
            onSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onError = { msg ->
                isLoading = false
                errorMessage = msg
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_billing_hero_1795181049165),
                        contentDescription = "Narendra Billing shop counter illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Narendra Billing",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Mobile Number Login • GST Billing Software",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Phone Number / OTP Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (verificationId == null) {
                            Text(
                                text = "Sign in with Mobile Number",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Enter your mobile number to receive a 6-digit OTP verification SMS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                OutlinedTextField(
                                    value = countryCode,
                                    onValueChange = { input ->
                                        val cleaned = input.filter { it.isDigit() || it == '+' }.take(5)
                                        countryCode = if (cleaned.startsWith("+")) cleaned else "+$cleaned"
                                    },
                                    label = { Text("Code") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier
                                        .width(92.dp)
                                        .testTag("country_code_input")
                                )

                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { input ->
                                        phoneNumber = input.filter { it.isDigit() || it == ' ' }.take(14)
                                        errorMessage = null
                                    },
                                    label = { Text("Mobile Number") },
                                    placeholder = { Text("9876543210") },
                                    leadingIcon = {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { if (!isLoading) triggerSendOtp() }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("phone_number_input")
                                )
                            }

                            if (!errorMessage.isNullOrBlank()) {
                                ErrorBanner(errorMessage!!)
                            }

                            Button(
                                onClick = { triggerSendOtp(isResend = false) },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("send_otp_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Sending OTP...")
                                } else {
                                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Send OTP",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enter 6-Digit OTP",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Sent to ${sentPhoneE164.orEmpty()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        verificationId = null
                                        otpCode = ""
                                        errorMessage = null
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier.testTag("change_phone_button")
                                ) {
                                    Text("Change")
                                }
                            }

                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { input ->
                                    otpCode = input.filter { it.isDigit() }.take(6)
                                    errorMessage = null
                                },
                                label = { Text("6-Digit OTP") },
                                placeholder = { Text("123456") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { if (!isLoading) triggerVerifyOtp() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_code_input")
                            )

                            if (!errorMessage.isNullOrBlank()) {
                                ErrorBanner(errorMessage!!)
                            }

                            Button(
                                onClick = { triggerVerifyOtp() },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("verify_otp_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Verifying...")
                                } else {
                                    Text(
                                        text = "Verify & Login",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { triggerSendOtp(isResend = true) },
                                enabled = !isLoading && resendSecondsLeft == 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("resend_otp_button")
                            ) {
                                Text(
                                    if (resendSecondsLeft > 0) "Resend OTP in ${resendSecondsLeft}s"
                                    else "Resend OTP"
                                )
                            }
                        }
                    }
                }

                // Feature Highlights
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FeatureRow(
                            icon = Icons.Default.Storefront,
                            title = "Your Own Shop, GSTIN, Mohar & Sign",
                            desc = "Add multiple shops, upload your stamp (Mohar) & signature, print either or both."
                        )
                        FeatureRow(
                            icon = Icons.Default.ReceiptLong,
                            title = "Khareed & Bikri Rate Privacy",
                            desc = "Track purchase & selling rates privately while showing ONLY selling rate on customer bills."
                        )
                        FeatureRow(
                            icon = Icons.Default.Print,
                            title = "WhatsApp PDF & Mini Thermal Print",
                            desc = "Share proper A4 GST invoices on WhatsApp or print via 58mm/80mm Bluetooth printer."
                        )
                        FeatureRow(
                            icon = Icons.Default.Calculate,
                            title = "Built-in GST Calculator",
                            desc = "Exclusive & Inclusive GST calculator with CGST, SGST, and IGST splits."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
