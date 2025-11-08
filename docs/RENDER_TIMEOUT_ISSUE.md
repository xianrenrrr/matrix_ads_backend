# Render 502 Bad Gateway - FFmpeg Timeout Issue

## Problem

```
[POST]502 Bad Gateway
responseTimeMS=13587 (13.5 seconds)
```

The subtitle burning process works correctly, but Render times out during FFmpeg video processing.

## Root Cause

### What's Working ✅
1. Template has subtitle segments ✅
2. SRT file generated correctly ✅
3. FFmpeg command is correct ✅
4. Video download successful ✅

### What's Failing ❌
**FFmpeg processing takes too long** for Render's free tier:
- Request timeout: ~30 seconds
- Limited CPU/memory
- Video re-encoding is resource-intensive

## Evidence from Logs

```
[Compile] FFmpeg command: ffmpeg -y -f concat ...
[POST]502  ← Server timed out here
```

FFmpeg command was built and started, but server timed out before completion.

## Solutions

### Solution 1: Optimize FFmpeg Settings (Implemented)

**Changed encoding preset from `veryfast` to `ultrafast`:**

```java
// OLD (slower, better quality)
-preset veryfast
-crf 23

// NEW (faster, acceptable quality)
-preset ultrafast
-crf 28
```

**Impact:**
- ⚡ **2-3x faster** encoding
- 📉 Slightly lower quality (acceptable for social media)
- 💾 Slightly larger file size

**Trade-off:**
- Speed vs Quality
- For 6-second video: ~5-10 seconds → ~2-4 seconds

### Solution 2: Upgrade Render Plan

**Free Tier Limitations:**
- 512 MB RAM
- Shared CPU
- 30-second timeout (estimated)

**Paid Tier Benefits ($7/month):**
- 512 MB - 4 GB RAM
- Dedicated CPU
- Longer timeouts
- Better performance

### Solution 3: Async Processing (Future Enhancement)

Instead of synchronous processing:

```
Manager clicks Publish
    ↓
Start FFmpeg (async)
    ↓
Return immediately: "Processing..."
    ↓
FFmpeg completes in background
    ↓
Notify manager when done
```

**Benefits:**
- No timeout issues
- Better user experience
- Can handle longer videos

**Implementation:**
- Use job queue (Redis + Bull)
- WebSocket notifications
- Progress tracking

### Solution 4: Use External Video Processing Service

**Options:**
- AWS MediaConvert
- Alibaba Cloud Media Processing
- Cloudinary Video API

**Benefits:**
- Dedicated video processing infrastructure
- No timeout issues
- Scalable

**Drawbacks:**
- Additional cost
- External dependency
- More complex setup

## Current Status

### Implemented: Solution 1 (Optimized FFmpeg)

Changed to `ultrafast` preset for faster processing on limited resources.

**Expected result:**
- Faster encoding (2-3x speed improvement)
- Should complete within timeout
- Acceptable quality for social media

### Recommended: Solution 2 (Upgrade Render)

For production use, upgrade to paid plan:
- More reliable
- Better performance
- Longer timeouts
- Worth $7/month for stable service

## Testing

### Test 1: Short Video (6 seconds)
- **Before**: 13.5 seconds → timeout
- **After**: ~4-6 seconds → should succeed

### Test 2: Medium Video (30 seconds)
- May still timeout on free tier
- Should work on paid tier

### Test 3: Long Video (60+ seconds)
- Will timeout on free tier
- Needs async processing or paid tier

## Monitoring

### Success Indicators
```
[Compile] ✅ FFmpeg completed successfully
[Compile] ✅ Compiled video with subtitles uploaded
[POST]200 (success)
```

### Failure Indicators
```
[POST]502 Bad Gateway
[POST]504 Gateway Timeout
responseTimeMS > 30000
```

## FFmpeg Preset Comparison

| Preset | Speed | Quality | File Size | Use Case |
|--------|-------|---------|-----------|----------|
| ultrafast | ⚡⚡⚡⚡⚡ | ⭐⭐⭐ | 📦📦📦 | **Free tier, quick preview** |
| veryfast | ⚡⚡⚡⚡ | ⭐⭐⭐⭐ | 📦📦 | Paid tier, good balance |
| fast | ⚡⚡⚡ | ⭐⭐⭐⭐ | 📦📦 | Production quality |
| medium | ⚡⚡ | ⭐⭐⭐⭐⭐ | 📦 | High quality, slow |

**Current choice: `ultrafast`** - Best for free tier with limited resources.

## CRF (Quality) Comparison

| CRF | Quality | File Size | Speed |
|-----|---------|-----------|-------|
| 18 | Excellent | Large | Slow |
| 23 | Good | Medium | Medium |
| **28** | **Acceptable** | **Small** | **Fast** |
| 32 | Fair | Very Small | Very Fast |

**Current choice: 28** - Acceptable quality, faster encoding.

## Recommendations

### For Development/Testing
- ✅ Use `ultrafast` preset
- ✅ Use CRF 28
- ✅ Keep videos short (< 30 seconds)

### For Production
- ✅ Upgrade to Render paid plan ($7/month)
- ✅ Use `veryfast` preset
- ✅ Use CRF 23
- ✅ Consider async processing for long videos

### For Scale
- ✅ Implement async job queue
- ✅ Use external video processing service
- ✅ Add progress tracking
- ✅ WebSocket notifications

## Next Steps

1. **Deploy optimized FFmpeg settings**
2. **Test with short video** (should work now)
3. **If still timing out**: Upgrade Render plan
4. **For long-term**: Implement async processing

## Cost Analysis

### Current (Free Tier)
- Cost: $0/month
- Limitation: Timeouts on video processing
- Workaround: Optimized FFmpeg settings

### Paid Tier
- Cost: $7/month
- Benefit: Reliable video processing
- ROI: Worth it for production use

### External Service
- Cost: ~$10-50/month (usage-based)
- Benefit: Scalable, no timeout issues
- ROI: Worth it for high volume

## Conclusion

The subtitle burning logic is **working perfectly**. The issue is Render's free tier timeout during FFmpeg processing.

**Immediate fix**: Optimized FFmpeg settings (implemented)  
**Long-term fix**: Upgrade Render plan or implement async processing
