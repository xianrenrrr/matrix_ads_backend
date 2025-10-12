# Matrix Ads Backend

Spring Boot backend for the Matrix Ads video content platform.

## Structure

```
src/main/java/com/example/demo/
├── ai/                     # AI services (template generation, scene analysis)
│   ├── services/           # AI service implementations
│   ├── providers/          # AI provider integrations (OpenAI, DeepSeek)
│   ├── label/              # Object detection and labeling
│   └── seg/                # Video segmentation
├── controller/             # REST API controllers
│   ├── contentmanager/     # Content manager endpoints
│   └── contentcreator/     # Content creator endpoints
├── dao/                    # Data Access Objects (Firestore)
├── model/                  # Entity models
├── service/                # Business logic services
├── config/                 # Configuration classes
└── util/                   # Utility classes
```

## Key Files

- `DemoApplication.java` - Spring Boot entry point
- `controller/contentmanager/ContentManager.java` - Template management (819 lines - needs splitting)
- `ai/services/ComparisonAIService.java` - AI comparison (1,156 lines - needs splitting)
- `ai/services/TemplateAIServiceImpl.java` - AI template generation (786 lines)

## Development

```bash
./mvnw spring-boot:run  # Start on http://localhost:8080
./mvnw clean package    # Build JAR
./mvnw test             # Run tests
```

## Tech Stack

- **Framework:** Spring Boot 3.x
- **Language:** Java 17+
- **Database:** Firebase Firestore
- **Storage:** Firebase Storage
- **AI:** OpenAI GPT-4, DeepSeek, PaddleDetection

## Configuration

Required environment variables:
- `FIREBASE_CREDENTIALS` - Firebase service account JSON
- `OPENAI_API_KEY` - OpenAI API key
- `DEEPSEEK_API_KEY` - DeepSeek API key

## Known Issues

- `ComparisonAIService.java` is 1,156 lines (needs service splitting)
- `ContentManager.java` is 819 lines (needs controller splitting)
- Some DAO implementations have duplicate code (needs base class)

See `docs/IMPROVEMENT_RECOMMENDATIONS.md` for details.
