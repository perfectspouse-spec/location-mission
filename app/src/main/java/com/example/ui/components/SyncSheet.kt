package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.sync.SyncState
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSheet(
    syncState: SyncState,
    onDeviceTypeChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onSyncCodeChange: (String) -> Unit,
    onTriggerSync: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }

    val formattedLastSync = remember(syncState.lastSyncTime) {
        syncState.lastSyncTime?.let {
            SimpleDateFormat("HH:mm:ss (dd MMM)", Locale.forLanguageTag("tr")).format(Date(it))
        } ?: "Henüz senkronize edilmedi"
    }

    fun signIn() {
        scope.launch {
            isSigningIn = true
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(com.example.R.string.default_web_client_id))
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
                auth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener { task ->
                        isSigningIn = false
                        if (task.isSuccessful) {
                            currentUser = auth.currentUser
                            Toast.makeText(context, "Google hesabı ile giriş yapıldı.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Giriş başarısız: ${task.exception?.localizedMessage ?: "Bilinmeyen hata"}", Toast.LENGTH_LONG).show()
                        }
                    }
            } catch (e: GetCredentialException) {
                isSigningIn = false
                Toast.makeText(context, "Google hesabı seçilemedi: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                isSigningIn = false
                Toast.makeText(context, "Giriş hatası: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Bulut Senkronizasyonu", style = MaterialTheme.typography.titleLarge)
            Text("Aynı Google hesabıyla telefon ve tablette oturum açın.")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .25f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    if (currentUser == null) {
                        Text("Google hesabı bağlı değil", style = MaterialTheme.typography.titleMedium)
                        Text("Şimdilik yalnızca hesabı doğruluyoruz. Mevcut görevleriniz buluta gönderilmeyecek.")
                        Button(onClick = { signIn() }, enabled = !isSigningIn) {
                            if (isSigningIn) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isSigningIn) "Giriş yapılıyor…" else "Google ile giriş yap")
                        }
                    } else {
                        Icon(Icons.Default.CloudDone, contentDescription = null)
                        Text(currentUser?.displayName ?: "Google hesabı", style = MaterialTheme.typography.titleMedium)
                        Text(currentUser?.email ?: "Firebase oturumu açık")
                        Text("Firebase Authentication bağlantısı başarılı.")
                        OutlinedButton(onClick = {
                            scope.launch {
                                auth.signOut()
                                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                                currentUser = null
                            }
                        }) { Text("Çıkış yap") }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bu cihaz", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = syncState.deviceType == "Tablet",
                            onClick = { onDeviceTypeChange("Tablet") },
                            label = { Text("Tablet") },
                            leadingIcon = { Icon(Icons.Default.Tablet, null) }
                        )
                        FilterChip(
                            selected = syncState.deviceType == "Telefon",
                            onClick = { onDeviceTypeChange("Telefon") },
                            label = { Text("Telefon") },
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) }
                        )
                    }
                    Text("Son senkronizasyon: $formattedLastSync")
                }
            }

            Text(
                if (currentUser == null) "Bulut senkronizasyonu için önce Google hesabınızla giriş yapın."
                else "Hesap hazır. Bir sonraki aşamada Room ↔ Firestore görev senkronizasyonunu bağlayacağız.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
