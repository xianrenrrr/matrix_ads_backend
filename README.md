# Java API Service Starter

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