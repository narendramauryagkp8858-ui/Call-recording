package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.Customer

@Composable
fun CustomerEditorDialog(
    initial: Customer?,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var phone by remember(initial) { mutableStateOf(initial?.phone ?: "") }
    var address by remember(initial) { mutableStateOf(initial?.address ?: "") }
    var gstin by remember(initial) { mutableStateOf(initial?.gstin ?: "") }
    var stateName by remember(initial) { mutableStateOf(initial?.stateName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Customer" else "Edit Saved Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Full Address") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_address_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase() },
                        label = { Text("GSTIN (Optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_gstin_input")
                    )
                    OutlinedTextField(
                        value = stateName,
                        onValueChange = { stateName = it },
                        label = { Text("State") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_state_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        (initial ?: Customer()).copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            gstin = gstin.trim().uppercase(),
                            stateName = stateName.trim()
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_customer_button")
            ) {
                Text(if (initial == null) "Save Customer" else "Update Customer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_customer_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteCustomerConfirmDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onConfirmDelete: (Customer) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Saved Customer?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Remove \"${customer.name}\" from your saved customers list?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Past GST bills and billing history for this customer will remain safe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(customer) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_customer_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_customer_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
