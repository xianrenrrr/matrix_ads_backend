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

**Example Request (cURL):**
```bash
curl -X POST http://<host>/videos/upload \
  -F "file=@/path/to/video.mp4" \
  -F "userId=USER123" \
  -F "title=Sample Video" \
  -F "description=Test upload"