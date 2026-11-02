# PROGRESS — Nano Money

Catatan kemajuan otak lokal (model + aturan aplikasi). Terakhir diperbarui
**2 Nov 2026**. Angka di sini angka yang benar-benar diukur, bukan target.

> **Penting:** semua angka "eval" dan "uji HP" di bawah adalah **model + lapisan
> kata kunci aplikasi** (`CategoryHints`). AI murni jauh lebih rendah: kategori
> **67,9%** di eval dan **13–30%** pada 30 kalimat baru (tergantung format daftar
> kategori). Goal proyek adalah 90–99% **AI murni**, dan itu belum tercapai.
> Rincian angka murni ada di `PROJECT.md`.

## Keadaan sekarang

| Hal | Nilai |
|---|---|
| APK terpasang | `com.aistudio.pencatatkeuangan.nnomny`, `versionCode=1`, `versionName=1.0` |
| HP uji | M2101K6G (Redmi Note 10 Pro), `firstInstallTime` `2026-10-29 08:43:40` (data utuh) |
| APK lokal | `app/build/outputs/apk/debug/app-debug.apk`, 169,7 MB, MD5 `A748FCBD507C206207FD5FD4746AD253` |
| Model di aset | `app/src/main/assets/rank64.cact`, 63,4 MB, identik `r10_cat_a.cact` (MD5 `90C128E4187AB114B5F65609A179E7AF`) |
| Branch | `main`, sejajar `origin/main` di `570dab4` |

Model r10 tetap dipakai. Kandidat lain (`contrast_v2`, `v10`, `r10ft`,
`overfit189`) sudah diuji dan **tidak** menggantikan r10: ada yang inflated
(189 query eval ikut di data latih), ada yang seri/kalah.

## Angka eval 554 baris (`gold_v6/eval_599.jsonl`)

Prediksi model `r10_deq`, lalu lapisan aplikasi (`vm/measure_app_layer.py`):

| Ukuran | Model saja | + lapisan app |
|---|---|---|
| Jenis | 97,7% (541/554) | **99,6% (552/554)** |
| Kategori | 67,9% (376/554) | **90,6% (502/554)** |
| Kategori kustom | 78,6% | **100% (46/46)** |
| Kategori bawaan | 65,0% | **89,8% (456/508)** |

Angka ini **optimistis**: kata kunci kategori dipilih memakai eval yang sama.
Karena itu ada uji kalimat baru di HP.

## Uji di HP dengan kalimat baru

Semua kalimat diverifikasi tidak ada di 109.556 query dari 12 korpus latihan dan
tidak mirip (Jaccard < 0,75) dengan set uji lain.

| Set | Isi | Jenis | Kategori | Nominal |
|---|---|---|---|---|
| Set 1 | 30 kalimat, sebelum perbaikan | 26/30 (86,7%) | 20/30 (66,7%) | 30/30 |
| Set 2 | 30 kalimat, dipakai mendesain perbaikan | 29/30 (96,7%) | 22/30 (73,3%) | 30/30 |
| Set 3 | 30 kalimat, sesudah perbaikan, tidak dipakai mendesain | 26/30 (86,7%) | 27/30 (90,0%) | 29/30 |

Set 2 tidak bebas bias (kalimatnya yang dipakai memilih perbaikan). Angka yang
paling jujur untuk generalisasi adalah **set 1 dan set 3**: jenis ±87%,
kategori 67–90% tergantung seberapa umum kosakatanya.

## Yang sudah diperbaiki

1. **Kata kategori bahasa sehari-hari** (±178 kata) — `c3593ce`.
   Dipilih lewat ablasi per kata pada 554 baris eval, bukan tebakan. `jual`
   sengaja tidak dipakai: "jual hp bekas" itu barang pribadi, bukan Freelance.
2. **Arah keluar** untuk `sarapan`, `makan`, `ongkir` — `e0aed46`.
   Sebelumnya model menebak PEMASUKAN untuk "sarapan bubur ayam" dan "ongkir j&t".
3. **Empat akar aturan** — `570dab4`:
   - `INCOME_NOUNS` (tunjangan, honor, upah, lembur, pesangon, …) menang atas
     nama kategori lawan yang cuma jadi keterangan.
   - `INCOME_MASKS` membuang "tiket masuk"/"biaya masuk"/"pintu masuk" sebelum
     kata "masuk" dihitung sebagai pemasukan.
   - `ngajar`/`mengajar` dihitung pemasukan ("bayaran" sengaja tidak: arahnya dua).
   - `FALLBACKS["donasi"] = "lainnya"` supaya sedekah tidak jatuh ke tebakan model.

## Test

- Suite JVM: **265 test, 28 suite, semua hijau**.
  `./gradlew.bat :app:testDebugUnitTest`
- Test yang mengunci perilaku: `TransactionCorrectorTest` (29),
  `CategoryHintsTest` (10), `LocalToolsJsonTest`, `CategoryMigrationTest`.
- Test HP (`app/src/androidTest/.../UjiHp*Test.kt`) **tidak di-commit**; dipakai
  sekali jalan lalu paket `*.test` di-uninstall.

## Sisa celah (jujur)

1. **Jenis ±87% pada kalimat tanpa kata kerja arah.** Contoh yang masih salah:
   `fee konsultan pajak` (fee belum penanda masuk, "pajak" menunjuk Tagihan),
   `periksa ke dokter gigi` (periksa belum kata kerja keluar),
   `naik krl` (tidak tercatat, aplikasi balik bertanya arah).
2. **Bug aturan**: `uang dari` ada di `EARLY_INCOME_PHRASES` tetapi tidak
   dihitung di `hasIncomeSignal`, jadi "uang kasbon dari bos" tercatat keluar.
3. **Kosakata** yang belum ada: `arisan`, `sepeda`, `sajadah`, `pupuk`,
   `kebun raya`, `les privat`. Ini menambal satu per satu dan akan terus muncul.
4. **Gate model-side yang belum lolos**: item 85,3% (gate 90%) dan off-topic
   18/21 (gate 95%). Keduanya tidak bisa ditutup oleh kata kunci.

## Berikutnya (belum dikerjakan)

1. Perbaiki bug `uang dari` + kata arah (`fee`, `periksa`, `naik`), lalu uji
   dengan **set ke-4** yang benar-benar baru.
2. Kalau angka masih mentok ±87%, pengungkitnya model (fine-tune / data),
   bukan daftar kata.

## Catatan kerja

- APK harus memakai `applicationId`, `versionCode`, dan `versionName` yang sama
  supaya menimpa, bukan jadi aplikasi kedua.
- MIUI memblokir install lewat Gradle: pakai `adb install -r -t`.
- Jalankan instrumentasi tanpa `-w` kalau USB sering putus, lalu baca hasil dari
  `adb logcat -s <TAG>:*`.
- Backup data HP sebelum pasang:
  `C:\Users\yasin\Nano-Money\backup\nanomoney-backup-20261102.tar`.
- Repo data (`C:\Users\yasin\needle-nanomoney`) belum punya remote; catatan
  panjangnya ada di `PASANG-LOKAL.md` seksi 12–14.
