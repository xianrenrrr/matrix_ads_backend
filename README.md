# Java API Service Starter

---

## Changelog

### 2025-05-01
- Fixed unused import warning for `java.io.IOException` in `VideoController.java` by removing and re-adding as required by the code logic.
- Updated `api.md` with documentation for the `uploadVideo` API endpoint.
- Updated `Rules.md` to clarify process for API documentation and changelog updates.

### 2025-05-01
- Added video upload endpoint (`/videos/upload`) that stores user videos in Firebase Storage under `videos/{userId}/{videoId}/{originalFilename}`.
- Automatically extracts a thumbnail (screenshot from the first second) using FFmpeg and uploads it as `videos/{userId}/{videoId}/thumbnail.jpg`.
- Both video and thumbnail URLs are saved in Firestore.
- After each video upload, the backend now automatically creates a placeholder template in Firestore using the same `userId` and `videoId` as the template's `id`, with the title `AI model out of usage`. This is a placeholder for future AI-driven template generation.
- Added ai folder in src/main/java/com/example/demo/ai
- **Requirements:**
  - FFmpeg must be installed and available in the production environment (for thumbnail extraction).
  - Google Cloud Storage Java SDK dependency is required for Firebase Storage integration.
- **Note:** UUIDs are used for video/template ids, so duplicates are not possible unless ids are set manually.

---

This is a minimal Java API service starter based on [Google Cloud Run Quickstart](https://cloud.google.com/run/docs/quickstarts/build-and-deploy/deploy-java-service).

## Getting Started
## messgae from frontend ai:

Summary of Current Frontend Development Status (Matrix AI Ads Video Builder - Angular):

Project Structure:
The project is an Angular application generated using Angular CLI.
It includes components for user authentication (login, signup), content creation, and content management.
The main sections developed are:
Content Manager Dashboard: The main hub for managing templates and ads.
Template Board: A section within the Content Manager Dashboard for creating and managing video templates.
Ads Board: A section within the Content Manager Dashboard (currently minimal implementation).
Content creator dashboard This section is also created but with minimal functionalities.
Common Settings This section is also created but with minimal functionalities.
Manual Template Creation (Main Feature Developed):
TemplateBoardComponent:
Allows users to choose between creating a template manually or uploading a video.
Implements a detailed form for manual template creation, following the Manual Template Creation Specification.
Includes inputs for:
General Template Information (title, video length, target audience, tone).
Scene-by-Scene Setup (multiple scenes with detailed parameters).
Global Video Settings (video format, lighting, sound).
Implements a 3x3 screen grid selection for each scene.
addScene, updateScene and toggleGridBlock functions implemented.
Forms Module Imported to be able to use ngModel.
Manual Template Data Structure (manualTemplate):
A comprehensive object that holds all the data for a manually created template, including scenes and their properties.
The structure is well defined, so this is an important object to keep in mind when developing the backend.
Navigation: The app has implemented the correct routing.
Functionality:
Users can create templates manually by filling in the form.
Users can upload a video to create a new template.
Users can dynamically add multiple scenes to a template.
Users can select the blocks in the grid for each scene and also specify the label for each block.
Data binding is implemented for all inputs.
The manualTemplate data structure is updated correctly as the user fills out the form.
Currently, addManualTemplate() prints the manualTemplate object to the console.
createNewTemplate() handles the video template upload, but only saves the video name.
Technology:
Angular 17+
TypeScript
HTML
CSS
Next Steps for the frontend: The main next steps for the frontend will be :
Save the template in a database.
Improve the user interface.
Add more functionalities to the other components.
Create the final logic to handle a template.
Summary for the AI in Your New Backend Project:

"I am developing a backend for a video creation application called 'Matrix AI Ads Video Builder.' The frontend is an Angular application that allows users to create video templates. Users can create templates manually or upload a video. The main feature for now is the manual template creation. I have implemented a very specific way to create the templates, please, I want you to take this into account.

Frontend Data Structure for Manual Templates:

The frontend application has a complex data structure for storing manually created templates. This data structure is called manualTemplate and has the following properties:

manualTemplate: any = {
    templateTitle: '', // string
    totalVideoLength: 0, // number
    targetAudience: '', // string
    tone: '', // string
    scenes: [{ // array of scene objects
        sceneNumber: 1, // number (auto-incrementing)
        sceneTitle: '', // string
        sceneDuration: 0, // number
        scriptLine: '', // string
        presenceOfPerson: false, // boolean
        preferredGender: '', // string ("Male", "Female", "No Preference")
        personPosition: '', // string ("1/3 Left", "Center", "1/3 Right", "Close-up")
        deviceOrientation: '', // string ("Phone (Portrait 9:16)", "Laptop/Desktop (Landscape 16:9)")
        screenGridOverlay: [], // array of numbers (selected grid blocks 1-9)
        screenGridOverlayLabels: [], // array of strings. Same length as screenGridOverlay, indicates the label of each block.
        backgroundInstructions: '', // string
        specificCameraInstructions: '', // string
        movementInstructions: '', // string ("No Movement", "Slow Pan", "Walk Toward Camera", "Static")
        audioNotes: '', // string
        exampleFrame: '', // string (URL to an image)
        otherNotes: '' // string
    }],
    videoFormat: '', // string ("1080p 9:16", "1080p 16:9")
    lightingRequirements: '', // string
    soundRequirements: '', // string
};


Functionalities:

Manual Template Creation:
Users can create new templates manually through a detailed form.
The form allows multiple scenes to be added to a template.
Each scene has specific properties (refer to the data structure).
There is also a 3x3 grid selection for each scene.
Video Template Upload:
Users can upload a video to create a template. This function is not fully implemented yet.
Data Persistence:
This is an important next step. For now the data is only printed to the console, but it must be saved in a database.
Backend Requirements:

Database:
You will need a database to store the templates.
Firebase Firestore is recommended, but you can use another database if needed.
Data Model:
You will need to design a data model in the database that corresponds to the manualTemplate data structure in the frontend.
You will need to use collections and documents to save this info.
API Endpoints:
You will need API endpoints to:
Create a new template.
Get all templates.
Get a specific template by ID.
Update an existing template.
Delete a template.
Authentication:
You will need to implement user authentication so that only authorized users can create, read, update, and delete templates.
AI Integration:
Later, this app will have AI integration, so, be ready for that.
The main idea of the AI will be to help the user create new templates.
I will be developing the backend using another firebase studio project. Please, take into account the context of the project and the given summary."

Server should run automatically when starting a workspace. To run manually, run:
```sh
mvn spring-boot:run
```

---

## Backend Development Summary (as of 2025-04-30)

### Matrix AI Ads Video Builder Backend Progress

**Developed on:** 2025-04-28

**Key Features Implemented:**

- **GET /videos/user/{userId}:** Returns all videos for a given user.
- **GET /videos/{videoId}:** Returns detailed information about a specific video.
- **POST /templates endpoint:** Allows creation of new video templates by accepting a structured JSON payload that matches the frontend `manualTemplate` data model.
- **ManualTemplate & Scene Models:** Java classes closely mirror the frontend structure, including support for scene-by-scene setup and the 3x3 grid overlay (with block numbers as `List<Integer>` and labels as `List<String>`).
- **Firestore Integration:** Templates are stored in the `video_template` collection in Firestore. Collections and documents are created automatically on first insert; no manual DB setup required.
- **User Authentication:** Added `/auth/signup` and `/auth/login` endpoints. Signup creates a user with a unique ID and role (either `content_creator` or `content_manager`).
- **User Roles:** Each user has a role assigned at signup, which can be either `content_creator` or `content_manager`.
- **Basic In-Memory User Storage:** (for now, users are stored in-memory; persistence can be upgraded later).
- **Unit Testing:** Basic test for template creation endpoint verifies end-to-end functionality using Spring Boot's MockMvc.

**Current Limitations:**
- Only template creation (POST) is implemented; retrieval, update, and delete endpoints are not yet available.
- User authentication is basic and uses in-memory storage (no persistent DB yet).
- No AI integration yet, but the codebase is structured to support future enhancements.

**Next Steps (recommended):**
- Implement GET/PUT/DELETE endpoints for templates.
- Add persistent user storage and improve authentication/validation.
- Connect frontend to backend for real data persistence.
- Prepare for AI-assisted template creation in future iterations.

todo: bug video wioth same name