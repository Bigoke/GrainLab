# GrainLab

An Android photo editor and camera with classic film looks, one-tap auto tools and reusable edit templates.

**Download the latest APK:** https://github.com/Bigoke/GrainLab/releases/latest/download/GrainLab.apk

## Features

- **60+ film looks** in packs: Kodak, Fujifilm, Agfa, Ilford, Polaroid, Hasselblad, Urban, Winter and Vintage, each with a strength slider
- **Auto tools:** Auto fix (exposure, levels, white balance), Auto align (horizon straightening and vertical keystone), Auto noise reduction
- **Adjustments:** exposure, contrast, highlights, shadows, whites, blacks, fade, temperature, tint, vibrance, saturation, HSL per colour, split-tone grading, texture, clarity, dehaze, sharpening, noise reduction, grain, vignette, halation, light leaks, borders, date stamp
- **Crop and geometry:** rotate, flip, ratio crops, straighten, vertical and horizontal perspective
- **Templates:** save any edit, mark favourites, rename, share as a file, import
- **Camera:** live film look or template in the viewfinder, flash, timer, grid, 4:3/16:9, tap to focus, pinch zoom, exposure, optional original copy
- **Camcorder:** HD / Full HD / 4K, pause and resume; the look is baked into the saved video
- **Batch edit:** apply a template or film look to up to 50 photos
- Non-destructive editing with undo/redo, a before/after split slider and hold-to-compare; export as JPEG, PNG or WebP

## How it works

Every colour and tone setting is baked into one 33×33×33 lookup table. The same table drives the editor preview,
the live camera (OpenGL ES 3 shader) and recorded video (Media3 Transformer), so a look matches everywhere.
Detail effects (sharpening, clarity, noise reduction, grain, halation, vignette, light leaks) run in the shader.

Photos are saved to `Pictures/GrainLab`, videos to `Movies/GrainLab`.

## Build

Pushing to `main` builds a debug APK with GitHub Actions and publishes it at the link above.
Locally: `./gradlew assembleDebug` (JDK 17, Android SDK 35).
