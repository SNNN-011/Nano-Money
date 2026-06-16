package com.example.util

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object GoogleDriveHelper {
    private const val TAG = "GoogleDriveHelper"
    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    data class DriveBackupFile(
        val id: String,
        val name: String,
        val createdTime: String?,
        val sizeBytes: Long
    )

    sealed interface DriveResult<out T> {
        data class Success<out T>(val data: T) : DriveResult<T>
        data class Error(val message: String, val recoverableIntent: android.content.Intent? = null) : DriveResult<Nothing>
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Build the standard Google Sign In Client configured for Google Drive App Folder / Files access.
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Get the last signed-in Google account, if any.
     */
    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Revoke tokens and sign the user out.
     */
    fun signOut(context: Context, onComplete: () -> Unit) {
        val client = getGoogleSignInClient(context)
        client.signOut().addOnCompleteListener {
            client.revokeAccess().addOnCompleteListener {
                onComplete()
            }
        }
    }

    private class UnauthorizedException(message: String) : Exception(message)

    /**
     * Securely fetches the OAuth2 access token for Google Drive calls.
     * Can trigger UserRecoverableAuthException which needs an activity intent.
     */
    suspend fun fetchAccessToken(context: Context, forceRefresh: Boolean = false): DriveResult<String> = withContext(Dispatchers.IO) {
        val account = getSignedInAccount(context) ?: return@withContext DriveResult.Error("Belum terhubung dengan akun Google. Silakan hubungkan akun terlebih dahulu.")
        try {
            // GoogleAuthUtil.getToken requires a raw android.accounts.Account.
            // On newer Android/Play Services versions, account.account can be null as a privacy measure,
            // but account.email is available because we requested 'requestEmail()'.
            val sysAccount = account.account ?: if (!account.email.isNullOrEmpty()) {
                android.accounts.Account(account.email!!, "com.google")
            } else {
                null
            } ?: return@withContext DriveResult.Error("Akun Google tidak valid atau email tidak terakses.")
            
            val scopeString = "oauth2:$DRIVE_SCOPE"
            
            if (forceRefresh) {
                try {
                    val existingToken = GoogleAuthUtil.getToken(context, sysAccount, scopeString)
                    GoogleAuthUtil.invalidateToken(context, existingToken)
                    Log.d(TAG, "Token lama berhasil di-invalidasi karena forceRefresh=true")
                } catch (e: Exception) {
                    Log.w(TAG, "Selesai meng-invalidasi token kadaluarsa: ${e.message}")
                }
            }
            
            val token = GoogleAuthUtil.getToken(context, sysAccount, scopeString)
            DriveResult.Success(token)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "Izin tambahan diperlukan oleh sistem Google: ${recoverable.message}")
            DriveResult.Error("Izin otorisasi tambahan diperlukan.", recoverable.intent)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mendapatkan token OAuth2: ${e.message}", e)
            DriveResult.Error("Gagal otentikasi Google: ${e.localizedMessage ?: "Kesalahan tidak diketahui."}")
        }
    }

    /**
     * Helper to retrieve or create the dedicated "NanoMoney_Backups" folder in the user's Google Drive.
     */
    private fun getOrCreateFolderId(token: String): String? {
        try {
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = 'NanoMoney_Backups' and trashed = false"
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)"
            val request = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.code == 401) {
                throw UnauthorizedException("Token expired")
            }
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "{}"
                val json = JSONObject(responseBody)
                val filesArr = json.optJSONArray("files")
                if (filesArr != null && filesArr.length() > 0) {
                    return filesArr.getJSONObject(0).getString("id")
                }
            } else {
                Log.w(TAG, "Gagal mencari folder NanoMoney_Backups: HTTP ${response.code}")
            }

            // Folder does not exist, let's create it
            val folderMetadata = JSONObject().apply {
                put("name", "NanoMoney_Backups")
                put("mimeType", "application/vnd.google-apps.folder")
            }.toString()

            val createRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(folderMetadata.toRequestBody("application/json".toMediaType()))
                .build()

            val createResponse = httpClient.newCall(createRequest).execute()
            if (createResponse.code == 401) {
                throw UnauthorizedException("Token expired")
            }
            if (createResponse.isSuccessful) {
                val responseBody = createResponse.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBody)
                val newFolderId = jsonResponse.optString("id")
                if (!newFolderId.isNullOrEmpty()) {
                    Log.i(TAG, "Berhasil membuat folder baru di Google Drive: $newFolderId")
                    return newFolderId
                }
            } else {
                Log.e(TAG, "Gagal membuat folder di Google Drive: HTTP ${createResponse.code}")
            }
        } catch (unauthorized: UnauthorizedException) {
            throw unauthorized
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat melacak atau membuat folder di Google Drive", e)
        }
        return null
    }

    /**
     * Checks if a file with the given name already exists in the specifies folder.
     */
    private fun findExistingFileId(token: String, name: String, folderId: String?): String? {
        try {
            val query = if (folderId != null) {
                "name = '$name' and '$folderId' in parents and trashed = false"
            } else {
                "name = '$name' and trashed = false"
            }
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)"
            val request = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.code == 401) {
                throw UnauthorizedException("Token expired")
            }
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "{}"
                val json = JSONObject(responseBody)
                val filesArr = json.optJSONArray("files")
                if (filesArr != null && filesArr.length() > 0) {
                    return filesArr.getJSONObject(0).getString("id")
                }
            }
        } catch (unauthorized: UnauthorizedException) {
            throw unauthorized
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat melacak berkas duplikat di Google Drive", e)
        }
        return null
    }

    private fun uploadBackupToDriveInternal(token: String, backupFile: File, folderId: String?): DriveResult<String> {
        // Check if file already exists with same name to overwrite it
        val existingFileId = findExistingFileId(token, backupFile.name, folderId)
        val fileIdToUpload: String

        if (!existingFileId.isNullOrEmpty()) {
            Log.i(TAG, "Ditemukan berkas cadangan dengan nama yang sama: $existingFileId. Menggunakan mode timpa (overwrite).")
            fileIdToUpload = existingFileId
        } else {
            // Step 1: Create the file metadata in Google Drive inside the directory
            val metadataJson = JSONObject().apply {
                put("name", backupFile.name)
                put("mimeType", "application/x-sqlite3")
                if (!folderId.isNullOrEmpty()) {
                    put("parents", JSONArray().apply { put(folderId) })
                }
            }.toString()

            val metadataRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(metadataJson.toRequestBody("application/json".toMediaType()))
                .build()

            val metadataResponse = httpClient.newCall(metadataRequest).execute()
            if (metadataResponse.code == 401) {
                throw UnauthorizedException("Token expired")
            }
            if (!metadataResponse.isSuccessful) {
                val errorBody = metadataResponse.body?.string() ?: ""
                Log.e(TAG, "Gagal membuat metadata di Drive: Code=${metadataResponse.code}, Body=$errorBody")
                val detailedMsg = extractErrorMessage(metadataResponse.code, errorBody)
                return DriveResult.Error("Gagal membuat metadata berkas Google Drive: $detailedMsg")
            }

            val responseBody = metadataResponse.body?.string() ?: ""
            val jsonResponse = JSONObject(responseBody)
            val createdId = jsonResponse.optString("id")
            if (createdId.isNullOrEmpty()) {
                return DriveResult.Error("Gagal menarik ID berkas dari respons Google Drive.")
            }
            fileIdToUpload = createdId
        }

        // Step 2: Upload the raw SQLite binary data as the file's content (media) via PATCH (overwrites)
        val mediaRequest = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileIdToUpload?uploadType=media")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/octet-stream")
            .patch(backupFile.asRequestBody("application/octet-stream".toMediaType()))
            .build()

        val mediaResponse = httpClient.newCall(mediaRequest).execute()
        if (mediaResponse.code == 401) {
            throw UnauthorizedException("Token expired")
        }
        if (!mediaResponse.isSuccessful) {
            val errorBody = mediaResponse.body?.string() ?: ""
            Log.e(TAG, "Gagal mengunggah konten berkas ke Drive: Code=${mediaResponse.code}, Body=$errorBody")
            // Clean up the empty metadata file ONLY if it was brand-newly created
            if (existingFileId.isNullOrEmpty()) {
                tryDeleteDriveFile(token, fileIdToUpload)
            }
            val detailedMsg = extractErrorMessage(mediaResponse.code, errorBody)
            return DriveResult.Error("Gagal mentransfer data berkas cadangan ke Google Drive: $detailedMsg")
        }

        Log.d(TAG, "Berkas berhasil diunggah/diperbarui ke Google Drive: $fileIdToUpload")
        return DriveResult.Success(fileIdToUpload)
    }

    /**
     * Upload backup database (.db) file to Google Drive.
     */
    suspend fun uploadBackupToDrive(context: Context, backupFile: File): DriveResult<String> = withContext(Dispatchers.IO) {
        if (!backupFile.exists()) {
            return@withContext DriveResult.Error("Berkas cadangan tidak ditemukan lokal.")
        }

        var tokenResult = fetchAccessToken(context)
        if (tokenResult is DriveResult.Error) {
            return@withContext tokenResult
        }
        var token = (tokenResult as DriveResult.Success).data

        try {
            val folderId = getOrCreateFolderId(token)
            uploadBackupToDriveInternal(token, backupFile, folderId)
        } catch (unauthorized: UnauthorizedException) {
            Log.w(TAG, "Koneksi Google Drive unauthorized (token expired). Mencoba menyegarkan token...")
            tokenResult = fetchAccessToken(context, forceRefresh = true)
            if (tokenResult is DriveResult.Error) {
                return@withContext tokenResult
            }
            token = (tokenResult as DriveResult.Success).data
            try {
                val folderId = getOrCreateFolderId(token)
                uploadBackupToDriveInternal(token, backupFile, folderId)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal mengunggah cadangan setelah penyeleksian ulang token: ${e.message}", e)
                DriveResult.Error("Gagal mengunggah berkas ke Google Drive: ${e.localizedMessage ?: "Kesalahan tidak diketahui."}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat mengunggah cadangan ke Google Drive: ${e.message}", e)
            DriveResult.Error("Gagal mengunggah berkas: ${e.localizedMessage ?: "Kesalahan Jaringan."}")
        }
    }

    /**
     * Lists database backup files (.db) that were uploaded from our app on Google Drive.
     */
    private fun listBackupsFromDriveInternal(token: String): List<DriveBackupFile> {
        val folderId = getOrCreateFolderId(token)
        
        // Build a query focusing specifically on our backups folder if found
        val query = if (!folderId.isNullOrEmpty()) {
            "(name contains 'backup_' or name contains 'DataNanoMoney_') and name contains '.db' and '$folderId' in parents and trashed = false"
        } else {
            "(name contains 'backup_' or name contains 'DataNanoMoney_') and name contains '.db' and trashed = false"
        }
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,createdTime,size)&orderBy=createdTime desc"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.code == 401) {
            throw UnauthorizedException("Token expired")
        }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            Log.e(TAG, "Gagal mencantumkan berkas dari Drive: Code=${response.code}, Body=$errorBody")
            val detailedMsg = extractErrorMessage(response.code, errorBody)
            throw Exception("Gagal membaca daftar cadangan dari Google Drive: $detailedMsg")
        }

        val responseBody = response.body?.string() ?: "{}"
        val json = JSONObject(responseBody)
        val jsonArray = json.optJSONArray("files") ?: JSONArray()
        val list = mutableListOf<DriveBackupFile>()
        
        for (i in 0 until jsonArray.length()) {
            val fileObj = jsonArray.getJSONObject(i)
            val id = fileObj.getString("id")
            val name = fileObj.getString("name")
            val createdTime = fileObj.optString("createdTime", null)
            val sizeBytes = fileObj.optLong("size", 0L)
            list.add(DriveBackupFile(id, name, createdTime, sizeBytes))
        }
        return list
    }

    suspend fun listBackupsFromDrive(context: Context): DriveResult<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        var tokenResult = fetchAccessToken(context)
        if (tokenResult is DriveResult.Error) {
            return@withContext tokenResult
        }
        var token = (tokenResult as DriveResult.Success).data

        try {
            DriveResult.Success(listBackupsFromDriveInternal(token))
        } catch (unauthorized: UnauthorizedException) {
            Log.w(TAG, "Daftar Google Drive unauthorized (token expired). Menyegarkan token...")
            tokenResult = fetchAccessToken(context, forceRefresh = true)
            if (tokenResult is DriveResult.Error) {
                return@withContext tokenResult
            }
            token = (tokenResult as DriveResult.Success).data
            try {
                DriveResult.Success(listBackupsFromDriveInternal(token))
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memuat daftar dari Drive setelah penyegaran token: ${e.message}", e)
                DriveResult.Error("Gagal memuat daftar Google Drive: ${e.localizedMessage ?: "Sesi terganggu."}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat mencantumkan berkas Google Drive: ${e.message}", e)
            DriveResult.Error("Gagal memuat daftar Google Drive: ${e.localizedMessage ?: "Kesalahan Jaringan."}")
        }
    }

    /**
     * Download a backup from Google Drive and save it locally on the device (usually inside the downloads or backups folder).
     */
    private fun downloadBackupFromDriveInternal(token: String, fileId: String, targetFile: File) {
        // Ensure target parent directory exists
        targetFile.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }

        // Remove existing target file if any
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.code == 401) {
            throw UnauthorizedException("Token expired")
        }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            Log.e(TAG, "Gagal mengunduh berkas dari Drive: Code=${response.code}, Body=$errorBody")
            val detailedMsg = extractErrorMessage(response.code, errorBody)
            throw Exception("Gagal mengunduh isi berkas dari Google Drive: $detailedMsg")
        }

        val networkStream = response.body?.byteStream() ?: throw Exception("Isi berkas kosong atau tidak dapat diakses.")
        
        FileOutputStream(targetFile).use { fileStream ->
            networkStream.use { input ->
                input.copyTo(fileStream)
            }
        }

        Log.d(TAG, "Berkas dari Google Drive berhasil disimpan lokal: ${targetFile.absolutePath}")
    }

    suspend fun downloadBackupFromDrive(context: Context, fileId: String, targetFile: File): DriveResult<File> = withContext(Dispatchers.IO) {
        var tokenResult = fetchAccessToken(context)
        if (tokenResult is DriveResult.Error) {
            return@withContext tokenResult
        }
        var token = (tokenResult as DriveResult.Success).data

        try {
            downloadBackupFromDriveInternal(token, fileId, targetFile)
            DriveResult.Success(targetFile)
        } catch (unauthorized: UnauthorizedException) {
            Log.w(TAG, "Unduhan Google Drive unauthorized (token expired). Menyegarkan token...")
            tokenResult = fetchAccessToken(context, forceRefresh = true)
            if (tokenResult is DriveResult.Error) {
                return@withContext tokenResult
            }
            token = (tokenResult as DriveResult.Success).data
            try {
                downloadBackupFromDriveInternal(token, fileId, targetFile)
                DriveResult.Success(targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal mengunduh berkas setelah penyegaran token: ${e.message}", e)
                DriveResult.Error("Gagal mengunduh berkas Google Drive: ${e.localizedMessage ?: "Sesi terganggu."}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat mengunduh berkas dari Google Drive: ${e.message}", e)
            DriveResult.Error("Gagal mengunduh berkas: ${e.localizedMessage ?: "Kesalahan Jaringan."}")
        }
    }

    /**
     * Deletes a remote backup file from Google Drive.
     */
    private fun deleteBackupFromDriveInternal(token: String, fileId: String) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.code == 401) {
            throw UnauthorizedException("Token expired")
        }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            Log.e(TAG, "Gagal menghapus berkas di Drive: Code=${response.code}, Body=$errorBody")
            val detailedMsg = extractErrorMessage(response.code, errorBody)
            throw Exception("Gagal menghapus berkas di Google Drive: $detailedMsg")
        }

        Log.d(TAG, "Berkas di Google Drive berhasil dihapus: $fileId")
    }

    suspend fun deleteBackupFromDrive(context: Context, fileId: String): DriveResult<Boolean> = withContext(Dispatchers.IO) {
        var tokenResult = fetchAccessToken(context)
        if (tokenResult is DriveResult.Error) {
            return@withContext tokenResult
        }
        var token = (tokenResult as DriveResult.Success).data

        try {
            deleteBackupFromDriveInternal(token, fileId)
            DriveResult.Success(true)
        } catch (unauthorized: UnauthorizedException) {
            Log.w(TAG, "Penghapusan Google Drive unauthorized (token expired). Menyegarkan token...")
            tokenResult = fetchAccessToken(context, forceRefresh = true)
            if (tokenResult is DriveResult.Error) {
                return@withContext tokenResult
            }
            token = (tokenResult as DriveResult.Success).data
            try {
                deleteBackupFromDriveInternal(token, fileId)
                DriveResult.Success(true)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menghapus berkas setelah penyegaran token: ${e.message}", e)
                DriveResult.Error("Gagal menghapus berkas di Google Drive: ${e.localizedMessage ?: "Sesi terganggu."}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat menghapus berkas Google Drive: ${e.message}", e)
            DriveResult.Error("Gagal menghapus berkas: ${e.localizedMessage ?: "Kesalahan Jaringan."}")
        }
    }

    /**
     * Helper to silently delete a file on Drive if media upload fails.
     */
    private fun tryDeleteDriveFile(token: String, fileId: String) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membersinkan metadata yang terlanjur terbuat: ${e.message}")
        }
    }

    /**
     * Extracts more specific, user-actionable error messages from Google REST JSON replies (e.g. API disabled instructions).
     */
    private fun extractErrorMessage(code: Int, errorBody: String): String {
        if (errorBody.isEmpty()) return "HTTP $code"
        return try {
            val json = JSONObject(errorBody)
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                errorObj.optString("message", "HTTP $code")
            } else {
                json.optString("message", "HTTP $code")
            }
        } catch (e: Exception) {
            if (errorBody.length > 200) errorBody.take(200) + "..." else errorBody
        }
    }
}
