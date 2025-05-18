# Matrix Ads Backend API

---

## 1. Authentication

### [auth] Signup
- **POST** `/auth/signup`
  - **Request Body:**
    ```json
    {
      "username": "string",
      "email": "string",
      "password": "string",
      "role": "content_creator" | "content_manager"
    }
    ```
  - **Response:** User object (`id`, `username`, `email`, `role`)
  - **Errors:** 400 if username or email already exists

### [auth] Login
- **POST** `/auth/login`
  - **Request Body:**
    ```json
    {
      "username": "string", // or
      "email": "string",    // one of username or email is required
      "password": "string"
    }
    ```
  - **Response:** User object (`id`, `username`, `email`, `role`)
  - **Errors:** 400 if credentials are invalid or neither username/email provided

---

## 2. Content Manager Endpoints

### [content_manager] Templates
- **POST** `/content-manager/templates`: Create a new template
- **GET** `/content-manager/templates/user/{userId}`: List templates for a user
- **GET** `/content-manager/templates/{templateId}`: Get template details
- **DELETE** `/content-manager/templates/{templateId}`: Delete a template

### [content_manager] Videos
- **POST** `/content-manager/videos/upload`: Upload a video (manager)
- **GET** `/content-manager/videos/user/{userId}`: List videos for a user
- **GET** `/content-manager/videos/{videoId}`: Get video details

### [shared] Delete Template
- **DELETE** `/content-manager/templates/{templateId}`
  - **Path Parameter:** `templateId` (required)
  - **Responses:**
    - `204 No Content`: Deleted successfully
    - `404 Not Found`: Not found
    - `500 Internal Server Error`: Error during deletion

### [shared] Upload Video (Manager)
- **POST** `/content-manager/videos/upload`
  - **Description:** Uploads a video file for a user (manager). The video is stored in Firebase Storage, a thumbnail is extracted and uploaded, and metadata is saved in Firestore.
  - **Request Parameters:**
    - `file` (form-data, required)
    - `userId` (string, required)
    - `title` (string, optional)
    - `description` (string, optional)
  - **Response:** 200 OK (video object), 500 Internal Server Error
  - **Note:** Max upload size is 200MB; larger files return 500 error with MaxUploadSizeExceededException.

### [shared] Get Template Details
- **GET** `/content-manager/templates/{templateId}`
  - **Response:** Template object with details for the specified template.
---

## 3. Content Creator Endpoints

### [content_creator] Upload Video for Template
- **POST** `/content-creator/videos/upload`
  - **Description:** Uploads a content creator's video for a specific template. Deletes any previous submission for this user/template, uploads the new video, saves metadata, and returns the result. (Future: AI similarity/feedback)
  - **Request (multipart/form-data):**
    - `file` (required): The video file to upload
    - `templateId` (required): ID of the template
    - `userId` (required): ID of the content creator
  - **Response:**
    ```json
    {
      "message": "Video uploaded and processed.",
      "videoId": "uuid-string",
      "videoUrl": "https://...",
      "similarityScore": null,
      "feedback": [],
      "publishStatus": "pending"
    }
    ```

### [content_creator] Check Video Submission for Template
- **GET** `/content-creator/videos/submission?templateId={templateId}&userId={userId}`
  - **Query Parameters:** `templateId`, `userId` (both required)
  - **Response:**
    - If submission exists: `{ videoId, videoUrl, similarityScore, feedback, publishStatus }`
    - If no submission: `{ videoId: null }`
  - **Errors:** 400 if required params missing, 500 if server error

### [content_creator] Subscribe to a Template
- **POST** `/content-creator/users/{userId}/subscribe?templateId=...`
  - **Path Parameter:** `userId` (required)
  - **Query Parameter:** `templateId` (required)
  - **Response:** 200 OK if successful, 400 if already subscribed or template does not exist

### [content_creator] Get All Subscribed Templates
- **GET** `/content-creator/users/{userId}/subscribed-templates`
  - **Path Parameter:** `userId` (required)
  - **Response:** Array of template objects (same as `/content-manager/templates/{templateId}`)
  - If a template was deleted, it is removed from the user's `subscribed_template` field.

### [shared] Get Template Details
- **GET** `/content-manager/templates/{templateId}`
  - **Response:** Template object with details for the specified template.

---

## 5. Dependencies

- Uses Firebase Admin SDK:
  ```xml
  <dependency>
      <groupId>com.google.firebase</groupId>
      <artifactId>firebase-admin</artifactId>
      <version>9.2.0</version>
  </dependency>
  ```

---

**Legend:**
- `[auth]`: Authentication endpoints
- `[content_manager]`: Content Manager endpoints
- `[content_creator]`: Content Creator endpoints
- `[shared]`: Endpoints used by both roles or for shared resources


- **GET** `/content-creator/users/{userId}/subscribed-templates`: Get all templates a user is subscribed to
  - **Path Parameter:**
    - `userId` (string, required): The ID of the content creator user
  - **Response:**
    - Array of template objects (same structure as returned by `/content-manager/templates/{templateId}`)
    - If a template was deleted, it is automatically removed from the user's `subscribed_template` field.

  ```

### Delete Template
- **DELETE** `/templates/{templateId}`
- **Description:** Deletes the template with the specified ID.
- **Path Parameter:**
  - `templateId` (string, required): The ID of the template to delete.
- **Responses:**
  - `204 No Content`: Template was deleted successfully.
  - `404 Not Found`: Template with the specified ID does not exist.
  - `500 Internal Server Error`: An error occurred during deletion.

### Upload Video

**Endpoint:** `POST /videos/upload`

**Description:**
Uploads a video file for a user. The video is stored in Firebase Storage, and a thumbnail is automatically extracted and uploaded. Metadata is saved in Firestore.

**Request Parameters:**
- `file` (form-data, required): Video file to upload (type: MultipartFile)
- `userId` (string, required): The ID of the user uploading the video
- `title` (string, optional): Title of the video
- `description` (string, optional): Description of the video

**Response:**
- `200 OK`: Returns the uploaded video object with its metadata and URLs
- `500 Internal Server Error`: If an error occurs during upload

**Note:** The maximum allowed upload size is 200MB. If you try to upload a larger file, you will receive a 500 Internal Server Error with a MaxUploadSizeExceededException.

**Example Request (cURL):**
```bash
curl -X POST http://<host>/videos/upload \
  -F "file=@/path/to/video.mp4" \
  -F "userId=USER123" \
  -F "title=Sample Video" \
  -F "description=Test upload"
```

---

### Subscribe to a Template (Content Creator)
- **POST** `/users/{userId}/subscribe?templateId=...`
- **Description:** Adds the specified template ID to the user's `subscribed_template` field. Field is created if it does not exist. Duplicate IDs are l
- **Responses:**
  - `200 OK`: Subscribed to template successfully
  - `500 Internal Server Error`: Failed to subscribe

### Get All Subscribed Templates (Content Creator)
- **GET** `/users/{userId}/subscribed-templates`
- **Description:** Returns all templates referenced in the user's `subscribed_template` field. If a template is deleted, it is removed from the field and not returned.
- **Path Parameter:**
  - `userId` (string, required): The ID of the content creator user
- **Response:**
  - Array of template objects (same structure as returned by `/templates/{templateId}`)
  - If a template was deleted, it is automatically removed from the user's `subscribed_template` field.

---

### Upload Content Creator Video for Template
- **POST** `/content-creator/videos/upload`
- **Description:** Uploads a content creator's video for a specific template. Deletes any previous submission for this user/template, uploads the new video, saves metadata, and returns the result. Placeholder for future AI similarity/feedback.
- **Request (multipart/form-data):**
  - `file` (required): The video file to upload
  - `templateId` (required): ID of the template
  - `userId` (required): ID of the content creator
- **Response:**
  - `message`: "Video uploaded and processed."
  - `videoId`: The new video ID (UUID)
  - `videoUrl`: Public download link
  - `similarityScore`: null (placeholder for future AI)
  - `feedback`: [] (placeholder for future AI)
  - `publishStatus`: "pending"
- **Example Request (cURL):**
```bash
curl -X POST http://<host>/content-creator/videos/upload \
  -F "file=@/path/to/video.mp4" \
  -F "templateId=template123" \
  -F "userId=creator456"
```
- **Example Response:**
```json
{
  "message": "Video uploaded and processed.",
  "videoId": "7e1e9b4a-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "videoUrl": "https://storage.googleapis.com/bucket/content-creator-videos/creator456/template123/7e1e9b4a-xxxx-xxxx-xxxx-xxxxxxxxxxxx.mp4",
  "similarityScore": null,
  "feedback": [],
  "publishStatus": "pending"
}
```