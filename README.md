# 💸 Nano Money — Catat Keuangan Semudah Chat Teman

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white&style=for-the-badge)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI_Framework-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white&style=for-the-badge)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Database-Room_SQLite-005C84?logo=sqlite&logoColor=white&style=for-the-badge)](https://developer.android.com/training/data-storage/room)

**Nano Money** adalah asisten pencatat keuangan pintar berbasis Android yang dirancang dengan antarmuka modern, intuitif, dan responsif menggunakan **Jetpack Compose** dan **Material Design 3**. 

Aplikasi ini mendisrupsi cara pencatatan keuangan manual konvensional dengan menghadirkan fitur **AI Chat-to-Track**—memungkinkan pengguna mencatat setiap pemasukan dan pengeluaran secara kasual seolah mengirimkan pesan teks kepada asisten pribadi atau melakukan pemotretan struk belanja fisik secara presisi!

---

## 🌌 Tema Visual: Midnight Abyss
Nano Money mengusung tema visual **Midnight Abyss** (Dark Theme) yang elegan, dirancang khusus untuk kenyamanan mata pengguna:
* **Slate Canvas Background**: Latar belakang pekat mengurangi emisi cahaya biru dan konsumsi energi baterai ponsel.
* **Ambient Radial Glows**: Penerapan degradasi warna pendar cahaya (*ambient glow*) menggunakan warna **Steel Blue** (Utama) dan **Neon Violet** (Aksen) untuk menonjolkan visualisasi metrik data finansial penting.
* **Dynamic Animations**: Sentuhan mikro-interaksi responsif dan visual riak halus (*Material Ripples*) saat komponen berinteraksi.

---

## ✨ Fitur Produksi Utama

### 💬 1. AI Chat-to-Track & OCR Nota Pintar
* **Asisten Chat Interaktif**: Berbincang secara ramah dengan model Gemini. AI dapat mengurai percakapan bebas seperti *"kemarin beli bensin 30 ribu"* menjadi entri data terstruktur secara otomatis.
* **Kemampuan Multimodal OCR**: Unggah atau potret struk fisik Anda. AI melakukan pemindaian semantik (*Optical Character Recognition*) untuk mendeteksi item belanja dan nominal akhir secara instan.
* **Keamanan URL Endpoint & Multi-Model**: Mendukung peralihan model AI secara aman (seperti `gemma-4-31b-it` dan `gemini-3.5-flash-lite`) tanpa membocorkan infrastruktur serverless utama Anda.

### 📈 2. Real-Time Dashboard & Batas Anggaran (Budgets)
* **Glow Metric Cards**: Panel informasi dinamis yang memetakan akumulasi saldo, total pendapatan, dan total pengeluaran secara akurat dalam rupiah.
* **Sistem Rollover Saldo Akumulatif (Balance Carryover)**: Saldo total dihitung secara kumulatif dari seluruh transaksi historis hingga akhir bulan terpilih, sehingga saldo dari bulan sebelumnya otomatis terbawa (rollover) ketika berpindah ke bulan baru.
* **Batas Anggaran Bulanan (Monthly Limit)**: Menetapkan batas atas total pengeluaran Anda.
* **Anggaran Khusus Kategori (Category Limits)**: Mengontrol pengeluaran di bawah sektor atau kategori spesifik (Makanan, Belanja, Hiburan, dll.) lengkap dengan indikator grafis berupa persentase pemakaian dan pemberitahuan batas kritis.

### 📄 3. Ekspor Laporan PDF Profesional
* **Android Native Canvas Generator**: Menghasilkan dokumen laporan fisik berformat `.pdf` berkualitas tinggi tanpa ketergantungan library pihak ketiga yang besar.
* **Sinkronisasi Filter Aktif**: Data finansial yang diekspor disesuaikan secara dinamas dengan filter aktif pada UI (pilihan rentang tanggal atau filter kategori khusus) guna menjamin kesesuaian dokumen yang diunduh.

### 💾 4. Sampah (Arsip Transaksi Terhapus)
* **Soft Delete**: Transaksi yang dihapus tidak benar-benar hilang. Database hanya menandai `isDeleted = 1`, sehingga data masih utuh dan bisa dikembalikan kapan saja.
* **Tab Sampah**: Berada di dalam tab **Simpan**. Berisi seluruh transaksi yang pernah dihapus, lengkap dengan tombol **PULIHKAN** per item maupun **PULIHKAN SEMUA**.
* **Kosongkan Sampah**: Penghapusan permanen tersedia dengan dialog konfirmasi karena tidak bisa dibatalkan.
* **Sinkron ke Cloud**: Setiap transaksi yang dipulihkan langsung diunggah ulang ke Firestore dengan `isDeleted = false` agar tidak terhapus kembali oleh sinkronisasi.

### ☁️ 5. Sinkronisasi Cloud & Pencadangan
* **Sinkronisasi Realtime (Firestore)**: Data tersinkron antar perangkat yang terhubung ke akun Google yang sama. Saat terjadi konflik dengan id yang sama, versi dari cloud menjadi acuan.
* **Export / Import Excel & PDF**: Ekspor data ke berkas `.csv` dan laporan `.pdf`, serta impor kembali dari berkas `.csv`.
* **Backup Database Lokal (fitur tersembunyi)**: UI pencadangan ke penyimpanan internal dinonaktifkan lewat flag `SHOW_LOCAL_BACKUP_UI` di `DatabaseBackupSection.kt`. Kode dan parameternya tetap utuh; cukup ubah flag tersebut menjadi `true` untuk menghidupkan kembali.
* **Snapshot Konsisten (VACUUM INTO)**: Database berjalan pada journal mode WAL, di mana transaksi terbaru bisa berada di file `-wal` dan tidak ikut ter-copy. Cadangan memakai `VACUUM INTO` agar menghasilkan snapshot lengkap tanpa harus menutup database.

### 🛡️ 6. Proteksi Keamanan Berlapis (Security Compliance)
* **Root & Emulator Detection**: Mencegah jalannya aplikasi pada perangkat yang telah di-root atau lingkungan emulator tidak aman untuk menghindari eksploitasi data finansial.
* **API Protection & Request Signing**: Setiap lalu-lintas request menuju peladen proxy Gemini disertakan dengan tanda tangan kriptografi tepercaya (*X-Worker-Secret*) yang terenkripsi dan diverifikasi di sisi serverless edge secara dinamis.
* **Screen Capture Prevention (Remote Config Managed)**: Melarang sistem Android melakukan tangkapan layar (*screenshot*) atau perekaman layar guna menghindari kebocoran data finansial sensitif. Fitur pencegahan ini dapat dinyalakan atau dimatikan (*ON/OFF*) secara dinamis dari jauh melalui parameter `"prevent_screenshot"` di Firebase Remote Config.

---

## 🛠️ Arsitektur & Tech Stack

Nano Money diimplementasikan menggunakan arsitektur modular yang direkomendasikan Google (**MVVM + Clean Architecture + State-Driven UI**):

| Komponen | Teknologi | Keterangan |
| :--- | :--- | :--- |
| **Pondasi Bahasa** | Kotlin (100%) | Null-safe, ekspresif, dan performa tinggi dengan Coroutines & Flow. |
| **Arsitektur UI** | Jetpack Compose | Deklaratif toolkit modern berbasis Material Design 3. |
| **Navigation** | Kotlinx Serialization | Navigasi aman berbasis objek tipe data asli (`@Serializable`). |
| **Data Engine** | Room + SQLCipher | Room untuk storage modular dengan validasi query waktu compile, SQLCipher untuk enkripsi database. |
| **API Client** | Retrofit 2 & OkHttp3 | Pengiriman data terstruktur dengan interceptor header dinamis. |
| **Cloud Sync** | Firebase Auth + Firestore | Sinkronisasi antar perangkat dan proteksi biometrik. |
| **Asynchronous** | StateFlow & SharedFlow | Mekanisme transmisi status reaktif antara UI dan ViewModel. |
| **Background Work** | WorkManager | Menjadwalkan sinkronisasi dan pencadangan terjadwal. |

```
📁 app/src/main/java/com/example/
├── data/           # Room Entity/DAO/Database, Repositori, model & konfigurasi AI
├── ui/             # Jetpack Compose per-tab: common, home, transaction, calendar,
│                   # analysis, chat, export, config, theme
├── util/           # BackupHelper, FirebaseSyncHelper, GoogleDriveHelper, format,
│                   # security check (root/emulator), worker terjadwal
└── worker/         # SyncWorker untuk sinkronisasi terjadwal
```

---

## 🔒 Proteksi API Key via Secure Cloudflare Workers Proxy

Untuk menjaga kredensial orisinal API Key Google Gemini tetap aman dari bahaya dekompilasi aplikasi Android, Nano Money mendelegasikan proses pengiriman parameter otorisasi ke edge server melalui **Cloudflare Workers Proxy**:

```
[ Aplikasi Android ] 📱 ──(Kirim Metadata + X-Worker-Secret)──► [ Cloudflare Workers Proxy ] ☁️
                                                                          │
                                                                 (Sematkan API Key Asli)
                                                                          ▼
[ Google Gemini API Server ] 🤖 ◄─────────────────────────────────────────┘
```

Mekanisme pertahanan:
1. Kunci asli `GEMINI_API_KEY` disimpan sebagai variabel rahasia (*Environment Secret*) di dashboard awan Cloudflare.
2. Aplikasi client menghitung SHA-256 dan menyertakan header keamanan khusus `X-Worker-Secret` yang diekstrak menggunakan fungsionalitas kunci Android Keystore internal aman.
3. Workers di Cloudflare memvalidasi kecocokan tanda tangan data sebelum meneruskan instruksi menuju server Google.

---

## 🚀 Instalasi & Konfigurasi

### Kebutuhan Sistem
* Android Studio Jellyfish (atau versi yang lebih baru)
* JDK 17 atau JDK 21 (build memakai Android Studio JBR)
* Gradle dengan `compileSdk 36`, `targetSdk 36`, `minSdk 26` (Android 8.0 Oreo)
* Source/target compatibility: Java 11
* `applicationId`: `com.aistudio.pencatatkeuangan.nnomny` · `versionName`: 1.0

### Langkah Langkah Pengoperasian
1. **Clone Repositori**:
   ```bash
   git clone https://github.com/SNNN-011/Nano-Money.git
   cd Nano-Money
   ```
2. **Sinkronisasi Modul**:
   Buka direktori proyek Anda menggunakan editor Android Studio Anda, biarkan Gradle melakukan pengunduhan dependensi.
3. **Konfigurasi Kredensial Environment**:
   Salin file `.env.example` menjadi `.env` di root proyek, lalu atur `GEMINI_BASE_URL` dan `GEMINI_API_KEY` di dalamnya.
4. **Pasang `google-services.json`**:
   File ini **tidak** ikut tersimpan di repositori karena berisi kredensial. Letakkan di `app/google-services.json` sebelum membangun. Tanpa file ini, build gagal pada task `:app:processDebugGoogleServices`. Struktur minimumnya:
   ```json
   {
     "project_info": {
       "project_number": "NOMOR_PROYEK_ANDA",
       "project_id": "ID_PROYEK_ANDA",
       "storage_bucket": "ID_PROYEK_ANDA.firebasestorage.app"
     },
     "client": [
       {
         "client_info": {
           "mobilesdk_app_id": "1:000000000000:android:xxxxxxxx",
           "android_client_info": { "package_name": "com.aistudio.pencatatkeuangan.nnomny" }
         },
         "api_key": [ { "current_key": "API_KEY_ANDA" } ]
       }
     ],
     "configuration_version": "1"
   }
   ```
   Pasang juga `app/src/debug/google-services.json` bila ingin menimpa konfigurasi khusus untuk build debug.
5. **Build & Run**:
   Hubungkan ponsel Android Anda atau aktifkan Emulator bawaan Studio, tekan **Run (Shift + F10)** untuk mendeploy aplikasi.

---

## 🤝 Kontribusi & Dukungan

Laporan permasalahan (*bug reports*) dan saran perbaikan fitur dipersilakan melingkupi:
* Pengayaan representasi grafik analisis visual lainnya pada tab Analisis.
* Peningkatan performa render Canvas PDF untuk tabel bernominal baris ganda.
* Pengoptimalan pola translasi asisten chat AI multibahasa.

*Didesain dengan kepatuhan kode ketat dan visual bernilai seni tinggi demi ketenangan perencanaan finansial Anda sehari-hari.* 💸🚀
