- [x] improve ui/ux dari grid view karena sangat buruk aku suka impleemtasi dari usagi kamu bisa lihat implementasi dari usagi untuk detail page nya di parent project ini tapi tetap mempertahankan ui/ux dari yokai
- [x] optimization cache and ram you can see the example on usagi apk on this parent project
- [x] Change the menu layout in grid view to resemble a slider—rather than just a static list next to the filters—similar to the design found in the Usagi or TachiyomiSY apps. You can find the repository here: https://github.com/jobobby04/TachiyomiSY
- [x] pastika update todo dan check apa saja yang udah kamu lakukan dan apa harus aku ingatkan lagi jangan membuang
fungsi atau fitur yang sudah ada apa kamu masih belum paham juga? misal nih kenapa sekarang chapter tidak bisa di
slide kanan kiri seperti sebelumnya? coba lihat kode asli jika memungkinkan, juga perbaiki animasi ketika dari
halaman card ke detail terasa berat dan tidak smooth, perpindahan chapter ke pages juga masih terasa melompat
ketika di click, intinya aku mau smooth dan mulus aja lah ga ada yang namanya menyendat nyendat
- [x] selalu lakukan commit ketika ada perubahan pastikan tidak menggabungkan commit menjadi satu commit besar agar kamu bisa melihat lagi ke belakang ketika sesion mu telah habis dan cheklist otomatis di todo jika sudah menyeleseikan perubahan
  - catatan: perubahan dipecah per topik — 529a555d8f (cache gambar + preload), 43e6691e0f (komponen grid halaman + tab slider), 89809f92f3 (wiring detail: grid, swipe baris, smoothing), 67a93cb8bd (docs todo), lalu commit terpisah untuk swipe halaman dan update todo ini.
- [x] gimana kalo halaman pages nya di buat swipe pada halaman detail jadi ketika halaman detail di swipe kanan maka yang tampil adalah halaman pages agar swipe nya tidak bentrok dengan swipe helper
- [x] swipenya masih berantakan dan hanya swipe berpindah pindah saja tidak menentu maksudnya kalo right ya ke right kalo left ya ke left juga sesuaikan ui tombol chapter dan pages dengan ui yokai atau bawaan kalo ga ya hilangin aja button nya mungkin itu adalah awal dari semua masalah beruntuk mulai dari glicth, animasi hilang, hook ketika di scroll dll nya dan ga usah bikinin catatan di todo ga perlu
- [x] improve buat agar waktu proses developmentnya lebih cepat dan tidak memakan cukup banyak waktu
- [x] coba lakukan perbandingan dengan fork asli agar tetap kompatible dengan fork asli dan mencegah lebih banyak muncul nya bug dengan menclone project yang sama https://github.com/null2264/yokai untuk di jadikan acuan
- [x] improve ui/ux swipe nya jelek banget aku mau untuk swipe ke halaman pages hanya unutk halaman pages saja ke keseluruhan bukan menggantikan list chapter yang ada jadi ketika swipe ke kanan yang muncul adalah pages atau bisa dikatakan preview page tiap chapternya
- [x] swipe masih terasa mental dan belum smooth mengikuti ux aplikasi nya
- [x] sejak kapan jadi swipe kiri yang aku lihat sekarang untuk halaman pages buat ke swipe kanan dari pov ku untuk membuka halmaan pages kamu mungkin bisa melihat refrensinya dari https://github.com/jobobby04/TachiyomiSY lihat bagaiman implementasi halaman pages/preview dalam fork ini
git clone terlebih dahulu dan pahami halaman detailnya jika memungkinkan
- [x] buat halaman preview muncul dari kanan yang berarti leftswipe dan berikan tambahan untuk langsung scroll atau menampilkan halaman mana yang terakhir di baca di pages preview
- [x] kasih tau juga ke user kalo halaman detail bisa di swipe dengan memberikan sedikit semacam snapshot seperti Affordance Animation pada saat pertama kali membukanya seperti kalo kamu tau tiktok ketika penggunananya pertama kali membuaka tiktok akan memberi tahu kalo ini tuh bisa di scroll seperti itu animasinya
- [] berikan animasi pada saat scroll di preview ketika menampilkan halaman terakhir yang di baca
- [] untuk scroll ke halaman terakhir yang di baca di preview masih belum berhasil dan selalu tampilkan di posisi atas 
- [] di halaman preview tambahkan semacam efek untuk bagian atas dan bawah tampilan di layar agar bagian atas seperti jam baterai dari hp masih terbaca
- [] pada saat swipe berikan effect magnet agar tidak perlu mencoba swipe berulang2 ketika swipe gagal di lakukan 