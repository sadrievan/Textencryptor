package com.example.textencryptor

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EncryptorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptorScreen() {
    var originalText by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }
    var encryptedResult by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Шифровальщик") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = originalText,
                onValueChange = { originalText = it },
                label = { Text("Исходная фраза") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = { Text("Ключ / Пароль") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (originalText.isNotBlank() && keyText.isNotBlank()) {
                        encryptedResult = encryptText(originalText, keyText)
                    } else {
                        Toast.makeText(context, "Заполните оба поля", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Зашифровать")
            }

            if (encryptedResult.isNotEmpty()) {
                Text("Результат (Base64):", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = encryptedResult,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Button(
                    onClick = { saveToFile(context, encryptedResult) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сохранить в *.txt")
                }
            }
        }
    }
}

// Простое AES-GCM шифрование
fun encryptText(text: String, password: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    // В реальном приложении используйте PBKDF2 для получения ключа из пароля.
    // Здесь для простоты берем хэш пароля (не для продакшена!).
    val keyBytes = password.toByteArray().copyOf(16) 
    val secretKey = SecretKeySpec(keyBytes, "AES")
    
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
    
    val encrypted = cipher.doFinal(text.toByteArray())
    val combined = salt + iv + encrypted
    return Base64.getEncoder().encodeToString(combined)
}

// Сохранение в Downloads через MediaStore
fun saveToFile(context: Context, content: String) {
    val fileName = "encrypted_${System.currentTimeMillis()}.txt"
    val resolver = context.contentResolver

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Toast.makeText(context, "Требуется Android 10+", Toast.LENGTH_SHORT).show()
        return
    }

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
    }

    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    uri?.let {
        resolver.openOutputStream(it)?.use { stream ->
            stream.write(content.toByteArray())
        }
        Toast.makeText(context, "Сохранено в Downloads", Toast.LENGTH_SHORT).show()
    } ?: run {
        Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
    }
}
