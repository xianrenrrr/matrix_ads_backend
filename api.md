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
- **Response:** Array of template objects for the user.

### Get Template Details
- **GET** `/templates/{templateId}`
- **Response:** Template object with details for the specified template.