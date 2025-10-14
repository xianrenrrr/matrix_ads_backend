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

## API Endpoints

### Content Manager APIs

#### Template Management
- `GET /content-manager/templates/user/{userId}` - List user's templates (returns TemplateSummary)
- `GET /content-manager/templates/{templateId}` - Get template details
- `POST /content-manager/templates` - Create template
- `POST /content-manager/templates/manual-with-ai` - Create manual template with AI analysis
- `PUT /content-manager/templates/{templateId}` - Update template
- `PUT /content-manager/templates/{templateId}/folder` - Move template to folder
- `DELETE /content-manager/templates/{templateId}` - Delete template (⚠️ needs cascade logic)

#### Folder Management
- `GET /content-manager/folders?userId={userId}` - List user's folders
- `POST /content-manager/folders` - Create folder
- `PUT /content-manager/folders/{folderId}` - Rename folder
- `DELETE /content-manager/folders/{folderId}` - Delete folder (⚠️ needs cascade logic)

#### Template Assignment (Time-Limited Push)
- `POST /content-manager/template-assignments` - Push template to groups
- `GET /content-manager/template-assignments?groupId={groupId}` - Get group's assignments
- `GET /content-manager/template-assignments/{assignmentId}` - Get assignment details
- `DELETE /content-manager/template-assignments/{assignmentId}` - Remove assignment

#### Group Management
- `GET /content-manager/groups?userId={userId}` - List manager's groups
- `POST /content-manager/groups` - Create group
- `PUT /content-manager/groups/{groupId}` - Update group
- `DELETE /content-manager/groups/{groupId}` - Delete group
- `POST /content-manager/groups/{groupId}/members` - Add member to group
- `DELETE /content-manager/groups/{groupId}/members/{userId}` - Remove member

#### Submission Review
- `GET /content-manager/submissions?managerId={managerId}` - List submissions
- `GET /content-manager/submissions/{submissionId}` - Get submission details
- `PUT /content-manager/scenes/{sceneId}/manual-override` - Override scene approval

#### Video Management
- `POST /content-manager/videos/upload` - Upload video and create AI template
- `GET /content-manager/videos/{videoId}` - Get video details
- `GET /content-manager/videos/{videoId}/stream` - Get signed video URL

### Content Creator APIs

#### User Management
- `GET /content-creator/users/{userId}` - Get user profile
- `GET /content-creator/users/{userId}/templates` - Get user's assigned templates
- `POST /content-creator/users/{userId}/join-group` - Join group via invite code

#### Scene Submission
- `POST /content-creator/scenes/submit` - Submit scene video
- `GET /content-creator/scenes/user/{userId}` - Get user's scene submissions
- `GET /content-creator/scenes/{sceneId}` - Get scene details

### Mini Program APIs (via Proxy)
- All Content Creator APIs accessible via `/api/content-creator/*`
- Proxy handles CORS and URL signing for images/videos

## Data Models

### Core Models
- `ManualTemplate` - Template with scenes, metadata, and folder assignment
- `TemplateAssignment` - Time-limited template push to groups
- `TemplateFolder` - Hierarchical folder structure
- `Scene` - Individual scene with overlay instructions
- `SceneSubmission` - User-submitted scene video
- `SubmittedVideo` - Complete video submission
- `Group` - Content creator group
- `User` - User profile and preferences

### DTO Models
- `TemplateSummary` - Lightweight template list response (id, title, folderId)
- `CreateTemplateRequest` - Template creation request
- `DeletionResult` - Deletion operation statistics (planned)

## Known Issues

- ⚠️ **CRITICAL**: Template deletion doesn't cascade (see `docs/DELETION_LOGIC.md`)
- ⚠️ **CRITICAL**: Folder deletion doesn't cascade (see `docs/DELETION_LOGIC.md`)
- `ComparisonAIService.java` is 1,156 lines (needs service splitting)
- `ContentManager.java` is 819 lines (needs controller splitting)
- Some DAO implementations have duplicate code (needs base class)

See `docs/IMPROVEMENT_RECOMMENDATIONS.md` and `docs/DELETION_LOGIC.md` for details.
