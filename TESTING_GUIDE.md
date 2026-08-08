# 🧪 Buku Panduan Pengujian Manual (Manual Testing Guide) — Nano Money

> **Aplikasi:** Nano Money — Catat Keuangan Semudah Chat Teman  
> **Tujuan:** Panduan langkah-demi-langkah bagi penguji/pengembang untuk memverifikasi seluruh fungsi aplikasi berjalan normal dan bebas bug sebelum dipublikasikan.

---

## 📋 Daftar Isi Skenario Pengujian

1. [Persiapan Perangkat & Pengujian Keamanan Awal](#1-persiapan-perangkat--pengujian-keamanan-awal)
2. [Pengujian Autentikasi & Proteksi PIN](#2-pengujian-autentikasi--proteksi-pin)
3. [Pengujian Transaksi Manual (CRUD & Filter)](#3-pengujian-transaksi-manual-crud--filter)
4. [Pengujian Fitur AI Chat-to-Track & OCR Nota](#4-pengujian-fitur-ai-chat-to-track--ocr-nota)
5. [Pengujian Ekspor & Impor Data (PDF & CSV)](#5-pengujian-ekspor--impor-data-pdf--csv)
6. [Pengujian Backup & Restore Data](#6-pengujian-backup--restore-data)
7. [Pengujian Notifikasi & Pengingat Harian](#7-pengujian-notifikasi--pengingat-harian)
8. [Checklist Akhir Kualitas Aplikasi (Definition of Done)](#8-checklist-akhir-kualitas-aplikasi-definition-of-done)

---

## 1. Persiapan Perangkat & Pengujian Keamanan Awal

### 1.1 Penanganan Lingkungan Execution
- **Skenario A (Device Asli / Non-Root):**
  1. Buka aplikasi Nano Money di HP Android fisik (Android 8.0 / API 26 ke atas).
  2. **Ekspektasi:** Aplikasi terbuka lancar tanpa pesan peringatan keamanan.
- **Skenario B (Emulator pada Release Build):**
  1. Jalankan APK Release (bukan Debug) pada Emulator Android Studio / Genymotion.
  2. **Ekspektasi:** Aplikasi mendeteksi emulator dan menolak berjalan (menampilkan layar peringatan / keluar demi keamanan data keuangan).
- **Skenario C (Device Ter-Root / Magisk):**
  1. Jalankan aplikasi pada perangkat yang memiliki akses Root.
  2. **Ekspektasi:** Aplikasi memblokir akses dan keluar otomatis (`SecurityStatus.ROOTED`).

---

## 2. Pengujian Autentikasi & Proteksi PIN

### 2.1 Membuat & Menguji PIN
1. Masuk ke tab **Pengaturan** -> Aktifkan **Kunci Keamanan PIN**.
2. Masukkan 4 digit PIN baru (contoh: `1234`), lalu konfirmasi PIN.
3. Keluar dari aplikasi (tutup dari menu Recents/App Switcher).
4. Buka kembali Nano Money.
5. **Ekspektasi:** 
   - Layar verifikasi PIN muncul.
   - Menginputkan PIN salah (contoh: `9999`) -> Muncul pesan error "PIN Salah" dan vibrate/animasi menolak.
   - Menginputkan PIN benar (`1234`) -> Masuk ke layar utama secara instan.
6. **Pengecekan Logcat (Post-Fix Verification C-01 & C-02):**
   - Buka `Logcat` di Android Studio saat verifikasi PIN.
   - Pastikan **TIDAK ADA** log yang mencetak digit PIN atau panjang PIN (`pinLen`).

---

## 3. Pengujian Transaksi Manual (CRUD & Filter)

### 3.1 Menambah Transaksi (Create)
1. Pada tab **Beranda / Home**, tekan tombol **+ (Tambah Transaksi)**.
2. Isi formulir:
   - Deskripsi: `Nasi Goreng Spesial`
   - Jumlah: `25.000`
   - Tipe: `Pengeluaran`
   - Kategori: `Makanan`
3. Tekan **Simpan**.
4. **Ekspektasi:** 
   - Transaksi muncul di daftar transaksi terbaru.
   - Saldo total dan pengeluaran bulan ini berkurang/ter-update secara real-time pada *Glow Metric Cards*.

### 3.2 Mengedit & Menghapus Transaksi (Update & Delete)
1. Ketuk transaksi `Nasi Goreng Spesial`.
2. Ubah nominal menjadi `30.000` -> Tekan **Update**.
3. **Ekspektasi:** Nominal di daftar dan total pengeluaran berubah menjadi Rp 30.000.
4. Hapus transaksi tersebut.
5. **Ekspektasi:** Transaksi hilang dari daftar dan saldo kembali ke semula.

### 3.3 Pengujian Anggaran & Filter (Budgets & Filter)
1. Tetapkan batas anggaran kategori **Makanan**: Rp 100.000.
2. Tambahkan transaksi makanan sebesar Rp 90.000.
3. **Ekspektasi:** Indikator progress bar kategori Makanan berubah warna menjadi kuning/merah (*Warning/Critical threshold*).

---

## 4. Pengujian Fitur AI Chat-to-Track & OCR Nota

### 4.1 Chat AI Input Teks
1. Masuk ke tab **AI Chat**.
2. Ketik pesan kasual: `"Kemarin beli bensin 35 ribu"`.
3. Tekan **Kirim**.
4. **Ekspektasi:** 
   - AI merespons ramah.
   - Muncul kartu konfirmasi transaksi terstruktur (Deskripsi: `Bensin`, Nominal: `35.000`, Kategori: `Transportasi`).
   - Tekan **Konfirmasi Simpan** -> Transaksi otomatis tersimpan ke Room Database.

### 4.2 OCR Scanning Struk / Nota Fisik (Post-Fix Verification C-07)
1. Tekan ikon **Kamera / Galeri** di tab AI Chat.
2. **Uji Foto Resolusi Tinggi (12MP - 108MP):** Pilih foto nota fisik berukuran besar.
3. **Ekspektasi (Post-Fix C-07):** 
   - Gambar berhasil diproses tanpa membuat aplikasi *Crash (Out Of Memory / OOM)*.
   - AI berhasil mengekstrak total nominal dan daftar item belanja dari nota.

---

## 5. Pengujian Ekspor & Impor Data (PDF & CSV)

### 5.1 Ekspor Laporan PDF
1. Buka tab **Laporan / Export**.
2. Pilih rentang tanggal (contoh: Bulan Ini) -> Tekan **Ekspor PDF**.
3. Buka file PDF yang terunduh di penyimpanan HP.
4. **Ekspektasi:** File `.pdf` terbuka dengan rapi, berisi header Nano Money, ringkasan total saldo, dan tabel transaksi yang sesuai dengan data di aplikasi.

### 5.2 Impor Data CSV (Post-Fix Verification H-08)
1. Siapkan file `.csv` dengan header: `Deskripsi,Jumlah,Tipe,Kategori,Tanggal`.
2. **Uji File CSV Besar (Pengujian Streaming H-08):** Impor file CSV yang berisi ratusan/ribuan baris data.
3. Buka tab Pengaturan -> Pilih **Impor CSV**.
4. **Ekspektasi:** 
   - Data ter-impor dengan lancar tanpa freeze atau *OutOfMemoryError*.
   - Semua entri transaksi masuk ke database dengan kategori yang benar.

---

## 6. Pengujian Backup & Restore Data

### 6.1 Local Backup & Restore
1. Buka **Pengaturan** -> Tekan **Buat Backup Lokal**.
2. Buat transaksi baru sebagai penanda (`Uji Backup 100rb`).
3. Tekan **Restore Backup Lokal** -> Pilih file backup yang dibuat di langkah 1.
4. **Ekspektasi:** Aplikasi me-restore database. Transaksi penanda (`Uji Backup 100rb`) hilang, dan data kembali ke kondisi saat backup dibuat.

---

## 7. Pengujian Notifikasi & Pengingat Harian

### 7.1 Pengingat Harian (Post-Fix Verification L-09)
1. Masuk ke **Pengaturan** -> Aktifkan **Pengingat Catat Keuangan Harian**.
2. Atur waktu pengingat 2 menit dari waktu sekarang (contoh: jam 20.05).
3. Jalankan pada perangkat **Android 12 / 13 / 14 (API 31+)**.
4. **Ekspektasi (Post-Fix L-09):** 
   - Pengaturan jam pengingat tersimpan tanpa *SecurityException Crash*.
   - Saat waktu mencapai jam yang ditentukan, notifikasi pengingat muncul di panel status bar Android.

---

## 8. Checklist Akhir Kualitas Aplikasi (Definition of Done)

Gunakan tabel checklist ini sebelum rilis APK ke Google Play Store:

| No | Komponen Pengujian | Status | Catatan |
|----|--------------------|--------|---------|
| 1 | Bebas dari crash OOM saat memilih gambar galeri | [ ] PASS | Terverifikasi fix C-07 |
| 2 | Impor file CSV berukuran besar berjalan stabil | [ ] PASS | Terverifikasi fix H-08 |
| 3 | Notifikasi pengingat berfungsi di Android 12+ | [ ] PASS | Terverifikasi fix L-09 |
| 4 | Verifikasi PIN tidak bocor di Logcat | [ ] PASS | Terverifikasi fix C-01 & C-02 |
| 5 | Keystore error handling tidak timpa passphrase diam-diam | [ ] PASS | Terverifikasi fix C-03 |
| 6 | Rotasi layar HP tidak membuat state hilang/reset | [ ] PASS | Tested Compose State |
| 7 | Transaksi CRUD (Tambah, Edit, Hapus) presisi | [ ] PASS | Room DB verified |
| 8 | AI Chat Gemini & OCR Nota membalas tepat | [ ] PASS | Cloudflare Proxy verified |
| 9 | Laporan PDF ter-generate dan bisa dibuka | [ ] PASS | Android Native Canvas |

---
*Buku panduan ini diperbarui secara berkala sesuai dengan evolusi versi Nano Money.* 💸🚀
