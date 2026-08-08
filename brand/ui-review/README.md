# Deckscape UI redesign record

This folder records the image-generation-assisted 1.1 interface redesign at
the target 1920×1080, 240 dpi head-unit profile.

## Files

- `current-overview.png`, `current-gallery.png`, and `current-add-source.png`
  are the 1.0 emulator screenshots supplied as visual references.
- `generated-redesign.png` is the design direction produced with OpenAI's
  built-in image-generation mode.
- `implemented-overview.png`, `implemented-gallery.png`, and
  `implemented-add-source.png` are emulator captures of the native Android
  implementation.
- `implemented-1.2-overview.png`, `implemented-1.2-gallery.png`,
  `implemented-1.2-download-progress.png`, and `implemented-1.2-active.png`
  record the subsequent category-cover, download-progress, and active-state
  refinements at the same target profile.

The screenshots contain public catalog previews only. They were captured in an
isolated emulator and contain no vehicle, account, map, or location data.

The prompt below is retained verbatim as provenance and therefore uses the
application's former HorizonDeck name.

## Exact generation prompt

```text
Use case: ui-mockup
Asset type: shippable Android automotive head-unit app screen, 1920×1080 landscape
Input images: Image 1 is the current HorizonDeck repository overview; Image 2 is the current wallpaper gallery and the primary screen to redesign; Image 3 is the current add-repository dialog and supporting interaction reference.
Primary request: Redesign HorizonDeck into a more polished, professional, simple wallpaper browser for a car head unit while preserving its existing information architecture and functions.
Context: HorizonDeck browses wallpaper images from public GitHub repositories. Repository folders become categories. Users choose a source, browse a four-column wallpaper grid, apply a wallpaper with one touch, add custom repositories, and choose automatic rotation intervals. Target is Android 10, 1920×1080 at 240 dpi, operated at arm’s length while parked. Touch targets must be at least 48 dp and glanceable.
Style/medium: realistic production UI, premium restrained OEM-dashboard feel, not concept art; clean typography, consistent spacing, subtle depth and rounded geometry.
Composition/framing: full-screen landscape UI only, no device frame. Keep a slim source rail on the left and a dominant main gallery. Create a calm top bar with brand, active-wallpaper state, rotation interval, Next, and a primary Activate/Apply action grouped logically. Below it, use breadcrumb/title plus search, then compact horizontally scrollable category chips. Show a four-column grid with large 16:9 wallpaper artwork; overlay or tightly attach the filename, file size, GIF badge when relevant, and a clear one-touch Apply control without wasting vertical space. Integrate refresh and add-source as secondary actions. Make the selected repository and selected category unmistakable. Show a refined custom add-source surface if helpful, but the gallery must remain the primary screen.
Color palette: deep midnight navy and blue-black surfaces, electric cyan primary accent drawn from the existing brand icon, off-white text, muted blue-gray secondary text, restrained coral only for warnings or inactive state.
Text (verbatim where shown): "HorizonDeck", "Wallz", "Abyssal Wave", "Search wallpapers", "1 hour", "Next", "Activate", "Apply", "Add source".
Constraints: preserve practical usability and existing functions; minimize distraction; readable at arm’s length; strong contrast; no tiny labels; no hamburger-only navigation; no car imagery; no BYD marks; no unrelated features; no wallpaper bundled into the visual brand; no glassmorphism haze; no excessive gradients; no watermark; no device mockup; no extra marketing copy.
```

The generated mockup was a design reference, not a runtime asset. The shipped
screen is implemented with native Android views and code-drawn folder icons so
the APK remains small and dependency-free.
