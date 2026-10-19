# 00 — Sintesis Video Referensi & Arah Desain Zendo Launcher

Tanggal: 19 Oktober 2026 (sesi analisa otonom)
Metode: 5 video YouTube → ekstraksi frame (tool `youtube-frames`) → analisa visual per frame + subtitle → laporan per video di folder ini → sintesis lintas video.

## 1. Video yang dianalisa

| # | ID | Judul | Channel | Durasi | Laporan |
|---|-----|-------|---------|--------|---------|
| 1 | `oUoxlF_s7wk` | This Video Will Save You 9+ Years Of Your Life In 6 Minutes | Maxwell Rhoe | 6:22 | `oUoxlF_s7wk-save-9-years.md` |
| 2 | `7jVb1lLniEw` | how I reduced my screentime by 80% (guide) | Reysu | 10:34 | `7jVb1lLniEw-reduced-screentime-80.md` |
| 3 | `krityj3ITVE` | my anti-brainrot iphone setup | unwiredo | 8:46 | `krityj3ITVE-anti-brainrot-setup.md` |
| 4 | `iCUB7JZmmig` | I Turned My iPhone Into an AI Productivity Machine. | Better Creating | 19:07 | `iCUB7JZmmig-ai-productivity-machine.md` |
| 5 | `7LPEVfMaajU` | my maximalist iPhone setup in 2026 | Reysu | 21:22 | `7LPEVfMaajU-maximalist-setup-2026.md` |

## 2. Pola yang berulang (konvergen) di 5 video

| Pola | Bukti | Implikasi untuk launcher |
|------|-------|--------------------------|
| **Home kosong = ketenangan** | v1: home kosong + dock 4 ikon + pill Search; v3: 1 halaman, 0 ikon di home, hanya dock; v5: halaman widget-only dengan ruang kosong besar | Default home harus bisa mendekati kosong; app masuk drawer, bukan auto-pin |
| **Teks sebagai UI** | v2 (Dumbify): daftar huruf kecil satu kolom di atas hitam polos, tanpa ikon/dock/badge | Mode text-list sudah ada (LayoutMode.TEXT) → poles tipografi & lowercase |
| **Ikon monokrom / tinted** | v3: iOS 18 Tinted + eyedropper (hasil hitam-putih); v4: icon pack monokrom buatan sendiri; v5: 2 ikon custom mono | Monochrome sudah jadi identitas Zendo → tambah mode Tinted + match wallpaper |
| **Wallpaper = palet + suasana** | v3: line-art manga B/W; v5: near-black bokeh #0C0A09–#1A120B; v4: bokeh hijau-kelabu | Wallpaper sebagai backdrop opsional + sumber warna aksen |
| **Widget anchor 2×2 kiri-atas** | v5: habit matrix pada slot 2×2 = anchor visual, konsisten antar frame | Slot widget wellbeing di halaman Today |
| **Search sebagai aksi first-class** | v1: pill Search di atas dock; v5: search = permukaan kontrol | Pill search di home |
| **Grayscale / peredam warna** | v1: Color Filters → grayscale; v2: demo full grayscale "layar terasa tidak menarik" | Deep-link ke pengaturan grayscale sistem |
| **Density dibayar dengan mekanisme, bukan visual** | v5: auto-tick habit via automation, feed dipotong di sumber (RSS/browser) | Wellbeing mechanism > gimmick visual; Pause/AccessWindow sudah ada |
| **Friction sengaja** | v3: install-gate (App Store dimatikan + App Limits); v2: jadwal blokir; v1: uninstall, bukan limit | Install-gate + interstitial niat = backlog prioritas tinggi |
| **Craft = tipografi + ruang + satu aksen** | v4: "Apple Swiss", hitam-putih + aksen oranye; v5: cream/gold monokrom | Satu aksen warna saja, sisanya netral |

## 3. Arah desain: "Calm Canvas"

Tesis: **default kalem, craft opsional.** Tiga lapis:

1. **Fondasi kalem** — home = clock + ruang kosong + chrome minimal; semua app di drawer; nol badge warna; hairline sebagai satu-satunya pemisah. (Sudah jadi DNA Zendo.)
2. **Craft opsional** — user yang mau "cantik" menyalakan: ikon Tinted + accent, wallpaper di belakang home dengan scrim, dock pin, search pill. Semua opt-in, default tetap mono polos.
3. **Mekanisme wellbeing** — Intentional Pause, AccessWindow, fav-pin per halaman (sudah ada); lanjut ke install-gate, quiet stack, widget habit.

## 4. Yang diimplementasikan di sesi ini (F1–F6)

Detail lengkap: `01-implementation.md`.

| Fitur | Pola video | Status |
|-------|-----------|--------|
| F1 Ikon 3 mode: Monochrome / Tinted (6 swatch + match wallpaper) / Color | v3, v4 | ✅ |
| F2 Home backdrop: wallpaper template + scrim legibilitas | v3, v5 | ✅ |
| F3 Search pill di halaman Today → All apps + keyboard | v1, v5 | ✅ |
| F4 Dock row (pinned app, ikon-only, cap 5) di halaman Today | v1, v3 | ✅ |
| F5 Deep-link "Display & grayscale" | v1, v2 | ✅ |
| F6 Tests + screenshot harness + docs | — | ✅ |

Settings keys: `icon_style`, `icon_tint`, `icon_tint_match_wallpaper`, `home_backdrop` (+ migrate `monochrome_icons`).

## 5. Backlog terprioritas (belum dikerjakan)

| P | Ide | Dari | Effort |
|---|-----|------|--------|
| P1 | Quiet Stack / triage notifikasi (Interrupt / Badge / Silent per-app, default badge) | v1, v4 | M |
| P1 | Install-gate: friction sebelum buka Play Store / installer | v3 | M |
| P1 | Widget anchor 2×2 di Today (habit/task matrix interaktif) | v5 | M |
| P2 | Mode text-list lowercase (opsi kapitalisasi label) | v2 | S |
| P2 | Folder / row-as-category di layar apps + overlay blur wallpaper | v5 | M |
| P2 | Dashboard page = widget-only + empty space (PageType.WIDGET sudah ada, belum terpakai) | v5 | S |
| P3 | "Dispatch, not launch": long-press → intent/ketik, kartu antrian task | v4 | L |
| P3 | Screen-time driven constraint ratchet (tawarkan pengetatan saat usage naik) | v2, v3 | L |

## 6. Verifikasi

- `& ./gradlew.bat :app:testDebugUnitTest --console=plain` — 250 test / 31 kelas, 0 gagal (final, setelah fix round review).
- Screenshot: `app/build/screenshots/calm-home-*.png` (Robolectric NATIVE, 1080×2400).
- Regenerasi: `& ./gradlew.bat :app:testDebugUnitTest --tests "com.zendolauncher.ui.ScreenshotCaptureTest" --rerun`
