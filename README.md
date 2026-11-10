# GrainLab

An Android film camera and photo editor: 60+ classic film looks, pro camera controls and a pro editor with masks, curves and healing. Everything runs on the phone.

**Download the latest APK:** https://github.com/Bigoke/GrainLab/releases/latest/download/GrainLab.apk

## Camera
- Film looks or your own templates live in the viewfinder; swipe on the picture to change look
- Lens buttons (ultra-wide, main, 2x, telephoto) built from the phone's cameras
- PRO mode: manual ISO, shutter speed, focus and white balance; focus peaking, zebras, live histogram, level
- Zero-shutter-lag capture, burst (hold the shutter), night mode (6 frames aligned and averaged)
- Volume buttons as shutter, haptics, shutter sound, timer, grid, optional location
- Video in HD / Full HD / 4K, 24 fps cinematic, stabilisation, pause/resume; the look is baked into the saved clip
- Every setting is remembered

## Editor
- Live GPU preview: sliders respond instantly
- Auto fix, auto align (horizon + keystone), auto noise reduction
- Light, colour, tone curve (RGB/R/G/B), HSL, split-tone grading, texture, clarity, dehaze, sharpening, noise reduction
- Masks: radial, gradient, brush, automatic sky and subject (on-device ML) with their own exposure, contrast, saturation, warmth
- Healing brush, free crop with ratio lock, straighten, perspective
- Grain, halation, vignette, light leaks, borders, date stamp
- Before/after split slider, hold for original, full screen, pinch zoom, clipping warning
- Undo/redo, copy/paste edits, re-edit saved photos from the original, landscape layout
- Export up to full 50 MP resolution, JPEG/PNG/WebP, Instagram 4:5, Story 9:16, WhatsApp, watermark; camera details kept

## Library
- Templates with favourites, previews, file sharing and QR codes (scan to import)
- GrainLab gallery of everything you shot and exported, with favourites, share and delete
- Batch apply a template or look to up to 50 photos

## Film packs
Kodak, Fujifilm, Agfa, Ilford, Polaroid, Hasselblad, Urban, Winter and Vintage. A "brand-free names" option
(camera settings) switches to generic names for publishing.

## How it works
All colour and tone settings are baked into one 33×33×33 half-float lookup table used by the editor preview,
the live camera (OpenGL ES 3 shader) and video (Media3 Transformer), so a look matches everywhere. Detail effects
and masks run in the shader. Large photos are rendered in strips so full sensor resolution fits on the GPU.

## Build
Pushing to `main` runs the unit tests, builds the APK with GitHub Actions and publishes it at the link above.
Builds are signed with a fixed key in the repo, so each new APK installs over the previous one.
Locally: `./gradlew testDebugUnitTest assembleDebug` (JDK 17, Android SDK 35).

Privacy: see [PRIVACY.md](PRIVACY.md).
