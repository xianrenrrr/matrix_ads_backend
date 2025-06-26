# AI Services Architecture

This folder contains the AI-powered services for the Matrix Ads project, organized by task-specific functionality with shared reusable components.

## 📁 Folder Structure

```
ai/
├── shared/          # Reusable AI services across all tasks
├── template/        # AI Template Generator
├── comparison/      # Video Comparison (Future)
├── suggestions/     # AI Suggestions (Future)
└── README.md        # This documentation
```

---

## 🤖 Shared Services (`shared/`)

These services can be used across multiple tasks and provide core AI functionality:

### `BlockDescriptionService`
- **Purpose**: Analyze image blocks using GPT-4o vision
- **Input**: Map of image URLs
- **Output**: Map of text descriptions
- **Used by**: Task 1 (template generation), Task 2 (comparison), Task 3 (suggestions)

### `VideoSummaryService`
- **Purpose**: Generate video summaries using GPT-4o
- **Input**: Video metadata, scene labels, block descriptions
- **Output**: Concise video summary
- **Used by**: Task 1 (template creation), Task 3 (suggestions)

### `KeyframeExtractionService`
- **Purpose**: Extract keyframes from videos using FFmpeg
- **Input**: Video URL, start/end timestamps
- **Output**: Keyframe image URL in Firebase Storage
- **Used by**: Task 1 (scene analysis), Task 2 (comparison frames)

---

## 🎯 AI Template Generator (`template/`)

**Goal**: Convert raw videos into structured 3x3 scene templates

### Services

#### `AITemplateGenerator` (Main Orchestrator)
- Coordinates all services to generate complete `ManualTemplate`
- Handles error cases with fallback templates
- Processes multiple scenes in sequence

#### `SceneDetectionService`
- Uses Google Video Intelligence API
- Detects scene boundaries, labels, person presence
- Returns `List<SceneSegment>` with timestamps

#### `BlockGridService`
- Crops keyframes into 3x3 grids (9 blocks)
- Uploads each block to Firebase Storage
- Returns map of block positions to image URLs

### Workflow
```
Video URL → Scene Detection → Keyframe Extraction → 3x3 Grid → Block Description → Template
```

### Dependencies
- Google Video Intelligence API
- FFmpeg (keyframe extraction)
- OpenAI GPT-4o (block descriptions)
- Firebase Storage

---

## 🔄 Video Comparison (`comparison/`)

**Goal**: AI-powered video comparison and similarity analysis

### Planned Services
- `VideoComparisonService` - Compare video similarities
- `FrameDifferenceAnalyzer` - Analyze frame-by-frame differences
- `ContentSimilarityMatcher` - Match similar content elements

### Shared Dependencies
- `KeyframeExtractionService` (extract comparison frames)
- `BlockDescriptionService` (analyze frame content)
- `VideoSummaryService` (summarize differences)

---

## 💡 AI Suggestions (`suggestions/`)

**Goal**: Generate AI-powered video improvement suggestions

### Planned Services
- `VideoSuggestionService` - Generate improvement recommendations
- `CompositionAnalyzer` - Analyze video composition quality
- `ContentOptimizer` - Suggest content enhancements

### Shared Dependencies
- `BlockDescriptionService` (analyze visual elements)
- `VideoSummaryService` (understand video context)
- `KeyframeExtractionService` (extract representative frames)

---

## 🔧 Configuration

### Required Environment Variables
```properties
# OpenAI API Key (for GPT-4o services)
OPENAI_API_KEY=sk-your-api-key-here
```

### Firebase Configuration
- Bucket: `matrix_ads_video`
- Folders: `keyframes/`, `blocks/`

### Google Cloud Services
- Video Intelligence API (scene detection)
- Cloud Storage (video/image storage)

---

## 🚀 Usage Examples

### Generate AI Template
```java
@Autowired
private AITemplateGenerator aiTemplateGenerator;

ManualTemplate template = aiTemplateGenerator.generateTemplate(video);
```

### Using Shared Services Independently
```java
@Autowired
private BlockDescriptionService blockDescriptionService;

Map<String, String> descriptions = blockDescriptionService.describeBlocks(imageUrls);
```

---

## 🔄 Migration from Old Structure

The old flat AI structure has been reorganized:

**Old**: `ai/AITemplateGeneratorImpl.java`
**New**: `ai/template/AITemplateGeneratorImpl.java`

**Old**: `ai/BlockDescriptionService.java`
**New**: `ai/shared/BlockDescriptionService.java`

### Breaking Changes
- Package imports updated from `com.example.demo.ai.*` to specific packages
- Shared services moved to `com.example.demo.ai.shared.*`
- Template services moved to `com.example.demo.ai.template.*`
- Comparison services moved to `com.example.demo.ai.comparison.*`
- Suggestion services moved to `com.example.demo.ai.suggestions.*`

---

## 🛠️ Development Guidelines

1. **Shared Services**: Place reusable AI functionality in `shared/`
2. **Feature-Specific**: Keep feature-unique logic in respective folders (`template/`, `comparison/`, `suggestions/`)
3. **Error Handling**: Always provide fallback mechanisms
4. **Logging**: Use descriptive logging for AI operations
5. **Resource Management**: Clean up temporary files and API connections

---

## 📈 Future Enhancements

- **Comparison**: Video comparison and similarity scoring
- **Suggestions**: AI-powered suggestions and optimizations
- **Shared**: Additional AI models (Claude, Gemini alternatives)
- **Performance**: Caching for AI API responses
- **Monitoring**: AI service usage and cost tracking