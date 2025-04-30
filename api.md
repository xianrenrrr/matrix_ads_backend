# Matrix Ads Backend API

## Authentication

### Signup
- **POST** `/auth/signup`
- **Request Body:**
  ```json
  {
    "username": "string",
    "password": "string",
    "role": "content_creator" | "content_manager"
  }
  ```
- **Response:**
  - User object (with unique `id` and `role`, password omitted)
- **Errors:**
  - 400 if username already exists

### Login
- **POST** `/auth/login`
- **Request Body:**
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
- **Response:**
  - User object (with `id`, `username`, `role`, password omitted)
- **Errors:**
  - 400 if credentials are invalid

## Templates

### Create Template
- **POST** `/templates`
- **Request Body:**
  ```json
  {
    "userId": "string",
    "manualTemplate": { /* see below for structure */ }
  }
  ```
- **Response:**
  - The created template object (with assigned ID)
- **Errors:**
  - 500 if creation fails

#### ManualTemplate Structure (example)
```json
{
  "templateTitle": "string",
  "totalVideoLength": 0,
  "targetAudience": "string",
  "tone": "string",
  "scenes": [
    {
      "sceneNumber": 1,
      "sceneTitle": "string",
      "sceneDuration": 0,
      "scriptLine": "string",
      "presenceOfPerson": false,
      "preferredGender": "string",
      "personPosition": "string",
      "deviceOrientation": "string",
      "screenGridOverlay": [1,2,3],
      "screenGridOverlayLabels": ["label1","label2","label3"],
      "backgroundInstructions": "string",
      "specificCameraInstructions": "string",
      "movementInstructions": "string",
      "audioNotes": "string",
      "exampleFrame": "string",
      "otherNotes": "string"
    }
  ],
  "videoFormat": "string",
  "lightingRequirements": "string",
  "soundRequirements": "string"
}
```

---

**Note:** Only signup, login, and template creation endpoints are currently available. GET/PUT/DELETE for templates and users are not yet implemented.