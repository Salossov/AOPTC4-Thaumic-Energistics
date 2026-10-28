# Branding source

`logo.svg` is the editable vector source for both the NeoForge mod logo and the AOP UI settings icon. It has a transparent background and contains no embedded bitmap.

Export with ImageMagick built with RSVG support, from the repository root (replace `<mod-id>` with `forbidden` or `thaumicenergistics`):

```powershell
magick -background none assets/branding/logo.svg -resize 512x512 PNG32:src/main/resources/logo.png
Copy-Item src/main/resources/logo.png src/main/resources/assets/aop_ui/textures/branding/<mod-id>.png
magick -background none assets/branding/logo.svg -resize 64x64 PNG32:src/main/resources/assets/aop_ui/textures/branding/items/<mod-id>.png
```

Commit the SVG and all three transparent PNG exports together. The game loads PNG textures; the public README displays the SVG. See the root NOTICE.md for artwork attribution.
