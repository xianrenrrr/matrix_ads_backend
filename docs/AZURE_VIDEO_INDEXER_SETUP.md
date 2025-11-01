# Azure Video Indexer Setup Guide

Azure Video Indexer provides superior subtitle extraction compared to Alibaba Cloud OCR.

## Features
- **Transcript extraction** with word-level timing
- **OCR** for on-screen text
- **Scene detection**
- **Face detection**
- **Emotion detection**
- Much better accuracy for Chinese and English

## Setup Steps

### 1. Create Azure Video Indexer Account

1. Go to [Azure Portal](https://portal.azure.com)
2. Search for "Video Indexer" or go to https://www.videoindexer.ai
3. Click "Sign in" and use your Azure account
4. Create a new account or use existing

### 2. Get API Credentials

#### Option A: Trial Account (Free, Limited)
1. Go to https://www.videoindexer.ai
2. Sign in with your account
3. Click on your profile → "Account settings"
4. Copy your **Account ID**
5. Go to "API" tab
6. Copy your **Subscription Key**
7. Location will be: `trial`

#### Option B: Production Account (Paid, Unlimited)
1. Create Video Indexer resource in Azure Portal
2. Get Account ID from resource overview
3. Get Subscription Key from "Keys and Endpoint"
4. Location will be your Azure region (e.g., `eastus`, `westus2`)

### 3. Configure Environment Variables

Add to your `application.properties` or environment:

```properties
# Azure Video Indexer Configuration
AZURE_VIDEO_INDEXER_ACCOUNT_ID=your-account-id-here
AZURE_VIDEO_INDEXER_SUBSCRIPTION_KEY=your-subscription-key-here
AZURE_VIDEO_INDEXER_LOCATION=trial
```

Or set as environment variables:
```bash
export AZURE_VIDEO_INDEXER_ACCOUNT_ID="your-account-id"
export AZURE_VIDEO_INDEXER_SUBSCRIPTION_KEY="your-subscription-key"
export AZURE_VIDEO_INDEXER_LOCATION="trial"
```

### 4. Test the Integration

Run the test class:
```bash
mvn test -Dtest=AzureVideoIndexerExtractorTest
```

Or use the standalone test:
```bash
cd model_testing
javac -cp "../matrix_ads_backend/target/classes:." AzureVideoIndexerTest.java
java -cp "../matrix_ads_backend/target/classes:." AzureVideoIndexerTest
```

## API Limits

### Trial Account
- **Free tier**: 10 hours of indexing per month
- **Rate limit**: 5 concurrent indexing jobs
- **Storage**: 30 days retention

### Production Account
- **Pay-as-you-go**: $0.15 per minute of video
- **No rate limits** (within reason)
- **Unlimited storage**

## Pricing Comparison

| Service | Cost per Hour | Accuracy | Features |
|---------|--------------|----------|----------|
| Azure Video Indexer | $9/hour | ⭐⭐⭐⭐⭐ | Transcript, OCR, Faces, Emotions |
| Alibaba Cloud OCR | $0.50/hour | ⭐⭐ | OCR only, poor accuracy |
| Alibaba Cloud ASR | $0.30/hour | ⭐⭐⭐ | Audio only |

**Recommendation**: Use Azure Video Indexer for production. Much better accuracy justifies the cost.

## Usage in Code

```java
@Autowired
private AzureVideoIndexerExtractor azureExtractor;

// Extract subtitles
List<SubtitleSegment> subtitles = azureExtractor.extract(videoUrl);

// Group by scenes
Map<Integer, String> scriptLines = azureExtractor.groupByScenes(subtitles, scenes);
```

## Troubleshooting

### Error: "Failed to get access token"
- Check your subscription key is correct
- Verify account ID matches your Video Indexer account
- Ensure location is correct (`trial` for trial accounts)

### Error: "Failed to upload video"
- Video URL must be publicly accessible
- Supported formats: MP4, AVI, MOV, WMV
- Max file size: 2GB for trial, 30GB for production

### Error: "Indexing timed out"
- Long videos take time (1 minute of video ≈ 1 minute of processing)
- Increase `maxAttempts` in `waitForIndexing()` method
- Check video is not corrupted

## API Documentation

Full API reference: https://learn.microsoft.com/en-us/rest/api/videoindexer/

Key endpoints:
- **Get Access Token**: `GET /auth/{location}/Accounts/{accountId}/AccessToken`
- **Upload Video**: `POST /{location}/Accounts/{accountId}/Videos`
- **Get Index**: `GET /{location}/Accounts/{accountId}/Videos/{videoId}/Index`
- **Get Insights**: `GET /{location}/Accounts/{accountId}/Videos/{videoId}/Index`

## Support

- Azure Video Indexer Portal: https://www.videoindexer.ai
- Documentation: https://learn.microsoft.com/en-us/azure/azure-video-indexer/
- Support: https://azure.microsoft.com/support/
