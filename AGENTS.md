# AGENTS.md

Aturan dan konvensi kerja untuk siapa pun (manusia atau agent) yang mengerjakan project ini.

## Tentang Project

- **Miyako** adalah fork personal dari [Yōkai](https://github.com/null2264/yokai) (yang merupakan fork Mihon/Tachiyomi).
- Tujuan fork: menjadikan app **lebih ringan, hemat RAM, dan mulus** (referensi UX: Usagi) **tanpa mengubah UI/UX Yōkai** dan **tanpa menghapus fitur**.
- Package app: `eu.kanade.tachiyomi` (+ suffix `.yokai` untuk build standard release). Nama app tetap "Yōkai"; repo bernama "miyako".
- `todo.md` di root adalah daftar pekerjaan dari user. Item `- []` adalah yang harus dikerjakan; item `- [x]` sudah selesai.

## Aturan Wajib (tidak bisa dinegosiasikan)

1. **Selalu commit setiap ada perubahan.** Pecah pekerjaan menjadi beberapa commit logis — jangan pernah menggabungkan semuanya menjadi satu commit besar. Tujuannya agar riwayat bisa ditelusuri kembali setelah sesi berakhir.
2. **Checklist todo.md otomatis** saat item selesai dikerjakan dan diverifikasi. Ubah `- []` menjadi `- [x]`.
3. **Jangan menambahkan catatan ("catatan:") di todo.md.** Cukup ubah status checkbox. Jangan menyentuh item yang dikomentari user (`<!-- ... -->`) kecuali user mengaktifkannya lagi.
4. **Pertahankan UI/UX Yōkai.** Ada larangan keras membuat UI/UX baru yang bertentangan dengan UI/UX Yōkai. Perubahan harus terasa seperti bagian asli dari app (styling, navigasi, gesture, animasi).
5. **Jangan membuang fungsi atau fitur yang sudah ada.** Refactor diperbolehkan selama perilaku dan fitur tetap utuh.
6. **Push hanya ke remote `miyako`** (`https://github.com/pepestunaa/miyako`). Remote `origin` adalah upstream (`null2264/yokai`) — jangan pernah push ke sana.
7. **Sinkronkan `dev` dan `master`**: setelah perubahan di-push ke `dev`, fast-forward `master` ke `dev` dan push `master` juga.
8. **Jangan lakukan operasi git destruktif tanpa izin eksplisit** (force push, `reset --hard`, hapus branch, `clean -f`). Commit baru selalu lebih baik daripada amend.

## Alur Kerja

1. Baca `todo.md`, cek `git status`, dan pahami kode terkait sebelum mengubah apa pun.
2. Implementasi seminimal mungkin dan fokus pada item yang dikerjakan — tanpa refactor atau abstraksi yang tidak diminta.
3. Build, install ke device, dan uji manual fitur yang berubah (lihat bagian **Audit E2E**). Compile sukses bukan bukti fitur berjalan.
4. Commit per topik (lihat konvensi di bawah), lalu check off item todo (tanpa catatan), lalu push `dev` + `master`.
5. Laporkan hasil singkat di akhir: apa yang berubah, commit hash, dan hasil verifikasi.

## Commit Convention

Gunakan Conventional Commits, bahasa Inggris, subject singkat:

```
feat(Manga): glide the preview to the last-read page
fix(Manga): anchor the preview marker to the most recently read chapter
perf(app): cache images on disk and skip reader preload under memory pressure
build: add devAbi flag and parallel Gradle for faster builds
docs: document the preview gesture and lazy thumbnails
docs: rewrite the README with clean, consistent structure
```

- Subject maksimal ~50 karakter, menjelaskan *apa*.
- Body (jika perlu) menjelaskan *kenapa*, bukan *bagaimana*.
- `todo.md` hanya file lokal: sudah di-ignore dan tidak dilacak git, jadi jangan commit file itu.
- Satu commit = satu topik logis; file-file terkait satu perbaikan boleh digabung.

## Build dan Verifikasi di Device

Untuk development, selalu pakai build debug (bukan `assembleStandardRelease`):

```sh
# Build debug (sekitar 1-2 menit, sudah auto-signed)
./gradlew :app:assembleStandardDebug -PdevAbi=arm64-v8a --console=plain -x lint

# Output
app/build/outputs/apk/standard/debug/app-standard-arm64-v8a-debug.apk
```

- Debug sudah di-sign otomatis dengan debug keystore — langsung `adb install -r`, tanpa zipalign/apksigner.
- ApplicationId debug: `eu.kanade.tachiyomi.debugYokai`; launcher: `eu.kanade.tachiyomi.debugYokai/.MainActivityDefaultDummy`. Debug dan rilis bisa terpasang berdampingan.
- `assembleStandardRelease` hanya untuk memverifikasi build rilis (minify/shrink). Output: `app/build/outputs/apk/standard/release/app-standard-arm64-v8a-release-unsigned.apk`.
- APK release tidak di-sign otomatis: `zipalign -f 4` lalu `apksigner.bat sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --out ...`.
- `apksigner` hanya tersedia sebagai `apksigner.bat` di build-tools; gunakan file `.bat`.
- Pakai path `C:/tmp/...` untuk intermediate APK (bukan `/tmp`, zipalign gagal di sana).
- Device uji: OPPO CPH1931 (`d89608bf`), 720x1600 @320dpi, Android 10.
- adb: `/c/platform-tools/adb.exe`.
- Install: `adb -s d89608bf install -r <apk>`.
- Screenshot: `adb -s d89608bf exec-out screencap -p > C:/tmp/foo.png`, lalu baca dengan tool Read.
- Tag logcat untuk panel preview: `YokaiPanel`. Cek crash dengan logcat `FATAL`.
- Jangan menguji berlebihan saat user sedang memakai HP; ambil snapshot singkat dan terkontrol.

## Audit E2E

Setiap perubahan fitur/UI wajib melewati audit end-to-end di device fisik sebelum dilaporkan selesai. Urutannya:

1. Build + install build debug terbaru (lihat bagian sebelumnya).
2. Bersihkan logcat, lalu launch app:

   ```sh
   adb -s d89608bf logcat -c
   adb -s d89608bf shell am start -n eu.kanade.tachiyomi.debugYokai/.MainActivityDefaultDummy
   ```

3. Uji golden path fitur yang berubah. Contoh untuk panel preview halaman:
   - Buka Library → pilih manga → halaman detail.
   - Swipe kiri untuk membuka preview; cek marker biru sesuai chapter/halaman terakhir dibaca.
   - Swipe kanan dan tombol back untuk menutup preview.
   - Pastikan animasi mulus, tanpa kedip atau lompat posisi.
4. Uji edge case yang relevan:
   - Manga tanpa history dan manga yang semua chapternya sudah dibaca.
   - Manga dengan ratusan chapter (anchor harus ke chapter yang benar).
   - Swipe cepat dan swipe pelan, gesture diagonal, drag vertikal.
   - Panel dibuka sebelum history selesai dimuat.
   - Buka-tutup panel berulang kali.
5. Ambil screenshot tiap state penting ke `C:/tmp/...` dan baca dengan tool Read untuk verifikasi visual.
6. Cek crash dan error:
   - `adb -s d89608bf logcat -d | grep -c FATAL` harus `0`.
   - Buffer crash harus kosong: `adb -s d89608bf logcat -d -b crash`.
7. Regression check di sekitar fitur: navigasi back, swipe chapter row (bookmark/read), scroll daftar chapter, tema, dan fitur lain yang menyentuh file yang sama.
8. Baru setelah lolos audit: commit per topik, check off todo, lalu push.

Aturan audit:

- Compile sukses bukan bukti fitur benar; perubahan UI wajib diuji di device.
- Jangan menguji berlebihan saat user sedang memakai HP.
- Hasil audit dilaporkan di akhir sesi — **bukan** ditulis sebagai catatan di `todo.md`.
- Perubahan non-UI (build/dependency) cukup: build → install → launch → logcat bersih → tidak ada regresi saat startup.

## Checkpoint dan Oper Antar Agent

Pekerjaan multi-step harus bisa diambil alih agent lain tanpa membaca log chat.

- Satu baris bukti di body **setiap commit yang mengubah kode**, format tetap:

  ```
  evidence: build=<ok|fail|n/a> | verify=<PASS|PENDING|N/A> | next=<satu langkah berikutnya>
  ```

  `verify` = hasil uji yang relevan dengan perubahannya: audit device untuk UI, test atau pengukuran untuk non-UI, `N/A` untuk dokumen.
- `PENDING` artinya kode berubah dan build lolos, **tetapi belum diuji**. Itu bukan status selesai. Checkbox `todo.md` hanya untuk yang `PASS`.
- Untuk pekerjaan yang berjalan lebih dari satu commit, jaga satu file status per topik (mis. `docs/preview-checkpoint.md`) berisi tabel langkah → commit → build → verify, dan perbarui di commit yang sama dengan kodenya — jangan menyusul di commit berbeda.
- Kalau budget atau sisa waktu menipis: tulis checkpoint dan commit **lebih dulu**, baru berhenti. Berhenti tanpa checkpoint memaksa agent berikutnya mencari ulang apa yang sudah kamu tahu.
- Agent baru mulai dari: `agents.md` → file status topik → `git log --oneline -5`. Setelah itu jangan mengulang diagnosis yang sudah tercatat di file status.
- Baris `evidence:` ditegakkan oleh `.githooks/commit-msg` untuk commit yang menyentuh `app/src/main/**`. Aktifkan per clone: `git config core.hooksPath .githooks`, atau salin `.githooks/commit-msg` ke `.git/hooks/`.

## Tooling dan Hemat Token

- Pakai skill yang terpasang sebelum eksplorasi manual. Saat ini belum ada MCP server yang dikonfigurasi; kalau nanti ditambah, perlakukan sama.
  - `codebase-memory` untuk pertanyaan struktural — siapa memanggil X, apa yang dipanggil X, analisis dampak.
  - `reverse-engineer-anything` untuk membandingkan artefak atau refs dari app acuan (mis. Usagi) ketika sumbernya tidak ada di repo ini.
  - `deep-research` untuk fakta di luar repo.
- `Grep` dulu, `Read` kemudian, dan pakai `offset`/`limit`. Jangan membaca file 900 baris untuk mengecek satu fungsi.
- Pencarian yang butuh lebih dari tiga kueri delegasikan ke subagent, dan minta ringkasan berisi path plus nama simbol, bukan isi file.
- Jangan memasukkan diff atau logcat penuh ke konteks: pakai `git diff --stat`, `grep -c`, `tail`.
- Nomor baris di dokumen dan prompt bergeser setiap kali file diedit; rujuk nama simbol, bukan nomor baris.

## Referensi di Luar Repo (boleh dibaca, jangan diubah)

Acuan implementasi sudah ada di direktori induk `D:/coding/`. Cek ini dulu sebelum menulis perilaku UI baru atau menebak cara kerja fork lain:

| Lokasi | Isi | Pakai untuk |
|--------|-----|-------------|
| `D:/coding/_refs/yokai-upstream/` | `null2264/yokai` (HEAD `843a59b`) | perilaku asli Yōkai sebelum disentuh fork; pembanding kompatibilitas dan sumber kode bawaan saat mengecek "dulu bagaimana" |
| `D:/coding/_refs/tachiyomisy/` | `jobobby04/TachiyomiSY` (HEAD `c1eb1e3`) | desain halaman pages/preview dan slider menu grid yang jadi acuan user |
| `D:/coding/Usagi/` | `UsagiApp/Usagi` (HEAD `ae98217`) | strategi loader preview hemat RAM, window halaman, dan ukuran "mulus" yang dijadikan referensi rasa app |

Cara membacanya:

- **Read-only.** Tiga direktori itu bukan bagian project ini — jangan pernah mengedit, menjalankan build, atau membuat commit di dalamnya.
- Gunakan `Grep` dengan `path` ke direktori target (mis. `path: D:/coding/Usagi/app`), lalu `Read` pakai `offset`/`limit`. Jangan `find` dari `/`; pohonnya besar dan memakan waktu.
- Kutip `path` plus nama simbol di laporan atau body commit, jangan menyalin satu file utuh ke repo ini.
- HEAD di atas dicatat saat aturan ini ditulis dan checkout itu bisa tertinggal dari upstream. Sebutkan hash yang dibaca supaya perbandingan bisa diulang agent berikutnya.
- Mengadopsi perilaku dari sana tetap harus lolos aturan fork: UI/UX Yōkai tidak berubah dan fitur yang ada tidak dibuang.

## Catatan Git Bash (Windows)

- Set `export MSYS_NO_PATHCONV=1` sebelum perintah adb yang memakai path di dalam device.
- Jangan background chain dengan `&`; environment variable (mis. `ADB=...`) hilang di shell baru. Jalankan assignment dan perintah di satu shell foreground.
- Staging per-hunk bisa memakai `awk` (`RS="\n@@ "`) + `git apply --cached` untuk memisahkan commit logis.

## Peta Kode Kunci

- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsController.kt` — gesture halaman detail, panel preview kanan, scrim, magnet swipe, hint sekali.
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsAdapter.kt` — grid halaman per chapter, marker halaman terakhir dibaca, anchor + glide.
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsPresenter.kt` — data chapters/history, urutan load.
- `app/src/main/res/layout/manga_details_controller.xml` — layout detail + view scrim preview.
- `app/src/main/res/drawable/preview_scrim_top.xml`, `preview_scrim_bottom.xml` — gradient scrim.
- `app/build.gradle.kts` — konfigurasi build, flavor, signing, `devAbi`.

## Yang Harus Dihindari

- Membuat layar, tombol, atau alur baru yang tidak ada di Yōkai.
- Menghapus fitur/opsi hanya karena "tidak dipakai" tanpa instruksi user.
- Commit besar berisi banyak topik sekaligus.
- Menambah catatan di `todo.md`.
- Push ke `origin` atau memaksa push ke branch mana pun.
- Menyatakan selesai tanpa build + uji di device (untuk perubahan UI).
- Berhenti di tengah pekerjaan multi-step tanpa commit checkpoint — itu memaksa agent berikutnya mencari ulang apa yang sudah diketahui.
