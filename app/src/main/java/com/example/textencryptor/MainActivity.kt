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
    var resultText by remember { mutableStateOf("") }
    var isEncrypted by remember { mutableStateOf(false) } // Флаг: что сейчас в результате
    
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
                label = { Text("Исходная фраза / Шифр") },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (originalText.isNotBlank() && keyText.isNotBlank()) {
                            resultText = encryptText(originalText, keyText)
                            isEncrypted = true
                        } else {
                            Toast.makeText(context, "Заполните оба поля", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Зашифровать")
                }

                Button(
                    onClick = {
                        if (originalText.isNotBlank() && keyText.isNotBlank()) {
                            val decrypted = decryptText(originalText, keyText)
                            if (decrypted != null) {
                                resultText = decrypted
                                isEncrypted = false
                            } else {
                                Toast.makeText(context, "Ошибка дешифровки. Неверный ключ или поврежденные данные.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Заполните оба поля", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Дешифровать")
                }
            }

            if (resultText.isNotEmpty()) {
                Text(
                    text = if (isEncrypted) "Зашифрованный текст (Base64):" else "Расшифрованный текст:",
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = resultText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Button(
                    onClick = { saveToFile(context, resultText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сохранить в *.txt")
                }
            }
        }
    }
}

// --- КРИПТОГРАФИЯ ---

fun encryptText(text: String, password: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val keyBytes = password.toByteArray().copyOf(16) 
    val secretKey = SecretKeySpec(keyBytes, "AES")
    
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
    
    val encrypted = cipher.doFinal(text.toByteArray())
    val combined = salt + iv + encrypted
    return Base64.getEncoder().encodeToString(combined)
}

fun decryptText(encryptedBase64: String, password: String): String? {
    return try {
        val combined = Base64.getDecoder().decode(encryptedBase64)
        // Извлекаем IV (байты с 16 по 28)
        val iv = combined.copyOfRange(16, 28)
        // Извлекаем сам шифротекст (все байты после IV)
        val cipherText = combined.copyOfRange(28, combined.size)
        
        val keyBytes = password.toByteArray().copyOf(16)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        
        val decrypted = cipher.doFinal(cipherText)
        String(decrypted)
    } catch (e: Exception) {
        // Если ключ неверный или текст поврежден, вернется null
        null
    }
}

// --- СОХРАНЕНИЕ ---

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
