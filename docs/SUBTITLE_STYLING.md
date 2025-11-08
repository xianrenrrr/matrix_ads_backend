# Subtitle Styling Guide

## Current Enhanced Styling (v2.0)

### Default Settings
```java
fontSize = 32              // Larger for better visibility
textColor = "#FFFFFF"      // White text
outlineColor = "#000000"   // Black outline
outlineWidth = 3           // Thick outline for contrast
backgroundColor = "#000000C0"  // Semi-transparent black box (75% opacity)
bold = true                // Bold text for emphasis
alignment = 2              // Bottom center
```

### Visual Appearance
```
┌─────────────────────────────────────────┐
│                                         │
│         [Video Content]                 │
│                                         │
│                                         │
│    ┌───────────────────────────┐       │
│    │  泉州想贴车衣的直接来     │       │ ← Semi-transparent black box
│    └───────────────────────────┘       │
└─────────────────────────────────────────┘
     ↑
     White text with black outline
```

## Improvements from v1.0

| Feature | v1.0 (Old) | v2.0 (New) | Benefit |
|---------|------------|------------|---------|
| Font Size | 24 | 32 | 33% larger, easier to read |
| Outline Width | 2px | 3px | Better contrast |
| Bold | false | true | More prominent |
| Background | None | Semi-transparent black | Always visible on any background |
| Border Style | Default | Box (4) | Clean background box |

## Why These Changes?

### Problem with v1.0
- Small font (24px) hard to read on mobile
- Thin outline (2px) insufficient contrast
- No background box - invisible on white/bright backgrounds
- Not bold - text looked thin

### Solution in v2.0
- ✅ **Larger font (32px)** - Readable on all devices
- ✅ **Thicker outline (3px)** - Strong contrast
- ✅ **Bold text** - More prominent and professional
- ✅ **Semi-transparent black box** - Always visible regardless of video content
- ✅ **Box border style** - Clean, professional appearance

## Customization Options

Users can customize via API parameters:

```
POST /content-manager/videos/{videoId}/publish
  ?subtitleColor=#FFFFFF      (hex color)
  &subtitleSize=32             (font size in pixels)
  &subtitlePosition=center     (left|center|right)
```

### Position Mapping (Simplified)

**All positions are centered horizontally:**
- `top` → Alignment 8 (top center) - **DEFAULT**
- `middle` or `center` → Alignment 5 (middle center)
- `bottom` → Alignment 2 (bottom center)

**Default is `top` (alignment 8) to avoid overlapping with original video subtitles at the bottom.**

### ASS Alignment Reference
```
7  8  9  ← Top row
4  5  6  ← Middle row
1  2  3  ← Bottom row
```
We use: 8 (top center), 5 (middle center), 2 (bottom center)

## FFmpeg Filter Generated

```
subtitles=/tmp/subtitles_xxx.srt:force_style='
  FontSize=32,
  PrimaryColour=&H00FFFFFF,      # White (BGRA format)
  OutlineColour=&HFF000000,      # Black outline
  Outline=3,                      # 3px outline
  BackColour=&HC0000000,         # 75% transparent black
  BorderStyle=4,                  # Box background
  Bold=1,                         # Bold text
  Alignment=2                     # Bottom center
'
```

## Testing Recommendations

### Test on Different Backgrounds
1. ✅ White background (car, wall)
2. ✅ Dark background (night scene)
3. ✅ Busy background (complex scene)
4. ✅ Bright outdoor scene

### Test on Different Devices
1. ✅ Desktop (1920x1080)
2. ✅ Mobile (1080x1920 vertical)
3. ✅ Tablet (1024x768)

### Expected Results
- Subtitles should be clearly visible in ALL scenarios
- Text should be easy to read at normal viewing distance
- Background box should not be too obtrusive
- Outline should provide sufficient contrast

## Handling Videos with Existing Subtitles

### Problem
If the original video already has burned-in subtitles (hardcoded into pixels), both subtitles will be visible:

```
┌─────────────────────────────────────────┐
│    ┌───────────────────────────────┐   │
│    │  泉州想贴车衣的直接来         │   │ ← NEW subtitle (top)
│    └───────────────────────────────┘   │
│                                         │
│         [Video Content]                 │
│                                         │
│         testing  ← Old subtitle         │
│                    (in video pixels)    │
└─────────────────────────────────────────┘
```

### Solution
**Default position changed to TOP CENTER (alignment 8)** to avoid overlapping with original bottom subtitles.

### Why This Works
1. **Original subtitles** - Usually at bottom (standard position)
2. **New subtitles** - Now at top by default
3. **No overlap** - Both visible without conflict
4. **Clear hierarchy** - New subtitles more prominent at top

### If You Want Bottom Position
Use `subtitlePosition=center` parameter to force bottom position:
```
POST /content-manager/videos/{videoId}/publish
  ?subtitlePosition=center  (forces bottom center)
```

**Note:** This may overlap with original subtitles if they exist.

---

## Troubleshooting

### Subtitles Not Visible
1. Check SRT file content (logged in console)
2. Verify timing is correct (start < end)
3. Check FFmpeg filter in logs
4. Verify video was re-encoded (not just copied)

### Subtitles Too Large/Small
- Adjust `subtitleSize` parameter (default: 32)
- Recommended range: 24-48 for 1080p video

### Subtitles Wrong Position
- Check `subtitlePosition` parameter
- Verify alignment value in FFmpeg filter (1=left, 2=center, 3=right)

### Background Box Too Dark/Light
- Adjust `backgroundColor` alpha channel
- Current: `#000000C0` (75% opacity)
- Range: `#00000000` (transparent) to `#000000FF` (opaque)

## Future Enhancements

Potential improvements for future versions:

1. **Multiple subtitle tracks** - Support for multiple languages
2. **Animated subtitles** - Fade in/out effects
3. **Custom fonts** - Allow font family selection
4. **Position presets** - Top, middle, bottom options
5. **Karaoke style** - Word-by-word highlighting
6. **Emoji support** - Render emoji in subtitles

## References

- [FFmpeg Subtitles Filter Documentation](https://ffmpeg.org/ffmpeg-filters.html#subtitles-1)
- [ASS Subtitle Format Specification](http://www.tcax.org/docs/ass-specs.htm)
- [SRT Subtitle Format](https://en.wikipedia.org/wiki/SubRip)
