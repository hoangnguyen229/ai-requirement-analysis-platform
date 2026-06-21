# Render Deployment Guide

This guide provides step-by-step instructions to deploy the **Requirement Document Analyzer Agent** on Render as a single, containerized Spring Boot application with the Angular frontend bundled into Spring Boot static resources.

---

## Deployment Architecture

The application is deployed as a single unit using **Docker**.
*   **Frontend (Angular)** is compiled during the Docker build stage and bundled directly inside the Spring Boot jar's static resources path (`src/main/resources/static`).
*   **Backend (Spring Boot)** serves both the API endpoints, the MCP JSON-RPC server, and compiles/serves the frontend static assets.
*   **Runtime Environment**: Supported by JRE 21 (Eclipse Temurin) on Linux, using a dynamic port injected via Render's `PORT` environment variable.

---

## 1. Prerequisites

Before starting, ensure you have:
1.  A Render account ([render.com](https://render.com)).
2.  Your codebase pushed to a GitHub, GitLab, or Bitbucket repository.
3.  A valid **Gemini API Key**.

---

## 2. Environment Variables

Your Render service requires the following environment variables:

| Variable Name | Required | Description |
| :--- | :--- | :--- |
| `GEMINI_API_KEY` | **Yes** | Your API key for calling the Google Gemini LLM. |
| `PORT` | No | Automatically injected by Render (defaults to `8080` if missing). |

---

## 3. Render Deployment Steps

Follow these steps to deploy on Render:

1.  **Push the Code**: Ensure all changes (including the `Dockerfile`, `.dockerignore`, and `.gitignore`) are committed and pushed to your remote repository.
2.  **Create a Web Service on Render**:
    *   Log in to the **Render Dashboard**.
    *   Click **New +** and select **Web Service**.
    *   Connect your repository.
3.  **Configure Web Service Settings**:
    *   **Name**: `requirement-document-analyzer` (or your preferred name).
    *   **Region**: Select a region close to your target audience.
    *   **Branch**: Select your main/deployment branch (e.g. `main` or `master`).
    *   **Root Directory**: Leave blank (use the repository root `.`).
    *   **Language**: Select **Docker** (Render will automatically detect the root `Dockerfile`).
4.  **Add Environment Variables**:
    *   Click on **Advanced** or navigate to the **Environment** tab.
    *   Click **Add Environment Variable**.
    *   Set Key = `GEMINI_API_KEY`, Value = `your_actual_gemini_api_key`.
5.  **Instance Type**: Choose your plan (the Free plan works for testing).
6.  **Deploy**: Click **Create Web Service**. Render will pull the repository, build the multi-stage Docker image, and run the service.

---

## 4. Local Verification & Testing

### Build and Run with Script
To build the application locally on a Linux/macOS machine or Bash shell:
```bash
# 1. Execute the build script
./build.sh

# 2. Run the packaged JAR with a custom port
PORT=9090 java -jar backend/target/excel-analyzer-0.0.1-SNAPSHOT.jar
```
Open [http://localhost:9090](http://localhost:9090) in your browser and verify functionality.

### Build and Run via Docker
To test the Docker container locally:
```bash
# 1. Build the Docker image
docker build -t requirement-document-analyzer .

# 2. Run the Docker container
docker run -p 8080:8080 -e GEMINI_API_KEY=your_key_here requirement-document-analyzer
```
Open [http://localhost:8080](http://localhost:8080) and verify the setup.

---

## 5. Post-Deployment Checklist

After deployment is completed on Render, verify the following:

- [ ] **Home Page Loads**: Access the public Render URL (e.g., `https://your-service.onrender.com`) and confirm the Angular interface is displayed.
- [ ] **API Access**: Confirm endpoints are reachable.
- [ ] **Upload File**: Upload `test_requirements.xlsx` or any supported document (`.xlsx`, `.docx`, `.txt`, `.md`).
- [ ] **Pipeline Execution**: Select **NEW** or **UPDATE** and run analysis.
- [ ] **Agent Execution Trace**: Verify the step-by-step logs from the multi-agent pipeline:
    *   `Coordinator Agent`
    *   `McpClientService`
    *   `DocumentMcpServer`
    *   `RequirementAgent`
    *   `TaskAgent`
- [ ] **Detailed Report**: Ensure a report is generated with sections for:
    *   Requirement Summary
    *   Business Rules
    *   Clarifications Needed
    *   Impact Analysis
    *   Development Tasks
    *   Test Cases

---

## 6. Troubleshooting

### Application fails to start or bind port on Render
*   Verify Spring Boot is set to `server.port=${PORT:8080}` in `application.properties`. Render assigns a dynamic port that changes on deployment.
*   Ensure that the `EXPOSE` instruction in the `Dockerfile` matches the default port `8080`.

### Gemini API errors
*   If you receive `IllegalStateException: GEMINI_API_KEY is not configured`, double-check the environment variables in the Render Dashboard and redeploy the service.

### Transient Gemini API 503 Errors
*   Spikes in demand can cause temporary `503 Service Unavailable` errors. The application has built-in exponential backoff retry logic that will automatically try up to 5 times before failing.

### Angular interface shows 404
*   Ensure that the target directory of Stage 1 (`/app/frontend/dist/frontend/browser`) matches the source path in Stage 2 of the `Dockerfile`.
