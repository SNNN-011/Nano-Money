# 💸 Nano Money — Mencatat Keuangan Semudah Chat Teman

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white&style=for-the-badge)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI_Framework-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white&style=for-the-badge)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Database-Room_SQLite-005C84?logo=sqlite&logoColor=white&style=for-the-badge)](https://developer.android.com/training/data-storage/room)

**Nano Money** adalah aplikasi asisten pencatat keuangan pintar berbasis Android yang dirancang dengan antarmuka yang sangat modern, intuitif, dan responsif menggunakan **Jetpack Compose** dan **Material Design 3**. 

Aplikasi ini mendisrupsi cara pencatatan keuangan manual yang membosankan dengan menghadirkan fitur **AI Chat-to-Track**—memungkinkan Anda untuk mencatat pengeluaran atau pemasukan hanya dengan mengetik obrolan kasual layaknya berbicara dengan teman atau dengan mengambil foto struk cetak secara langsung!

---

## 🌌 Desain Antarmuka: Midnight Abyss (Dark Theme)
Nano Money mengusung tema visual **Midnight Abyss**, yang menggabungkan kegelapan pekat yang nyaman di mata dengan pendaran cahaya atmosferik (*atmospheric radial glows*) berwarna **Steel Blue** dan **Neon Violet**. Kombinasi ini menghadirkan estetika premium, elegan, dan profesional yang menonjolkan visualisasi data tanpa membuat mata lelah.

---

## ✨ Fitur-Fitur Unggulan

### 💬 1. Obrolan Pintar AI (Chat-to-Track) & OCR Nota/Struk
* **Input Teks Kasual**: Cukup ketik *"makan bakso 25 ribu siang ini"* atau *"gaji bulanan masuk 5 juta"*, AI akan secara otomatis mengenali nominal, jenis transaksi (pengeluaran/pemasukan), kategori (Makanan, Gaji, dll.), deskripsi, serta waktu transaksi.
* **Scan Nota / Struk**: Potret struk belanjaan Anda langsung dari kamera. AI akan melakukan pembacaan presisi (*OCR & Semantic Processing*) untuk mengekstrak nominal akhir tanpa harus memasukkan angka satu per satu secara manual.
* **Klarifikasi Interaktif**: Jika AI merasa instruksi kurang jelas, asisten chat akan menanyakan klarifikasi secara ramah sebelum memasukkan data ke database Anda.

### 📊 2. Dashboard Real-Time & Analisis Keuangan Mendalam
* **Glow Metric Cards**: Ringkasan pengeluaran, pemasukan, dan saldo bersih dalam card modern berarsitektur visual bercahaya.
* **Grafik Polarisasi Pengeluaran**: Distribusi pengeluaran per kategori yang direpresentasikan dengan persentase dan ikon dinamis.
* **Visualisasi Riwayat**: Grafik harian dan mingguan interaktif untuk mendeteksi tren gaya hidup keuangan Anda.

### 🛡️ 3. Keamanan Tingkat Tinggi (Lock Screen)
* **PIN 4 Digit**: Amankan data finansial Anda dari tangan-tangan jahat.
* **Sensor Biometrik**: Integrasi penuh dengan sistem sidik jari (*fingerprint*) atau pengenalan wajah (*face unlock*) bawaan ponsel Android Anda untuk membuka aplikasi secara instan dan aman.

### 💾 4. Privasi Data & Penyimpanan Lokal (Local First)
* Seluruh data transaksi, data berulang, dan riwayat obrolan disimpan 100% secara lokal menggunakan **SQLite Room Database**.
* Mengurangi latensi secara signifikan, menghemat kuota internet Anda, dan memastikan kerahasiaan riwayat finansial Anda terjaga seutuhnya.

### 🔄 5. Ekspor-Impor CSV & Cloud Sync
* **Ekspor/Impor CSV**: Memindahkan data Anda secara mudah ke software spreadsheet eksternal (Ms. Excel, Google Sheets).
* **Backup Google Drive**: Sinkronisasikan database terenkripsi Anda ke akun Google Drive pribadi Anda untuk kemudahan migrasi antar perangkat.

### ⏰ 6. Notifikasi Pengingat & Transaksi Berulang
* **Jadwal Pengingat**: Dapat dipersonalisasi untuk mengirim pengingat harian pada jam tertentu agar Anda tetap konsisten mencatat keuangan.
* **Sistem Transaksi Otomatis (Recurring)**: Mendukung pencatatan berulang harian, mingguan, bulanan, atau tahunan untuk kebutuhan pengeluaran wajib (listrik, paket internet, sewa kos) atau pendapatan rutin.

---

## 🛠️ Arsitektur & Tech Stack

Aplikasi ini dibangun menggunakan arsitektur modern Android yang direkomendasikan oleh Google (**MVVM + Clean UI Pattern**):

| Komponen | Teknologi | Keterangan |
| :--- | :--- | :--- |
| **Bahasa Utama** | Kotlin (100%) | Modern, aman, expresif, dan null-safe. |
| **Arsitektur UI** | Jetpack Compose | Deklaratif UI toolkit, memangkas boilerplate XML. |
| **Navigation** | Type-Safe Navigation | Navigasi aman berbasis `@Serializable` Kotlin Serialization. |
| **Sandi & Keamanan**| Biometric & SharedPreferences | Autentikasi biometrik standar industri + SharedPrefs terenkripsi. |
| **Database** | Room SQLite | Manajemen penyimpanan lokal yang cepat, aman, dan modular. |
| **Background Task**| WorkManager & AlarmManager | Untuk sinkronisasi cadangan background & triggers notifikasi waktu. |
| **Library HTTP** | Retrofit 2 & OkHttp3 | Konektivitas RESTful API berkinerja tinggi. |
| **Keamanan API Key**| Cloudflare Workers Proxy | Menghindari hardcoding API Key di file `.apk` dengan memindahkannya ke Edge Server. |

---

## 🔒 Arsitektur Proxy API Key dengan Cloudflare Workers

Agar **API Key Gemini** tidak bocor saat aplikasi dicompile menjadi APK dan didistribusikan ke ponsel lain, Nano Money menggunakan skema **Secure Serverless Proxy** menggunakan **Cloudflare Workers**.

Alur kerja autentikasi:
```
[ Aplikasi Android ] 📱 
       │
       │ (Request Tanpa API Key Asli)
       ▼
[ Cloudflare Workers Proxy ] ☁️ (Menyimpan API Key Gemini dengan aman di Environment Secrets)
       │
       │ (Menyisipkan parameter "key=GEMINI_API_KEY" di Edge Server)
       ▼
[ Google Gemini API Server ] 🤖
```

### 📄 Kode Cloudflare Workers yang Digunakan:
```javascript
export default {
  async fetch(request, env) {
    // API Key diambil secara aman dari cloud/env variable di dashboard Cloudflare
    const GEMINI_API_KEY = env.GEMINI_API_KEY;

    // Mendapatkan request dari aplikasi Android Anda
    const url = new URL(request.url);
    const targetUrl = new URL(`https://generativelanguage.googleapis.com${url.pathname}${url.search}`);
    
    // Menyisipkan API Key secara aman sebagai query parameter di balik layar
    targetUrl.searchParams.set("key", GEMINI_API_KEY);

    // Membuat request tujuan baru menuju server resmi Google Gemini
    const newRequest = new Request(targetUrl.toString(), new Request(request));

    // Kirim request ke Google dan kembalikan respon ke aplikasi Android
    return fetch(newRequest);
  }
}
```

Hal ini membuat aplikasi kita **tetap aman 100%** meskipun di-decompiled, karena kunci akses didelegasikan sepenuhnya di sisi peladen Cloudflare! Fitur **Toggle Switch Model** (Gemma 2b-it/Gemini-3.1-flash-lite) di dalam menu Chat tetap berfungsi penuh karena URL model diteruskan secara dinamis melalui path request.

---

## 🚀 Cara Menjalankan Project

### Prasyarat
* Android Studio Jellyfish (atau versi yang lebih baru)
* JDK 17 atau lebih tinggi
* SDK Android Terinstal (Min SDK API 26+)

### Langkah-langkah
1. **Clone repositori ini**:
   ```bash
   git clone https://github.com/username/nano-money.git
   cd nano-money
   ```
2. **Buka di Android Studio**:
   Buka direktori proyek Anda, tunggu Gradle melakukan sinkronisasi modul.
3. **Konfigurasi Lingkungan (Opsional jika ingin bypass proxy)**:
   Aplikasi secara bawaan didesain untuk langsung menggunakan router proxy Cloudflare yang sudah di-deploy. Jika Anda ingin menggunakan API Key lokal Anda sendiri melalui SDK bawaan, Anda dapat menambahkannya ke panel Secrets Android Studio atau meletakkannya pada properti `local.properties` berkas proyek.
4. **Build & Run**:
   Klik tombol hijau **Run** (`Shift + F10`) di Android Studio untuk menyiapkannya ke Emulator atau Perangkat Android fisik Anda!

---

## 🤝 Kontribusi
Kontribusi selalu terbuka! Silakan lakukan fork pada repositori ini, ajukan issue jika Anda menemukan bug, atau kirimkan Pull Request (PR) terbaik Anda untuk menambahkan visualisasi diagram atau analisis pintar baru lainnya.

---
*Dibuat dengan dedikasi penuh untuk menyederhanakan laporan finansial Anda secara aman, cepat, dan interaktif.* 💡🚀
