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
3. Build, install ke emulator, dan uji fitur yang berubah dengan gestur adb (lihat bagian **Audit E2E**). Compile sukses bukan bukti fitur berjalan.
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

## Build dan Verifikasi di Emulator (AVD)

Target uji project ini hanya satu: AVD `miyako-dev` — x86_64, API 36, 1080x2400 @420dpi, serial tetap
`emulator-5554`. HP fisik bukan lagi target build maupun audit; jangan pakai angka atau koordinat HP lama.

- `<sdk>` = `C:/Users/surya/AppData/Local/Android/Sdk` (isi `sdk.dir` di `local.properties`).
- adb: `<sdk>/platform-tools/adb.exe` saja. Jangan dicampur `/c/platform-tools/adb.exe` (35.0.2 vs 37.0.1
  → `adb server version doesn't match`, device hilang-timbul). Sudah terlanjur bentrok: `adb kill-server`.
- Start: `<sdk>/emulator/emulator.exe -avd miyako-dev -gpu host`. Proses GUI — jalankan detached, jangan
  ditunggu di shell foreground. Keluar `FATAL: Running multiple emulators with the same AVD` berarti
  instance lama masih hidup: cek `adb devices` dulu, jangan start ulang.
- Siap: `adb wait-for-device`, lalu `adb shell getprop sys.boot_completed` = `1`. Boot dari snapshot ±45 detik,
  `adb install -r` APK 39 MB ±6 detik.
- Hentikan dengan `adb -s emulator-5554 emu kill` (jangan `taskkill`) supaya snapshot ikut tersimpan —
  state app seperti history baca chapter ikut kembali di boot berikutnya. `taskkill` memutus tanpa save,
  dan `-no-snapshot-save` membuat sesi itu hilang lagi.
- Instance AVD bisa mati sendiri ketika build Gradle berat berjalan bersamaan (pernah terjadi di sesi ini).
  Jangan telusuri sebabnya dulu: cek `adb devices` sebelum install, start ulang, lanjut.

Build debug (bukan `assembleStandardRelease`). ABI **wajib** sama dengan `abi.type` AVD; APK arm64-v8a
ditolak emulator dengan `INSTALL_FAILED_NO_MATCHING_ABIS`. Build pertama untuk ABI baru itu lambat —
terukur 6m22s waktu AVD-nya juga jalan — bukan 1-2 menit seperti incremental ABI yang sudah pernah dipakai:

```sh
./gradlew :app:assembleStandardDebug -PdevAbi=x86_64 --console=plain -x lint

# Output
app/build/outputs/apk/standard/debug/app-standard-x86_64-debug.apk
```

- Debug sudah di-sign otomatis dengan debug keystore — langsung `adb install -r`, tanpa zipalign/apksigner.
- ApplicationId debug: `eu.kanade.tachiyomi.debugYokai`; launcher: `eu.kanade.tachiyomi.debugYokai/.MainActivityDefaultDummy`. Debug dan rilis bisa terpasang berdampingan.
- Install: `adb -s emulator-5554 install -r <apk>`.
- Launch: `adb -s emulator-5554 shell am start -n eu.kanade.tachiyomi.debugYokai/.MainActivityDefaultDummy`.
- Screenshot: `adb -s emulator-5554 exec-out screencap -p > C:/tmp/foo.png`, lalu baca dengan tool Read.
- Gestur: `adb -s emulator-5554 shell input swipe x1 y1 x2 y2 <ms>`, `input tap x y`,
  `input keyevent KEYCODE_BACK`. Ambil koordinat dari screenshot emulator, bukan dari ingatan HP.
- `assembleStandardRelease` hanya untuk memverifikasi build rilis (minify/shrink). Output: `app/build/outputs/apk/standard/release/app-standard-<abi>-release-unsigned.apk`.
- APK release tidak di-sign otomatis: `zipalign -f 4` lalu `apksigner.bat sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --out ...`.
- `apksigner` hanya tersedia sebagai `apksigner.bat` di build-tools; gunakan file `.bat`.
- Pakai path `C:/tmp/...` untuk intermediate APK (bukan `/tmp`, zipalign gagal di sana).
- Jangan menambah `Log.d` untuk mengaudit: diagnostik preview sudah dibuang dan audit dibaca dari perilaku
  di layar plus `FATAL` / buffer crash, bukan dari tag log.

### Workflow PowerShell

Gunakan script di root project untuk development tanpa Android Studio:

```powershell
.\build.ps1
```

`build.ps1` hanya melakukan build debug secara incremental dan menghasilkan APK. Script ini tidak
menyalakan emulator, meng-install APK, atau membuka aplikasi.

Workflow lengkap dijalankan secara terpisah:

```powershell
.\start.ps1
.\build.ps1
.\install.ps1
.\launch.ps1
```

Script terpisah tetap tersedia bila hanya membutuhkan satu langkah:

```powershell
.\start.ps1
.\install.ps1
.\launch.ps1
.\stop.ps1
```

`start.ps1` aman dijalankan ketika emulator sudah aktif karena hanya menunggu emulator siap.

## Audit E2E

Setiap perubahan fitur/UI wajib melewati audit end-to-end di emulator sebelum dilaporkan selesai. Urutannya:

1. Pastikan AVD jalan dan sudah boot, lalu build + install build debug terbaru (lihat bagian sebelumnya).
2. Bersihkan logcat, lalu launch app:

   ```sh
   adb -s emulator-5554 logcat -c
   adb -s emulator-5554 shell am start -n eu.kanade.tachiyomi.debugYokai/.MainActivityDefaultDummy
   ```

3. Uji golden path **plus edge case** fitur yang berubah. Langkah konkret tiap topik tidak ditulis di
   sini — dia hidup di bagian "Next action" file status topik itu, dalam bentuk daftar bernomor.
4. Cek crash dan error:
   - `adb -s emulator-5554 logcat -d | grep -c FATAL` harus `0`.
   - Buffer crash harus kosong: `adb -s emulator-5554 logcat -d -b crash`.
5. Regression check di sekitar fitur yang menyentuh file yang sama (navigasi back, gesture list, tema).
6. Baru setelah lolos audit: commit per topik, check off todo, lalu push.

Aturan audit:

- Compile sukses bukan bukti fitur benar; perubahan UI wajib diuji di emulator.
- Batas sesi audit — siapa yang menyetir dan seberapa banyak gestur: lihat **Audit di Emulator: Driver dan Batasnya**.
- Hasil audit dilaporkan di akhir sesi — **bukan** ditulis sebagai catatan di `todo.md`.
- Perubahan non-UI (build/dependency) cukup: build → install → launch → logcat bersih → tidak ada regresi saat startup.

## Audit di Emulator: Driver dan Batasnya

Sejak target uji pindah ke AVD, **agent yang menyetir layar** lewat `adb shell input` + `screencap`;
tidak ada lagi perangkat yang harus diperebutkan. Yang membuat sesi audit mati tetap sama: screenshot,
output gradle, dan dump logcat menumpuk di satu thread yang tidak bisa dibuang. Karena itu batasnya
dipasang di sisi agent:

- Daftar uji ditulis sebagai daftar bernomor dengan hasil yang diharapkan, **di file status topik**,
  bukan di prompt atau chat.
- Satu ronde audit = build → install → launch → cek foreground → **maksimal 7 gestur** → commit checkpoint.
  Thread yang sudah panjang tidak diselamatkan dengan pesan lebih pendek; ronde berikutnya di thread baru.
- Bukti teks lebih dulu daripada tangkapan layar: baris database lewat `run-as` + salin DB (app debug
  boleh), `dumpsys`, config, `git show`. Screenshot hanya untuk yang memang hanya bisa dilihat di layar,
  maksimal satu per butir hasil. Gambar adalah biaya konteks termahal di sesi audit.
- Hasil dicatat di file status dengan provenance: `PASS (emulator, YYYY-MM-DD)`, atau
  `PASS (user, YYYY-MM-DD)` kalau user menjalankan sendiri dan melaporkan. `PASS` tanpa penanda siapa
  dianggap belum diuji. Provenance hanya di file status — baris `evidence:` di commit harus tetap
  `verify=<PASS|PENDING|N/A>` polos, kalau tidak hook `commit-msg` menolaknya.
- Audit yang sudah tercatat terverifikasi **tidak diulang**. Butuh angka tambahan? Ambil satu butir
  spesifik, bukan satu sesi uji ulang.
- Daftar uji tidak boleh bertambah saat satu bug belum terjawab; tiap ronde baru harus didahului satu
  pertanyaan kode yang dijawab dari sumber, bukan dari layar.
- Jangan merebut emulator saat user sedang memakainya — tanya dulu apakah AVD sedang dipakai, sebelum
  menjalankan gestur apa pun.

## Checkpoint dan Oper Antar Agent

Pekerjaan multi-step harus bisa diambil alih agent lain tanpa membaca log chat.

- Satu baris bukti di body **setiap commit yang mengubah kode**, format tetap:

  ```
  evidence: build=<ok|fail|n/a> | verify=<PASS|PENDING|N/A> | next=<satu langkah berikutnya>
  ```

  `verify` = hasil uji yang relevan dengan perubahannya: audit emulator untuk UI, test atau pengukuran untuk non-UI, `N/A` untuk dokumen.
- `PENDING` artinya kode berubah dan build lolos, **tetapi belum diuji**. Itu bukan status selesai. Checkbox `todo.md` hanya untuk yang `PASS`.
- Untuk pekerjaan lebih dari satu commit, jaga satu file status per topik (mis. `docs/preview-checkpoint.md`): tabel langkah → commit → build → verify, diperbarui di commit yang sama dengan kodenya — jangan menyusul.
- Kalau budget atau sisa waktu menipis: tulis checkpoint dan commit **lebih dulu**, baru berhenti.
- Agent baru mulai dari: file status topik → `git log --oneline -5` → bagian "Next action" di file itu. Jangan mengulang diagnosis yang sudah tercatat di sana, dan jangan membuka file sumber besar sebagai pemanasan — pertanyaan struktur dijawab `codebase-memory` atau subagent yang mengembalikan path + nama simbol.
- **Prompt oper maksimal ~10 baris**: tunjuk file status plus satu langkah berikutnya. Detail uji (daftar periksa, perintah, pohon keputusan) hidup di file status. Yang membunuh agent konteks penuh biasanya bukan kurang instruksi, melainkan instruksi yang sama disalin di prompt + plan + file status.
- **Satu langkah = satu thread baru.** Thread yang sudah melempar "context too long" tidak bisa diselamatkan dengan pesan yang lebih pendek; mulai thread bersih dan arahkan ke file status.
- **Satu percobaan perbaikan per penyebab yang sudah terbukti.** Perbaikan yang ditulis tanpa penyebab yang
  terbukti adalah tebakan, dan tebakan beruntun di file yang sama adalah pola yang membuat topik ini mati
  tujuh ronde (lihat `docs/preview-checkpoint.md`).
- **Aturan berhenti 2 ronde.** Gejala yang sama masih gagal setelah 2 perbaikan di emulator → berhenti: tulis
  apa yang sudah tersingkir dan apa pertanyaan kode yang tersisa di file status, commit, serahkan ke user.
  Selama satu bug belum terjawab, daftar uji tidak boleh melebar (lihat **Audit di Emulator: Driver dan Batasnya**).
- **Agent menilai sendiri kapan harus berhenti.** Kalau sisa sesi cuma akan menghasilkan varian perbaikan
  keempat dari ide yang sama, checkpoint + berhenti jauh lebih berguna daripada commit lagi.
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
- Nomor baris di dokumen dan prompt bergeser setiap kali file diedit; rujuk nama simbol, bukan nomor baris. Rujuk commit dengan hash, bukan `git log --grep '<subject>'` — subject bisa tidak cocok dan pencariannya jadi jalan buntu. Jangan tempel screenshot panjang beruntun: satu gambar per pertanyaan.

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
- Menyatakan selesai tanpa build + uji di emulator (untuk perubahan UI).
- Berhenti di tengah pekerjaan multi-step tanpa commit checkpoint — itu memaksa agent berikutnya mencari ulang apa yang sudah diketahui.
