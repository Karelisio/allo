package com.karelisio.allo.ui.contacts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.data.ContactEdit
import com.karelisio.allo.data.PhoneEntry
import com.karelisio.allo.ui.Routes
import com.karelisio.allo.ui.back
import com.karelisio.allo.ui.components.AlloShapes
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val PHONE_TYPES = listOf(Phone.TYPE_MOBILE to "Mobile", Phone.TYPE_HOME to "Domicile", Phone.TYPE_WORK to "Travail", Phone.TYPE_MAIN to "Principal", Phone.TYPE_OTHER to "Autre")

/** Création et modification d'un contact (ContactsContract). */
@Composable
fun ContactEditScreen(nav: NavHostController, lookupKey: String?, initialNumber: String, initialNote: String = "") {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(ContactEdit(phones = listOf(PhoneEntry(initialNumber, "Mobile")), note = initialNote)) }
    var existingPhoto by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(emptyList<com.karelisio.allo.data.ContactAccount>()) }
    var accountMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (lookupKey == null) {
            accounts = withContext(Dispatchers.IO) { Graph.contacts.writableAccounts() }
            // Par défaut : le premier compte Google s'il existe (contacts sauvegardés), sinon le téléphone.
            edit = edit.copy(account = accounts.firstOrNull { it.type == "com.google" })
        }
    }
    LaunchedEffect(lookupKey) {
        if (lookupKey != null) {
            val d = withContext(Dispatchers.IO) { Graph.contacts.detail(lookupKey) } ?: return@LaunchedEffect
            existingPhoto = d.photoUri
            edit = ContactEdit(
                contactId = d.id,
                givenName = d.givenName.ifEmpty { d.name },
                familyName = d.familyName,
                phones = d.phones.ifEmpty { listOf(PhoneEntry("", "Mobile")) },
                emails = d.emails.map { it.address },
                note = d.note,
            )
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadScaled(ctx, uri, 720) }
            if (bmp != null) {
                preview = bmp
                val bytes = withContext(Dispatchers.Default) { ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray() }
                edit = edit.copy(photo = bytes)
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (lookupKey == null) "Nouveau contact" else "Modifier") },
            navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.Rounded.Close, "Annuler") } },
            actions = {
                Button(enabled = !saving && (edit.givenName.isNotBlank() || edit.familyName.isNotBlank() || edit.phones.any { it.number.isNotBlank() }), onClick = {
                    saving = true
                    scope.launch {
                        val key = Graph.contacts.save(edit)
                        saving = false
                        if (key == null) { ctx.toast("Enregistrement impossible (autorisation Contacts ?)"); return@launch }
                        nav.popBackStack()
                        if (lookupKey == null) nav.navigate(Routes.contact(key))
                    }
                }, modifier = Modifier.padding(end = 8.dp)) { Text("Enregistrer") }
            },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).clip(AlloShapes.cookie).clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, contentAlignment = Alignment.Center) {
                    val p = preview
                    if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Avatar("${edit.givenName} ${edit.familyName}".trim().ifEmpty { null }, existingPhoto, size = 132.dp, shape = AlloShapes.cookie)
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(36.dp).clip(AlloShapes.circle).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PhotoCamera, "Photo", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (lookupKey == null) {
                Box {
                    androidx.compose.material3.AssistChip(
                        onClick = { accountMenu = true },
                        label = { Text("Enregistrer dans : " + (edit.account?.label ?: "Téléphone")) },
                    )
                    DropdownMenu(accountMenu, { accountMenu = false }) {
                        DropdownMenuItem(text = { Text("Téléphone (non synchronisé)") }, onClick = { accountMenu = false; edit = edit.copy(account = null) })
                        accounts.forEach { a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { accountMenu = false; edit = edit.copy(account = a) }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(edit.givenName, { edit = edit.copy(givenName = it) }, Modifier.fillMaxWidth(), label = { Text("Prénom") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(edit.familyName, { edit = edit.copy(familyName = it) }, Modifier.fillMaxWidth(), label = { Text("Nom") }, singleLine = true)
            Spacer(Modifier.height(16.dp))
            Text("Numéros", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            edit.phones.forEachIndexed { i, p ->
                var typeMenu by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(p.number, { v -> edit = edit.copy(phones = edit.phones.toMutableList().also { it[i] = p.copy(number = v) }) },
                        Modifier.weight(1f), label = { Text("Téléphone") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Box {
                        TextButton(onClick = { typeMenu = true }) { Text(PHONE_TYPES.firstOrNull { it.first == p.type }?.second ?: p.label) }
                        DropdownMenu(typeMenu, { typeMenu = false }) {
                            PHONE_TYPES.forEach { (t, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = {
                                    typeMenu = false
                                    edit = edit.copy(phones = edit.phones.toMutableList().also { it[i] = p.copy(type = t, label = l) })
                                })
                            }
                        }
                    }
                    if (edit.phones.size > 1) IconButton(onClick = { edit = edit.copy(phones = edit.phones.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Rounded.Close, "Retirer") }
                }
            }
            TextButton(onClick = { edit = edit.copy(phones = edit.phones + PhoneEntry("", "Mobile")) }) {
                Icon(Icons.Rounded.Add, null); Text("Ajouter un numéro")
            }
            Spacer(Modifier.height(8.dp))
            Text("E-mails", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            edit.emails.forEachIndexed { i, e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(e, { v -> edit = edit.copy(emails = edit.emails.toMutableList().also { it[i] = v }) }, Modifier.weight(1f),
                        label = { Text("E-mail") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    IconButton(onClick = { edit = edit.copy(emails = edit.emails.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Rounded.Close, "Retirer") }
                }
            }
            TextButton(onClick = { edit = edit.copy(emails = edit.emails + "") }) { Icon(Icons.Rounded.Add, null); Text("Ajouter un e-mail") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(edit.note, { edit = edit.copy(note = it) }, Modifier.fillMaxWidth(), label = { Text("Note") }, minLines = 2)
            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun loadScaled(ctx: android.content.Context, uri: Uri, max: Int): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (o.outWidth / (s * 2) >= max && o.outHeight / (s * 2) >= max) s *= 2
    val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
    val side = minOf(b.width, b.height)
    val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
    if (side > max) Bitmap.createScaledBitmap(sq, max, max, true) else sq
}.getOrNull()
