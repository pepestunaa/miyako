# Rencana Optimasi Preview dan Detail Manga

> **SUPERSEDED — jangan dibaca sebagai acuan.** Dokumen ini menganjurkan batch window, animasi buka
> (glide) dan ingatan posisi saat panel dibuka ulang — tiga hal yang justru dihapus Phase A di
> `todo.md`. Status yang benar ada di `docs/preview-checkpoint.md`. Isi di bawah dibiarkan hanya
> sebagai riwayat keputusan; kalau sedang mengerjakan preview, berhenti membaca di sini.

## Tujuan

Ketika preview dibuka, aplikasi harus:

- memprioritaskan chapter dan halaman terakhir yang dibaca;
- tidak memuat seluruh chapter sekaligus;
- memuat data chapter secara bertahap berdasarkan viewport;
- mempertahankan posisi preview ketika chapter baru disisipkan;
- mengingat posisi preview ketika panel ditutup dan dibuka kembali;
- menjalankan animasi dari posisi preview terakhir menuju halaman terakhir dibaca;
- tetap menjaga scroll detail page dan list chapters agar halus.

## Pemahaman dan batasan yang sudah disepakati

### Lazy loading yang sudah ada

Lazy loading gambar halaman sudah tersedia dan harus dipertahankan. Perubahan ini
bukan untuk menggantinya atau memuat semua gambar lebih awal.

Lazy loading yang perlu diperbaiki berada pada level chapter:

```text
Chapter/page-list lazy loading:
  chapter atau section baru diproses ketika masuk atau mendekati viewport

Page/image lazy loading:
  gambar halaman diproses ketika item halaman terlihat di viewport
```

Data chapter dari presenter boleh tetap menjadi sumber urutan dan target scroll.
Yang harus ditunda adalah pemuatan page-list, pemasangan item halaman, dan pekerjaan
berat lain untuk chapter yang belum diperlukan.

### Prioritas awal

Jika history tersedia:

1. Ambil chapter terakhir berdasarkan history terbaru.
2. Ambil `last_page_read` dari chapter tersebut.
3. Muat page-list chapter target terlebih dahulu.
4. Tampilkan dan posisikan halaman terakhir dibaca.
5. Muat chapter lain hanya ketika dibutuhkan oleh viewport.

Jika history belum tersedia, tunggu history sebelum menentukan target. Jika history
kosong atau chapter target sudah tidak ada, gunakan perilaku fallback yang sudah
berlaku tanpa membuat target palsu.

## Ruang lingkup pekerjaan

### 1. Prioritaskan chapter terakhir dibaca

**Todo:** `fix urutan preview berdasarkan halaman terakhir yang dibuka...`

Periksa dan pertahankan mekanisme penentuan `currentPage` dan chapter target.
Pastikan target tidak hanya diberi marker biru, tetapi juga menjadi prioritas
pemuatan page-list.

Acceptance criteria:

- Preview manga dengan history langsung memprioritaskan chapter terakhir dibaca.
- Halaman terakhir dibaca tersedia sebelum chapter yang jauh dari target diproses.
- Preview tanpa history tetap dapat dibuka tanpa menunggu target yang tidak ada.
- Marker dan posisi halaman tidak berasal dari history lama setelah kembali dari reader.
- Chapter target yang berada di luar batch awal tetap dapat ditemukan tanpa memuat
  seluruh chapter sebelum target.

### 2. Lazy loading chapter dan batch dinamis

**Todo:** `jangan langsung meload seluruh chapter ketika membuka preview...`

Batch harus mengikuti viewport, bukan jumlah tetap yang memuat banyak chapter sejak
awal.

Perilaku yang diinginkan:

1. Muat chapter target terlebih dahulu.
2. Saat pengguna bergerak ke bawah, siapkan chapter berikutnya yang mendekati
   viewport.
3. Saat pengguna bergerak ke atas, siapkan chapter sebelumnya yang mendekati
   viewport.
4. Tunda chapter yang jauh dari viewport.
5. Jangan memuat chapter atau page-list yang sama dua kali.
6. Jangan memasukkan item halaman ketika RecyclerView sedang melakukan scroll aktif
   jika hal tersebut menyebabkan layout bergeser; pasang setelah scroll stabil.

Prefetch chapter tetangga boleh digunakan untuk menjaga scroll tetap halus, tetapi
harus kecil, terarah, dan tidak berubah menjadi preload seluruh daftar.

Acceptance criteria:

- Membuka manga dengan ratusan chapter tidak memuat semua page-list pada awal.
- Chapter yang masuk viewport dapat muncul tanpa jeda yang mengganggu.
- Scroll cepat tetap memicu pemuatan chapter yang benar.
- Arah atas dan bawah sama-sama berfungsi.
- Tidak ada chapter/page duplikat atau request berulang.
- Urutan chapter tetap sesuai sorting dan filter yang dipilih pengguna.

### 3. Analisis dan perbaikan scroll jank detail page

**Todo:** `fix scroll jank pada detail page...`

Fokus analisis utama adalah hubungan detail page dengan list chapters. Periksa:

- `setChapters`, `updateChapters`, dan `updateHeader` yang dapat membangun ulang
  adapter utama dan preview secara bersamaan;
- callback scroll pada detail RecyclerView dan preview RecyclerView;
- refresh atau update adapter yang terjadi saat status read/bookmark/download
  berubah;
- perubahan ukuran item ketika page-list atau thumbnail terpasang;
- listener atau animasi `Back to top`, `Resume`, swipe chapter, dan preview;
- pekerjaan berat yang berjalan setiap frame scroll;
- hilangnya posisi RecyclerView akibat `updateDataSet`, `notifyDataSetChanged`, atau
  layout ulang.

Acceptance criteria:

- Scroll detail page kembali ke atas tidak mengalami stuttering yang terlihat.
- Update status chapter tidak me-refresh seluruh daftar tanpa kebutuhan.
- Tombol `Back to top` dan `Resume` tetap memiliki fungsi serta animasi yang ada.
- Swipe read/bookmark pada chapter tetap berfungsi.
- Perbaikan preview tidak mengganggu scroll utama detail page.

### 4. Perbaiki arah animasi saat preview dibuka kembali

**Todo:** `fix dynamic scroll position pada preview...`

Fitur menuju halaman terakhir dibaca sudah ada. Masalah yang harus diperbaiki adalah
titik awal animasinya.

State yang diharapkan:

```text
Pembukaan pertama:
  mulai dari posisi awal -> animasi menuju halaman terakhir dibaca

Setelah pengguna menutup preview dari posisi bawah:
  pulihkan posisi bawah -> animasi menuju halaman terakhir dibaca
```

Saat ini perlu dianalisis apakah posisi di-reset oleh salah satu proses berikut:

- `scrollToCurrentPage()` dipanggil setiap kali gesture membuka panel dimulai;
- `cancelPendingScroll()` menghapus anchor atau state yang masih diperlukan;
- `setChapters()` atau `rebuildPageGrid()` mengganti dataset dan menghilangkan
  posisi layout;
- panel yang dibuat `GONE` menyebabkan LayoutManager tidak mempertahankan state;
- batch atau page-list baru masuk sebelum animasi menentukan posisi awal.

Implementasi harus:

1. membedakan pembukaan pertama dengan pembukaan kembali;
2. menyimpan anchor atau `LayoutManager` state saat preview ditutup;
3. memulihkan posisi sebelum animasi menuju target dijalankan;
4. menggunakan posisi aktual saat ini sebagai awal animasi;
5. menghindari animasi kecil yang hanya terlihat sebagai glitch;
6. tetap menangani history yang selesai dimuat setelah panel sudah dibuka.

Acceptance criteria:

- Pembukaan pertama tetap mengarahkan pengguna ke halaman terakhir dibaca.
- Setelah preview ditutup dari bawah, pembukaan berikutnya tidak dimulai dari atas.
- Jika target berada di atas posisi terakhir, animasi bergerak dari bawah ke target.
- Jika target sudah berada di posisi yang benar, tidak ada koreksi scroll kecil yang
  terlihat sebagai glitch.
- Swipe buka/tutup berulang tidak menghapus atau merusak posisi preview.

### 5. Pertahankan anchor saat chapter sebelumnya dimuat

**Todo:** `fix preview grid positioning...`

Masalahnya adalah chapter yang disisipkan di atas menambah tinggi konten sebelum
halaman terakhir dibaca, sehingga target semakin terdorong ke bawah.

Solusi harus menggunakan scroll anchoring berbasis identitas item, bukan hanya index:

1. Simpan item anchor yang terlihat dan offset pixel-nya.
2. Sisipkan page-list atau chapter baru.
3. Cari kembali item anchor berdasarkan chapter ID dan page index/stable ID.
4. Pulihkan posisi anchor dengan offset yang sama.
5. Jika target halaman terakhir sedang menjadi anchor, pertahankan target tersebut
   di posisi visual yang sama.

Chapter sebelumnya tidak boleh dinonaktifkan. Chapter sebelumnya dan berikutnya tetap
harus dapat dimuat progresif tanpa membuat posisi pengguna melompat.

Acceptance criteria:

- Memuat chapter sebelumnya tidak mendorong halaman terakhir secara kumulatif ke
  bawah.
- Posisi awal target stabil walaupun beberapa batch selesai asynchronous.
- Chapter sebelumnya tetap bisa diakses dengan scroll ke atas.
- Chapter berikutnya tetap bisa diakses dengan scroll ke bawah.
- Tidak ada reset ke posisi paling atas setelah `rebuildPageGrid()` atau update data.
- Anchor tetap benar saat filter, sorting, atau refresh chapter diproses.

## File utama yang perlu dianalisis

- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsAdapter.kt`
  - batch chapter;
  - page-section loading;
  - `currentPage` dan target chapter;
  - insert item halaman;
  - viewport anchor;
  - state scroll dan reopen preview.
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsController.kt`
  - lifecycle panel preview;
  - gesture buka/tutup;
  - `scrollToCurrentPage()`;
  - listener scroll detail dan preview;
  - update list chapters.
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsPresenter.kt`
  - pemuatan history;
  - urutan chapter;
  - refresh chapter setelah kembali dari reader;
  - pemuatan page-list.
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/chapter/ChapterPagesSectionItem.kt`
  - state section dan lifecycle bind.
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/chapter/ChapterPageItem.kt`
  - identitas item halaman dan marker halaman terakhir.
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/ChapterLayoutManager.kt`
  - perilaku grid dan perhitungan posisi item.

## Urutan eksekusi

1. Audit alur history, urutan chapter, dan state preview yang sudah ada.
2. Tambahkan atau perbaiki prioritas page-list chapter terakhir dibaca.
3. Perbaiki lazy loading chapter berbasis viewport.
4. Gabungkan pemuatan tersebut dengan batch dinamis atas dan bawah.
5. Stabilkan scroll anchor ketika page-list/chapter baru disisipkan.
6. Perbaiki penyimpanan dan pemulihan posisi saat preview dibuka kembali.
7. Analisis jank list chapters dan detail page menggunakan perubahan paling kecil.
8. Build, install, dan jalankan audit E2E di device fisik.
9. Setelah tiap topik lolos verifikasi, centang item terkait di `todo.md` dan buat
   commit terpisah.

## Invarian yang wajib dipertahankan

- Tidak mengubah gaya, navigasi, gesture, atau alur UI Yōkai tanpa kebutuhan.
- Tidak menghapus swipe chapter, bookmark, read, filter, sorting, atau preview.
- Tidak menonaktifkan pemuatan chapter sebelumnya hanya untuk menghindari scroll jump.
- Tidak menggunakan posisi adapter sebagai satu-satunya identitas anchor.
- Tidak memuat seluruh chapter atau seluruh gambar hanya untuk mendapatkan target.
- Tidak melakukan refresh adapter penuh pada setiap frame scroll.

## Verifikasi teknis dan manual

### Build development

Gunakan build debug, bukan release:

```sh
./gradlew :app:assembleStandardDebug -PdevAbi=x86_64 --console=plain -x lint
```

Install ke emulator (AVD `miyako-dev`, serial `emulator-5554`):

```sh
adb -s emulator-5554 install -r \
  app/build/outputs/apk/standard/debug/app-standard-x86_64-debug.apk
```

### Golden path

1. Bersihkan logcat.
2. Buka aplikasi dan masuk ke Library.
3. Buka manga dengan banyak chapter dan history di chapter yang jauh.
4. Buka preview dari swipe kiri.
5. Pastikan chapter/halaman terakhir dibaca diprioritaskan.
6. Scroll perlahan dan cepat ke atas serta ke bawah.
7. Pastikan chapter baru dimuat saat mendekati viewport.
8. Tutup preview dari posisi bawah.
9. Buka kembali preview dan pastikan animasi dimulai dari posisi sebelumnya menuju
   halaman terakhir dibaca.
10. Pastikan loading chapter di atas tidak menggeser target secara bertumpuk.

### Edge case

- Manga tanpa history.
- History belum selesai dimuat ketika preview dibuka.
- Chapter terakhir dibaca berada di awal, tengah, dan akhir daftar.
- Manga dengan ratusan chapter.
- Semua chapter sudah selesai dibaca.
- Chapter target sudah dihapus atau tidak memiliki page-list.
- Swipe cepat, swipe lambat, diagonal, dan drag vertikal.
- Buka-tutup preview berkali-kali.
- Filter dan sorting chapter.
- Status read/bookmark berubah saat preview atau list chapters sedang terlihat.

### Crash dan regresi

```sh
adb -s emulator-5554 logcat -d | grep -c FATAL
adb -s emulator-5554 logcat -d -b crash
```

Hasil yang diharapkan: tidak ada crash dan tidak ada `FATAL`. Regression check wajib
mencakup navigasi back, scroll list chapters, swipe read/bookmark, tema, tombol
`Back to top`, tombol `Resume`, dan pembukaan reader.

## Strategi commit

Pisahkan perubahan berdasarkan topik agar mudah ditelusuri:

1. `perf(Manga): prioritize last-read preview chapter`
2. `perf(Manga): lazy load preview chapters by viewport`
3. `fix(Manga): preserve preview scroll anchor`
4. `fix(Manga): restore preview reopen position`
5. `perf(Manga): smooth chapter list scrolling`

Jangan mencentang item `todo.md` sebelum implementasi dan audit untuk topik tersebut
benar-benar selesai.

## Kondisi awal dokumen

- Branch: `dev`.
- Revision saat dokumen dibuat: `d74e1bf2ef`.
- Working tree bersih sebelum dokumen dibuat.
- Perubahan batch window dan smooth scrolling sebelumnya telah direvert, sehingga
  implementasi berikutnya harus memeriksa ulang baseline kode saat ini, bukan
  menganggap commit lama masih aktif.
