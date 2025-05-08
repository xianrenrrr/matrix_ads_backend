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
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.2.0</version>
</dependency>

---

### Get All Templates for a User
- **GET** `/templates/user/{userId}`
- **Response:** Array of objects, each containing only the template `id` and `templateTitle` fields. Example:
  ```json
  [
    { "id": "template1", "templateTitle": "My First Template" },
    { "id": "template2", "templateTitle": "Promo Video" }
  ]
  ```

### Get Template Details
- **GET** `/templates/{templateId}`
- **Response:** Template object with details for the specified template.

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
- **Description:** Adds the specified template ID to the user's `subscribed_template` field. Field is created if it does not exist. Duplicate IDs are prevented.
- **Path Parameter:**
  - `userId` (string, required): The ID of the content creator user
- **Query Parameter:**
  - `templateId` (string, required): The ID of the template to subscribe to
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