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
* **Keamanan URL Endpoint & Multi-Model**: Mendukung peralihan model AI secara aman (seperti Gemma 2B-it dan Gemini Flash Lite) tanpa membocorkan infrastruktur serverless utama Anda.

### 📈 2. Real-Time Dashboard & Batas Anggaran (Budgets)
* **Glow Metric Cards**: Panel informasi dinamis yang memetakan akumulasi saldo, total pendapatan, dan total pengeluaran secara akurat dalam rupiah.
* **Batas Anggaran Bulanan (Monthly Limit)**: Menetapkan batas atas total pengeluaran Anda.
* **Anggaran Khusus Kategori (Category Limits)**: Mengontrol pengeluaran di bawah sektor atau kategori spesifik (Makanan, Belanja, Hiburan, dll.) lengkap dengan indikator grafis berupa persentase pemakaian dan pemberitahuan batas kritis.

### 📄 3. Ekspor Laporan PDF Profesional
* **Android Native Canvas Generator**: Menghasilkan dokumen laporan fisik berformat `.pdf` berkualitas tinggi tanpa ketergantungan library pihak ketiga yang besar.
* **Sinkronisasi Filter Aktif**: Data finansial yang diekspor disesuaikan secara dinamas dengan filter aktif pada UI (pilihan rentang tanggal atau filter kategori khusus) guna menjamin kesesuaian dokumen yang diunduh.

### 💾 4. Sistem Backup & Restaurasi Lokal Pintar
* **Local First Architecture**: Integritas data terjamin sepenuhnya di dalam penyimpanan lokal Room Database SQLite terenkripsi.
* **Sistem Pencadangan (Backup System)**: Mengekspor file database internal serta file preferensi (*SharedPreferences*) secara aman ke media luar.
* **Kompatibilitas Mundur & WAL Checkpointing**: Saat memproses pemulihan cadangan (*restore*), aplikasi melakukan checkpointing terhadap skema Room (WAL flush) untuk mencegah korupsi atau inkonsistensi struktur database di masa mendatang.

### 🛡️ 5. Proteksi Keamanan Berlapis (Security Compliance)
* **Root & Emulator Detection**: Mencegah jalannya aplikasi pada perangkat yang telah di-root atau lingkungan emulator tidak aman untuk menghindari eksploitasi data finansial.
* **API Protection & Request Signing**: Setiap lalu-lintas request menuju peladen proxy Gemini disertakan dengan tanda tangan kriptografi tepercaya (*X-Worker-Secret*) yang terenkripsi dan diverifikasi di sisi serverless edge secara dinamis.
* **Dynamic Lock Screen System**: Proteksi ganda berupa sandi PIN 4-digit khusus serta integrasi penuh dengan sensor Android Biometric (Sidik Jari / Pengenal Wajah) untuk membuka aplikasi secara instan dan tepercaya.
* **Screen Capture Prevention (Remote Config Managed)**: Melarang sistem Android melakukan tangkapan layar (*screenshot*) atau perekaman layar guna menghindari kebocoran data finansial sensitif. Fitur pencegahan ini dapat dinyalakan atau dimatikan (*ON/OFF*) secara dinamis dari jauh melalui parameter `"prevent_screenshot"` di Firebase Remote Config.

---

## 🛠️ Arsitektur & Tech Stack

Nano Money diimplementasikan menggunakan arsitektur modular yang direkomendasikan Google (**MVVM + Clean Architecture + State-Driven UI**):

| Komponen | Teknologi | Keterangan |
| :--- | :--- | :--- |
| **Pondasi Bahasa** | Kotlin (100%) | Null-safe, ekspresif, dan performa tinggi dengan Coroutines & Flow. |
| **Arsitektur UI** | Jetpack Compose | Deklaratif toolkit modern berbasis Material Design 3. |
| **Navigation** | Kotlinx Serialization | Navigasi aman berbasis objek tipe data asli (`@Serializable`). |
| **Data Engine** | Room SQL Database | Local storage modular tepercaya dengan validasi query di waktu compile. |
| **API Client** | Retrofit 2 & OkHttp3 | Pengiriman data terstruktur dengan interceptor header dinamis. |
| **Asynchronous** | StateFlow & SharedFlow | Mekanisme transmisi status reaktif antara UI dan ViewModel. |

```
📁 app/src/main/java/com/example/
├── data/           # Repositori, Sumber Data, Model Room & Cache Database
├── ui/             # Jetpack Compose Screens, Tabs, ViewModels, Komponen UI & Dialogs
└── util/           # Utilitas Keamanan (Root/Emulator Check), Formatter, & Helper Backup
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
* JDK 17 atau JDK 21
* SDK Platform Android Terinstal (Min API Level 26 / Android 8.0 Oreo)

### Langkah Langkah Pengoperasian
1. **Clone Repositori**:
   ```bash
   git clone https://github.com/username/nano-money.git
   cd nano-money
   ```
2. **Sinkronisasi Modul**:
   Buka direktori proyek Anda menggunakan editor Android Studio Anda, biarkan Gradle melakukan pengunduhan dependensi.
3. **Konfigurasi Kredensial Environment**:
   Pastikan Anda menyalin file `.env.example` menjadi file `.env` di root proyek Anda, dan atur variabel kredensial (seperti `WORKER_SECRET_KEY`) melalui menu Secrets panel yang disediakan.
4. **Build & Run**:
   Hubungkan ponsel cerdas Android Anda atau aktifkan Emulator bawaan Studio, tekan tombol **Run (Shift + F10)** untuk mendeploy aplikasi Nano Money Anda secara instan!

---

## 🤝 Kontribusi & Dukungan

Laporan permasalahan (*bug reports*) dan saran perbaikan fitur dipersilakan melingkupi:
* Pengayaan representasi grafik analisis visual lainnya pada tab Analisis.
* Peningkatan performa render Canvas PDF untuk tabel bernominal baris ganda.
* Pengoptimalan pola translasi asisten chat AI multibahasa.

*Didesain dengan kepatuhan kode ketat dan visual bernilai seni tinggi demi ketenangan perencanaan finansial Anda sehari-hari.* 💸🚀
