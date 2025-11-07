# Subtitle Debugging Guide

## Comprehensive Logging Added

The system now logs detailed information at every step of subtitle generation to help diagnose why subtitles might not appear.

## Log Flow

### 1. VideoController - Template Scenes
```
📋 Template has X scenes for subtitle generation
  Scene 1: Y subtitle segments
    - "泉州想贴车衣的直接来" (0ms - 5000ms)
    - "专业贴膜服务" (5000ms - 10000ms)
  Scene 2: Z subtitle segments
    ...
```

**What to check:**
- ✅ Are there scenes in the template?
- ✅ Do scenes have subtitle segments?
- ✅ Are the subtitle texts correct?
- ✅ Are the timings reasonable (start < end)?

### 2. VideoCompilationServiceImpl - Scene Processing
```
[Compile] 📋 Generating SRT from X scenes
[Compile]   Scene 1: Y subtitle segments
[Compile]   Scene 2: Z subtitle segments
[Compile] ✅ Generated SRT file: /tmp/subtitles_xxx.srt
```

**What to check:**
- ✅ Are scenes being passed to compilation?
- ✅ Do scenes still have subtitle segments?
- ✅ Was SRT file generated?

**If you see:**
```
[Compile] ⚠️ No SRT file generated:
[Compile]   - subtitleOptions: null
[Compile]   - scenes: null
```
This means either subtitle options or scenes are missing!

### 3. SubtitleBurningService - SRT Content
```
✅ Generated SRT file: /tmp/subtitles_xxx.srt with Y subtitle entries
📝 SRT Content:
1
00:00:00,000 --> 00:00:05,000
泉州想贴车衣的直接来

2
00:00:05,000 --> 00:00:10,000
专业贴膜服务
```

**What to check:**
- ✅ Is the SRT file content correct?
- ✅ Are timings in correct format?
- ✅ Is the text readable?
- ✅ Are there any encoding issues?

### 4. VideoCompilationServiceImpl - FFmpeg Filter
```
[Compile] 📝 Subtitle filter: subtitles=/tmp/subtitles_xxx.srt:force_style='FontSize=32,PrimaryColour=&H00FFFFFF,...'
[Compile] FFmpeg command: ffmpeg -y -f concat -safe 0 -i /tmp/concat-xxx.txt -filter_complex [0:v]subtitles=...
```

**What to check:**
- ✅ Is the subtitle filter being applied?
- ✅ Is the SRT path correct?
- ✅ Are the style parameters correct?
- ✅ Is the alignment set correctly (8 = top center)?

### 5. FFmpeg Execution
```
[Compile] ✅ FFmpeg completed successfully
[Compile] ✅ Compiled video with subtitles uploaded: https://...
```

**What to check:**
- ✅ Did FFmpeg complete without errors?
- ✅ Was the video uploaded successfully?

## Common Issues and Solutions

### Issue 1: No Subtitle Segments in Template

**Symptoms:**
```
📋 Template has 1 scenes for subtitle generation
  Scene 1: 0 subtitle segments
```

**Cause:** Template scenes don't have subtitle segments populated.

**Solution:** 
- Check if subtitles were extracted during template creation
- Verify Azure Video Indexer returned transcript/OCR
- Check if subtitle segments were saved to template

### Issue 2: Empty SRT File

**Symptoms:**
```
✅ Generated SRT file: /tmp/subtitles_xxx.srt with 0 subtitle entries
📝 SRT Content:
(empty)
```

**Cause:** Scenes have no subtitle segments or segments are empty.

**Solution:**
- Check template creation logs
- Verify subtitle extraction worked
- Check if segments have text content

### Issue 3: Wrong Timing

**Symptoms:**
Subtitles appear but at wrong time or not at all.

**Example:**
```
1
00:00:00,000 --> 00:00:00,000  ← Start = End (won't show!)
泉州想贴车衣的直接来
```

**Solution:**
- Verify subtitle segment timings are correct
- Ensure start < end
- Check if timings match video duration

### Issue 4: Invisible Subtitles

**Symptoms:**
FFmpeg succeeds but subtitles not visible in video.

**Possible causes:**
1. **Color mismatch** - White text on white background
2. **Font size too small** - Can't see on mobile
3. **Wrong position** - Off-screen or covered
4. **Encoding issue** - Special characters not rendered

**Solution:**
- Check subtitle styling in logs
- Verify color contrast (white text + black outline + black box)
- Confirm alignment (8 = top center)
- Test with simple ASCII text first

### Issue 5: FFmpeg Filter Error

**Symptoms:**
```
[Compile] FFmpeg failed with exit code 1
[Compile] FFmpeg output:
Error: Invalid subtitle file format
```

**Cause:** SRT file format is incorrect.

**Solution:**
- Check SRT content in logs
- Verify format matches SRT specification
- Check for encoding issues (UTF-8)
- Verify file path is accessible

## Debugging Checklist

When subtitles don't appear, check logs in this order:

1. ✅ **Template has scenes?**
   - Look for: `📋 Template has X scenes`
   - If 0 scenes → Template creation issue

2. ✅ **Scenes have subtitle segments?**
   - Look for: `Scene 1: Y subtitle segments`
   - If 0 segments → Subtitle extraction issue

3. ✅ **Subtitle text is correct?**
   - Look for: `- "text" (start - end)`
   - Check text content and timing

4. ✅ **SRT file generated?**
   - Look for: `✅ Generated SRT file`
   - Check SRT content in logs

5. ✅ **FFmpeg filter applied?**
   - Look for: `📝 Subtitle filter:`
   - Verify filter parameters

6. ✅ **FFmpeg succeeded?**
   - Look for: `✅ FFmpeg completed successfully`
   - Check for error messages

7. ✅ **Video uploaded?**
   - Look for: `✅ Compiled video with subtitles uploaded`
   - Verify URL is accessible

## Testing Recommendations

### Test 1: Simple Subtitle
Create a template with one scene and one subtitle:
```
Scene 1:
  - "Test" (0ms - 5000ms)
```

Expected: "Test" appears at top center for 5 seconds.

### Test 2: Multiple Subtitles
Create a template with multiple subtitles:
```
Scene 1:
  - "First" (0ms - 3000ms)
  - "Second" (3000ms - 6000ms)
```

Expected: "First" for 3s, then "Second" for 3s.

### Test 3: Chinese Characters
Test with Chinese text:
```
Scene 1:
  - "泉州想贴车衣的直接来" (0ms - 5000ms)
```

Expected: Chinese text renders correctly.

### Test 4: Long Text
Test with long subtitle:
```
Scene 1:
  - "This is a very long subtitle that should wrap to multiple lines" (0ms - 5000ms)
```

Expected: Text wraps properly, still readable.

## Log Locations

### Development
- Console output (stdout)
- Application logs

### Production (Render)
- Render dashboard → Logs tab
- Real-time log streaming
- Search for keywords: `📋`, `✅`, `⚠️`, `[Compile]`

## Quick Diagnosis

Copy this checklist and fill in from logs:

```
[ ] Template has scenes: ___
[ ] Scenes have segments: ___
[ ] Segment text: "___"
[ ] Segment timing: ___ - ___
[ ] SRT file generated: ___
[ ] SRT entries: ___
[ ] FFmpeg filter: ___
[ ] FFmpeg success: ___
[ ] Video uploaded: ___
```

If any checkbox is empty or shows 0/null, that's where the problem is!
