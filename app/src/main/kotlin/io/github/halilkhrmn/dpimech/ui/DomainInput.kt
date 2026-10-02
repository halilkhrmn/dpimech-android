package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.Hostlist

/** Extra domains as removable pills, with a field that adds what is typed or pasted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DomainPills(
    domains: List<String>,
    input: String,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = input, onValueChange = onInput,
            label = { Text(stringResource(R.string.profile_extra_domains)) },
            supportingText = { Text(stringResource(R.string.profile_extra_domains_hint)) },
            singleLine = true,
            // No autocorrect: it adds spaces after dots and "fixes" domain names.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            trailingIcon = {
                IconButton(onClick = onAdd, enabled = Hostlist.parseInput(input).isNotEmpty()) {
                    Icon(Icons.Default.Add, stringResource(R.string.profile_domain_add))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (domains.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                domains.forEach { d ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(d) },
                        label = { Text(d) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.profile_domain_remove, d),
                                Modifier.size(InputChipDefaults.AvatarSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

