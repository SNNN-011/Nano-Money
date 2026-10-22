# Catatan AI Lokal (Offline) — Nano Money

> **Cakupan:** ringkasan kerja otak AI lokal di branch `feat/ai-lokal-offline`
> (18 commit, `bb42186..0d0b1ae`) yang sudah masuk `main`, plus hasil uji di HP.
>
> **Tujuan dokumen:** supaya riwayat keputusan dan angka ujinya bisa dibaca ulang
> tanpa menelusuri commit satu per satu.

---

## 1. Apa yang berubah

Sebelumnya chat-to-track memanggil model Gemini di cloud. Sekarang aplikasi punya
**otak teks lokal** yang berjalan di HP tanpa internet:

- Model dimuat dari aset APK (`.cact`), dijalankan lewat jembatan JNI `libneedle_jni.so`.
- Mesinnya satu untuk seluruh proses dan **tidak thread-safe**, jadi semua panggilan
  diserialkan di `NeedleLocalEngine` (Mutex).
- Prefix prompt sengaja hanya baris tanggal (`date: 2026-10-19 Mon 05:30`), persis
  bentuk saat melatih model. Prompt besar ala Gemini tidak dipakai karena menurunkan akurasi.
- Daftar kategori aktif milik user **dikirim** ke model (lewat skema tool `enum`).
  Tanpa daftar ini, kategori benar anjlok ke 0,7–4% karena model menjawab dari ingatan.

Model yang dipakai: **r10_cat_a** (`rank64.cact`, 63 MB, md5 `90c128e4187ab114b5f65609a179e7af`),
hasil fine-tune dari model dasar **Needle** (Cactus Compute).

## 2. Lapisan aturan di atas model

Model tetap sama; mutu dinaikkan lewat lapisan aplikasi yang deterministik dan bisa diuji
di JVM tanpa mesin Android. Urutan bukti saat menentukan kategori:

1. Nama kategori kustom milik user yang disebut di kalimat.
2. Kata kunci bawaan (`CategoryHints`), mis. `whiskas` → Kucing, `penjualan` → Jualan Online.
3. Sasaran cadangan (`FALLBACKS`), mis. kata kesehatan tanpa kategori Kesehatan → Belanja.
4. Kategori dari model.
5. Penampung `Lainnya` milik user.

Arah uang (masuk/keluar) diputuskan dari kata kerja dan penanda di kalimat user
(`TransactionCorrector`), bukan dari tebakan model — kalimat user menang, kecuali
kalimatnya menyebut nama kategori arah lawan.

## 3. Berkas besar yang TIDAK ikut git

Sengaja di-`.gitignore` karena ukurannya besar dan bukan sumber:

| Berkas | Ukuran | Keterangan |
|---|---|---|
| `app/src/main/assets/*.cact` | 63 MB | bobot model hasil training |
| `app/src/main/jniLibs/**/*.so` | 2,6 MB | jembatan JNI (`libneedle_jni.so`) |

Keduanya **harus ada di mesin** sebelum `assembleRelease`. Cara mendapatkannya ada di
`PASANG-LOKAL.md` (catatan setup, tidak ikut repo).

## 4. Riwayat commit

| Commit | Tanggal | Isi |
|---|---|---|
| `b88e9f3` | 2026-10-19 | koreksi jenis transaksi dari kategori user dan kalimat asli |
| `8210cf7` | 2026-10-19 | otak AI lokal offline: pilih otak, model rank64 jalan di HP |
| `23d0770` | 2026-10-19 | uji 30 kalimat otak lokal lewat jalur aplikasi di HP |
| `970f20e` | 2026-10-19 | kartu konfirmasi memakai kategori dan jenis yang sama dengan penyimpanan |
| `a2b6b91` | 2026-10-19 | tanpa panggilan model, tanya dulu daripada bilang bukan transaksi |
| `c4ea40a` | 2026-10-19 | baca angka telanjang dengan penjaga, dan kenali nominal banyak |
| `a1a29f9` | 2026-10-19 | kartu konfirmasi memperingatkan kalimat yang memuat beberapa nominal |
| `9dbdde8` | 2026-10-20 | kirim daftar kategori user ke otak lokal |
| `2205877` | 2026-10-20 | kategori tidak lagi asal Makanan, deskripsi tidak kosong |
| `8301b72` | 2026-10-20 | arah uang ikut kalimat user, item & kategori tidak lagi asal tebak |
| `43495f8` | 2026-10-20 | pulihkan data lama saat pindah ke aplikasi berbasis otak lokal |
| `0ffb672` | 2026-10-20 | kembali ke id paket `nnomny` agar login Google berfungsi |
| `56928ad` | 2026-10-20 | betulkan arah bunga tabungan, kata duit, servis, dan jajan |
| `c0d40a8` | 2026-10-20 | merge dari `origin/main` |
| `47e69dd` | 2026-10-21 | merge dari `origin/main` |
| `bb2a704` | 2026-10-22 | dahulukan kategori kustom user sebelum kata kunci |
| `548d562` | 2026-10-22 | kategori tidak kosong dan penunjuk kategori lebih tepat |
| `0d0b1ae` | 2026-10-22 | penanda pemasukan `gaji`/`honor`/`dividen`, kategori kucing/kiriman |

Total: 18 commit (16 kerja + 2 merge), 22 berkas, +3196 / −107 baris.

## 5. Hasil uji di HP

Semua uji dijalankan di HP fisik lewat `AndroidJUnitRunner`, memakai jalur aplikasi yang
sama dengan pemakaian nyata (bukan unit test tiruan).

| Uji | Kalimat | Hasil |
|---|---|---|
| Kategori kustom (19 kalimat) | 19 | jenis 19/19, nominal 19/19, kategori 19/19 |
| 30 kalimat baru | 30 | 17 dicatat, jenis 17/17, kategori 15/17 |
| 34 kalimat (campur) | 34 | 21 dicatat, jenis 21/21, kategori 18/21 |
| 50 kalimat baru | 50 | **50 dicatat, jenis 50/50, nominal 50/50, kategori 47/50** |

Unit test JVM: **116 lulus** (`LocalTransactionParserTest`, `TransactionCorrectorTest`,
`CategoryResolverTest`, `AmountReaderTest`, `LocalToolsJsonTest`).

Sisa 3 kalimat yang kategorinya meleset di uji 50:

1. "dapat hadiah lomba lari 1,5 juta" → Investasi (seharusnya Lainnya; baru `undian` yang dipetakan).
2. "uang ganti rugi asuransi 5 juta" → Gaji (seharusnya Lainnya).
3. "bayar denda parkir 100rb" → Transportasi (label uji menyebut Tagihan; keduanya masuk akal,
   `parkir` lebih spesifik daripada `denda`).

Catatan soal angka: pada uji 30 dan 34, kategori terlihat turun setelah perbaikan karena
kalimat seperti "uang kiriman" dan "cashback" sekarang jujur masuk **Lainnya**, sedangkan
alat uji lama menganggap `Lainnya` selalu salah. Jadi penurunan itu bukan kemunduran mutu.

## 6. Batas yang diketahui

- Aturan kata kunci makin rapuh kalau ditambal per kalimat. Sisa kesalahan sebaiknya
  diselesaikan lewat data latih v6, bukan menambah kata kunci terus-menerus.
- Model hanya dilatih dengan prefix tanggal; menambah konteks percakapan panjang menurunkan akurasi.
- Berkas model dan `.so` tidak ada di repo, jadi build release butuh langkah setup manual.

## 7. Lisensi

- **Model dasar Needle** (Cactus Compute) berlisensi **Apache-2.0**; bobot `rank64.cact`
  adalah turunan hasil fine-tune dari model tersebut.
- **Mesin Cactus** (runtime yang dibangun menjadi `libneedle_jni.so`) memakai lisensi
  source-available tersendiri, **bukan** Apache-2.0. Ini perlu ditinjau ulang sebelum
  aplikasi didistribusikan publik atau dijadikan open source.
- Teks lisensi lengkap: <https://github.com/cactus-compute/needle/blob/main/LICENSE>
