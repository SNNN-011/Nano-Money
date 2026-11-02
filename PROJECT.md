# PROJECT — Nano Money (otak lokal)

Dokumen ini menjelaskan **apa** yang sedang dibangun dan **kapan dianggap
selesai**. Untuk angka dan kemajuan terkini, lihat `STATUS.md`.

## Tujuan

Membuat pencatatan keuangan lewat kalimat bahasa sehari-hari yang berjalan
**sepenuhnya offline di HP** — tanpa API, tanpa kirim data ke server — dengan
prioritas yang sudah ditetapkan pemilik:

1. **Kategori** paling penting.
2. **Jenis** (masuk/keluar) kedua.
3. Nominal, item, dan tanggal menyusul.

Model tetap `r10` (aset `app/src/main/assets/rank64.cact`, ±63 MB) yang dipakai
bersama lapisan aturan aplikasi. Model hanya dipakai kalau terbukti lebih baik;
kalau tidak, r10 tetap juara dan yang diperbaiki adalah aturannya.

## Ruang lingkup

**Masuk lingkup**

- Lapisan aturan setelah jawaban model: `TransactionCorrector` (arah),
  `CategoryHints` (kata → kategori), `CategoryResolver` (kecocokan nama kategori).
- Data latih/eval dan alat ukurnya: `data_app/`, `draf_v5/`,
  `gold_v6/eval_599.jsonl`, `vm/measure_app_layer.py`.
- APK debug dan pengujian di HP lewat instrumentasi.
- Dokumentasi keputusan dan angka di `STATUS.md`.

**Di luar lingkup (non-goals)**

- **UI/desain tidak disentuh.** Tidak ada komponen, warna, jarak, atau tata
  letak baru; kalau perlu menampilkan sesuatu, pakai pola yang sudah ada.
- Tidak menambah dependensi baru kalau stdlib/komponen yang ada cukup.
- Tidak memaksa ganti model demi mengejar angka; kandidat yang kalah tetap kalah.
- Tidak ada cloud, sinkronisasi, atau akun.
- Tidak menambah kategori/kata hanya untuk mengejar satu set uji.

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

Angka harus memenuhi **semua** ini pada eval 599 baris:

| Ukuran | Ambang |
|---|---|
| Kategori total | ≥ 78% |
| Kategori OOD | ≥ 75% |
| Kategori kustom | ≥ 85% |
| Jenis | ≥ 97% (recall masuk dan keluar masing-masing ≥ 95%) |
| Item | ≥ 90% |
| Off-topic | ≥ 95% |
| JSON rusak | 0 |
| Patuh enum | 100% |
| app-exact | menang ≥ 8 poin dari r10, tidak ada slice turun > 2 poin |
| Ukuran `.cact` | ≤ 70 MB |
| Waktu parse di Redmi Note 10 Pro | ≤ 1,5 detik |
| Uji HP | kategori ≥ 90%, jenis/nominal/item tidak regresi |

Kalau gerbang gagal, r10 tetap terpasang dan kegagalan dianalisis — tidak
dipaksakan deploy.

## Status terhadap Definition of Done

- **Lolos**: kategori eval 90,6%, jenis eval 99,6%, kustom 100%, rusak 0,
  enum 100%, `.cact` 63,4 MB.
- **Belum lolos**:
  - **Jenis pada kalimat yang benar-benar baru: ±87%** (ambang 97%). Ini yang
    paling jauh dan tidak bisa ditutup hanya dengan daftar kata.
  - **Kategori di HP pada kalimat baru: 67–90%** (ambang 90%) — tergantung
    seberapa umum kosakatanya.
  - Item 85,3% (ambang 90%) dan off-topic 18/21 (ambang 95%); keduanya
    model-side.
- Konsekuensinya: proyek **belum selesai** menurut definisi di atas, meski
  sebagian besar gerbang eval sudah terpenuhi.
