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
- Jangan menambah `Log.d` untuk mengaudit: diagnostik preview sudah dibuang dan audit dibaca dari perilaku
  di layar plus `FATAL` / buffer crash, bukan dari tag log.
- Jangan menguji berlebihan saat user sedang memakai HP; ambil snapshot singkat dan terkontrol.

## Audit E2E

Setiap perubahan fitur/UI wajib melewati audit end-to-end di device fisik sebelum dilaporkan selesai. Urutannya:

1. Build + install build debug terbaru (lihat bagian sebelumnya).
2. Bersihkan logcat, lalu launch app:

   ```sh
   adb -s d89608bf logcat -c
   adb -s d89608bf shell am start -n eu.kanade.tachiyomi.debugYokai/.MainActivityDefaultDummy
   ```

3. Uji golden path **plus edge case** fitur yang berubah. Langkah konkret tiap topik tidak ditulis di
   sini — dia hidup di bagian "Next action" file status topik itu, dalam bentuk daftar bernomor.
4. Cek crash dan error:
   - `adb -s d89608bf logcat -d | grep -c FATAL` harus `0`.
   - Buffer crash harus kosong: `adb -s d89608bf logcat -d -b crash`.
5. Regression check di sekitar fitur yang menyentuh file yang sama (navigasi back, gesture list, tema).
6. Baru setelah lolos audit: commit per topik, check off todo, lalu push.

Aturan audit:

- Compile sukses bukan bukti fitur benar; perubahan UI wajib diuji di device.
- Jangan menguji berlebihan saat user sedang memakai HP.
- Siapa yang memegang HP dan siapa yang membaca log: lihat bagian **Pembagian Kerja: User dan Agent**.
- Hasil audit dilaporkan di akhir sesi — **bukan** ditulis sebagai catatan di `todo.md`.
- Perubahan non-UI (build/dependency) cukup: build → install → launch → logcat bersih → tidak ada regresi saat startup.

## Pembagian Kerja: User dan Agent (Audit Device)

Kesepakatan: **user yang menguji di HP, agent yang menguji di teks dan di kode.** Sesi audit yang
menyerahkan seluruhnya ke agent sudah tiga kali mati kehabisan konteks — bukan karena instruksi kurang,
tetapi karena screenshot, output gradle, dan dump logcat menumpuk di satu thread yang tidak bisa dibuang.

- Agent menulis langkah uji sebagai daftar bernomor dengan hasil yang diharapkan, **di file status
  topik**, bukan di prompt atau chat.
- Agent hanya melakukan yang berupa baris teks: build, install, `logcat -c`, `logcat -d | grep -c FATAL`,
  `logcat -d -b crash`. Tidak ada gestur layar di sisi agent kecuali user menyatakan tidak bisa pegang HP.
- User menjalankan daftar itu dan mengirim balik **satu baris per butir**: `T2 FAIL — header Ch. 2,
  thumbnail tetap kosong sampai digeser`. Hanya apa yang terlihat; tanpa dugaan penyebab.
- Agent mencatat hasil dengan provenance di file status: `PASS (user, YYYY-MM-DD)`. `PASS` tanpa
  penanda siapa dianggap belum diuji. Provenance hanya di file status — baris `evidence:` di commit harus
  tetap `verify=<PASS|PENDING|N/A>` polos, kalau tidak hook `commit-msg` menolaknya.
- Audit yang sudah tercatat terverifikasi **tidak diulang**. Butuh angka tambahan? Minta satu butir
  spesifik, bukan satu sesi uji ulang.
- Screenshot dibaca maksimal satu per butir, dan hanya kalau laporan user ambigu. Gambar adalah biaya
  konteks termahal di sesi audit.
- Kalau user berhalangan: agent boleh mengaudit sendiri dengan batas tegas — build → install → cek
  foreground → maksimal 7 interaksi → selesai, lalu commit checkpoint. Mode ini lambat dan boros; bukan default.

## Checkpoint dan Oper Antar Agent

Pekerjaan multi-step harus bisa diambil alih agent lain tanpa membaca log chat.

- Satu baris bukti di body **setiap commit yang mengubah kode**, format tetap:

  ```
  evidence: build=<ok|fail|n/a> | verify=<PASS|PENDING|N/A> | next=<satu langkah berikutnya>
  ```

  `verify` = hasil uji yang relevan dengan perubahannya: audit device untuk UI, test atau pengukuran untuk non-UI, `N/A` untuk dokumen.
- `PENDING` artinya kode berubah dan build lolos, **tetapi belum diuji**. Itu bukan status selesai. Checkbox `todo.md` hanya untuk yang `PASS`.
- Untuk pekerjaan lebih dari satu commit, jaga satu file status per topik (mis. `docs/preview-checkpoint.md`): tabel langkah → commit → build → verify, diperbarui di commit yang sama dengan kodenya — jangan menyusul.
- Kalau budget atau sisa waktu menipis: tulis checkpoint dan commit **lebih dulu**, baru berhenti.
- Agent baru mulai dari: file status topik → `git log --oneline -5` → bagian "Next action" di file itu. Jangan mengulang diagnosis yang sudah tercatat di sana, dan jangan membuka file sumber besar sebagai pemanasan — pertanyaan struktur dijawab `codebase-memory` atau subagent yang mengembalikan path + nama simbol.
- **Prompt oper maksimal ~10 baris**: tunjuk file status plus satu langkah berikutnya. Detail uji (daftar periksa, perintah, pohon keputusan) hidup di file status. Yang membunuh agent konteks penuh biasanya bukan kurang instruksi, melainkan instruksi yang sama disalin di prompt + plan + file status.
- **Satu langkah = satu thread baru.** Thread yang sudah melempar "context too long" tidak bisa diselamatkan dengan pesan yang lebih pendek; mulai thread bersih dan arahkan ke file status.
- File yang otomatis termuat tiap sesi (`AGENTS.md`, file status topik) adalah **biaya tetap** untuk agent berikutnya, bukan arsip. Jaga file status ±100 baris; narasi audit lama dipadatkan dan isi lengkapnya ditunjuk lewat `git show <commit>:<file>`.
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

- **Read-only.** Jangan pernah mengedit, membangun, atau commit di dalam tiga direktori itu.
- `Grep` dengan `path` ke direktori target (mis. `path: D:/coding/Usagi/app`), lalu `Read` pakai `offset`/`limit`. Jangan `find` atau `du` dari root parent — pohonnya besar dan pernah membuat command timeout.
- Kutip `path` + nama simbol + HEAD yang dibaca di laporan atau body commit; jangan menyalin file utuh ke repo ini, dan checkout itu bisa tertinggal dari upstream. Mengadopsi perilaku dari sana tetap harus lolos aturan fork: UI/UX Yōkai tidak berubah, fitur tidak dibuang.

## Catatan Git Bash (Windows)

- Set `export MSYS_NO_PATHCONV=1` sebelum perintah adb yang memakai path di dalam device.
- Jangan background chain dengan `&`; environment variable (mis. `ADB=...`) hilang di shell baru. Jalankan assignment dan perintah di satu shell foreground.
- Staging per-hunk bisa memakai `awk` (`RS="\n@@ "`) + `git apply --cached` untuk memisahkan commit logis.

## Peta Kode Kunci

- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsController.kt` — gesture halaman detail, panel preview kanan, scrim, magnet swipe, hint sekali.
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsAdapter.kt` — grid halaman per chapter, marker halaman terakhir dibaca, lazy load thumbnail.
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
