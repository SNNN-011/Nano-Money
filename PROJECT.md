# PROJECT — Nano Money (otak lokal)

Dokumen ini menjelaskan **apa** yang sedang dibangun dan **kapan dianggap
selesai**. Untuk angka dan kemajuan terkini, lihat `STATUS.md`.

## Tujuan

Membuat pencatatan keuangan lewat kalimat bahasa sehari-hari yang berjalan
**sepenuhnya offline di HP**, dengan sasaran utama:

> **Model lokal sendiri mencapai akurasi kategori 90–99%, murni dari AI
> (tanpa atau hanya sedikit bantuan kode).**

Urutan prioritas: **kategori** dulu, **jenis** (masuk/keluar) kedua, lalu
nominal/item/tanggal.

Arti "tanpa bantuan kode": `CategoryHints` (peta kata → kategori) dan koreksi
kategori lain di lapisan aplikasi **tidak dipakai** saat mengukur. `TransactionCorrector`
hanya boleh dipakai untuk arah, dan itupun dilaporkan terpisah. Model yang
diukur adalah `r10` (aset `app/src/main/assets/rank64.cact`, ±63 MB) apa adanya.

Lapisan kata kunci yang ada sekarang tetap dipakai di aplikasi sebagai jaring
pengaman, tetapi **bukan** ukuran keberhasilan proyek ini.

## Ruang lingkup

**Masuk lingkup**

- Model lokal: bobot `r10`, data latih (`data_app/`, `draf_v5/`, `draf_v6/`),
  prompt/enum yang dikirim ke model, dan fine-tune.
- Pengukuran **model-only** (tanpa `CategoryHints`): `gold_v6/eval_599.jsonl`,
  set OOD, dan set kalimat baru di HP.
- APK debug dan pengujian di HP lewat instrumentasi.
- Dokumentasi keputusan dan angka di `STATUS.md`.

**Di luar lingkup (non-goals)**

- **UI/desain tidak disentuh.** Tidak ada komponen, warna, jarak, atau tata
  letak baru; kalau perlu menampilkan sesuatu, pakai pola yang sudah ada.
- Tidak menambah dependensi baru kalau stdlib/komponen yang ada cukup.
- Tidak mengklaim akurasi dari hasil lapisan kata kunci. Angka model-only dan
  angka app-level harus selalu dilaporkan terpisah.
- Tidak ada cloud, sinkronisasi, atau akun.

## Definition of Done

### Untuk setiap perubahan

- Satu perubahan = satu commit, pesan commit menjelaskan sebabnya.
- `./gradlew.bat :app:testDebugUnitTest` hijau (termasuk test yang mengunci
  perilaku baru).
- Tidak ada baris eval yang berubah dari **benar menjadi salah**. Kenaikan wajib
  disertai jumlah baris berubah benar/salah.
- Angka dilaporkan apa adanya; variasi sampling model dicatat, bukan disembunyikan.
- Test HP (`UjiHp*Test.kt`) tidak di-commit; paket `*.test` di-uninstall sesudah uji.

### Untuk pemasangan di HP

- `applicationId`, `versionCode`, dan `versionName` sama → menimpa, bukan aplikasi kedua.
- `firstInstallTime` tidak berubah dan data pengguna masih tampil di aplikasi.
- Backup data diambil sebelum memasang versi baru.

### Untuk rilis (gerbang deploy)

Sasaran proyek diukur **model-only** (tanpa `CategoryHints`). Angka app-level
boleh dilaporkan sebagai jaring pengaman, tetapi tidak boleh dipakai untuk
mengklaim goal tercapai.

| Ukuran (model-only) | Ambang |
|---|---|
| **Kategori** di eval 599 | **≥ 90%** (target 90–99%) |
| **Kategori** di set OOD / kalimat baru | **≥ 85%** (target mendekati 90%) |
| Kategori kustom | ≥ 85% |
| Jenis | ≥ 97% (recall masuk dan keluar masing-masing ≥ 95%) |
| Item | ≥ 90% |
| Off-topic | ≥ 95% |
| JSON rusak | 0 |
| Patuh enum | 100% |
| Ukuran `.cact` | ≤ 70 MB |
| Waktu parse di Redmi Note 10 Pro | ≤ 1,5 detik |

Syarat tambahan: format daftar kategori yang dikirim ke model harus sama dengan
format saat latihan (huruf kecil, urut, di dalam `enum`). Selisih karena format
dihitung sebagai kegagalan, bukan "bantuan kode".

Kalau gerbang gagal, r10 tetap terpasang dan kegagalan dianalisis — tidak
dipaksakan deploy.

## Status terhadap Definition of Done

Angka murni model (tanpa bantuan kata kunci), 2 Nov 2026:

| Uji | Kategori | Jenis |
|---|---|---|
| eval 554 baris (enum per baris, huruf kecil) | **67,9%** | 97,7% |
| 30 kalimat baru, daftar 21 kategori huruf kecil | **30,0%** | 86,7% |
| 30 kalimat baru, daftar 21 kategori huruf besar (yang dikirim aplikasi) | **13,3%** | 90,0% |
| 30 kalimat baru, daftar 7 kategori lama (huruf besar) | **10,0%** | 76,7% |

Sebagai pembanding, dengan lapisan kata kunci aplikasi angka yang sama menjadi
kategori 90,6% (eval) dan 90,0% (kalimat baru). Artinya **hampir seluruh**
akurasi kategori yang terlihat selama ini berasal dari kode, bukan dari AI.

Kesimpulan: goal 90–99% AI murni **belum tercapai** dan masih jauh. Dua temuan
yang menentukan langkah berikutnya:

1. Format daftar kategori berpengaruh besar (13,3% → 30,0% hanya dengan huruf
   kecil). Ini bug format, bukan kurang pintar.
2. Walaupun format diperbaiki, model masih 30% pada kalimat baru dan 67,9% di
   eval, jadi pengungkit utamanya data/fine-tune, bukan tambalan aturan.
