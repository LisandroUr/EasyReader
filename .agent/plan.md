# Project Plan

Enhance the EasyReader app with an advanced PDF viewer (zoom, night mode, horizontal scroll), navigation improvements (search, continue reading), advanced organization (status, favorites, bulk import), and a revolutionary "Notebook Sharing" feature to export/import series with metadata and PDFs.

## Project Brief

# Project Brief: EasyReader

EasyReader is a vibrant and energetic Android PDF launcher designed to transform a chaotic collection of documents into a structured, personalized library. It focuses on organizing PDFs into "Series" and providing a premium reading experience with seamless data portability.

## Features

- **Series-Based Organization & Customization**: Group PDFs into custom folders (Series) with manual drag-and-drop ordering and personalized cover art selected from the gallery.
- **Enhanced Reading Experience**: A feature-rich PDF viewer supporting pinch-to-zoom, night mode (color inversion), and bookmarks, with a "Continue Reading" section on the Home screen for quick access.
- **Advanced Notebook Sharing**: Export an entire Series—including the organization metadata and the actual PDF files—into a single shareable package that other users can import to recreate the library structure.
- **Smart File Management & Bulk Import**: Utilize the Storage Access Framework (SAF) for persistent file permissions, allowing users to bulk import entire folders of PDFs efficiently.
- **Data Resilience**: Local library management with full database backup and restore functionality to ensure reading stats and organizational structures are never lost.

## High-Level Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3, Full Edge-to-Edge display)
- **Asynchronous Logic**: Kotlin Coroutines & Flow
- **Local Persistence**: Room Database (Code generation via **KSP**)
- **PDF Rendering**: Android `PdfRenderer`
- **File Access**: Storage Access Framework (SAF) with Persistent URI Permissions
- **Image Loading**: Coil (for custom series/book covers)

## Implementation Steps
**Total Duration:** 1h 10m 23s

### Task_1_Foundation: Implement the data layer (Room database with Series and Book entities), Repository, and Storage Access Framework (SAF) helpers for persistent PDF access.
- **Status:** COMPLETED
- **Updates:** Implemented Room database (Series, Book entities), DAOs, LibraryRepository with SAF persistence logic, and PdfHelper for thumbnail generation. Configured vibrant M3 theme and enabled edge-to-edge. Project builds successfully.
- **Acceptance Criteria:**
  - Room DB for Series and Books created
  - Repository handles SAF URI persistence
  - PdfRenderer utility implemented
  - Build pass
- **Duration:** 4m 39s

### Task_2_LibraryUI: Build the main library UI with Jetpack Compose and Material 3, including Series list (Home) and Book list (Series Detail) screens.
- **Status:** COMPLETED
- **Updates:** Implemented Home screen with Series grid and Detail screen with Books grid using Jetpack Compose and Material 3. Integrated SAF for PDF picking, automated thumbnail generation, and persistent URI permission handling. Set up Compose Navigation and ViewModel for state management. Verified edge-to-edge support and M3 theming.
- **Acceptance Criteria:**
  - Series and Books lists displayed with M3 components
  - Add/Edit Series and import Books flows working
  - Full edge-to-edge display implemented
- **Duration:** 2m 15s

### Task_3_Viewer_and_Customization: Develop the PDF Viewer screen with progress tracking, custom book ordering, gallery-based cover picking, and application of a vibrant M3 theme.
- **Status:** COMPLETED
- **Updates:** Developed the PDF Viewer screen with scroll-based page tracking and automatic progress saving using PdfRenderer. Implemented manual book ordering (Move Up/Down) and gallery-based custom cover picking for both series and books. Images are saved to internal storage for reliability. Refined the vibrant M3 theme and verified full edge-to-edge support across all screens. App icon and database schema (v2) are finalized.
- **Acceptance Criteria:**
  - PDF Viewer renders documents and saves reading progress
  - Manual ordering of books within series functional
  - Custom cover picker from gallery implemented
  - Vibrant M3 color scheme and adaptive icon applied
- **Duration:** 5m 6s

### Task_4_Final_Verification: Perform a final run to verify application stability, persistent storage, and alignment with the vibrant M3 design aesthetic.
- **Status:** COMPLETED
- **Updates:** Final verification completed successfully. The app is stable, handles Room persistence and SAF correctly, and adheres to Material 3 design and edge-to-edge display requirements. Reading progress and data persist across app restarts. Adaptive icon is verified. No regressions found.
- **Acceptance Criteria:**
  - App does not crash during navigation or PDF rendering
  - Reading progress persists across app restarts
  - Adaptive icon and full edge-to-edge display verified
  - All existing tests pass
- **Duration:** 18m 29s

### Task_5_Advanced_UI_and_Viewer: Enhance the PDF viewer with pinch-to-zoom, night mode, and horizontal scrolling. Implement library search, 'Continue Reading' shelf, and SAF-based bulk folder import.
- **Status:** COMPLETED
- **Updates:** Enhanced PDF viewer with pinch-to-zoom, night mode (color inversion), scroll orientation toggle, and brightness control. Added 'Continue Reading' shelf and unified search bar to the Home screen. Implemented bulk folder import using SAF OpenDocumentTree with persistent permissions and recursive file processing. Upgraded database to version 3. Verified build and stability.
- **Acceptance Criteria:**
  - PDF viewer includes zoom, night mode, and scroll orientation toggle
  - Home screen shows 'Continue Reading' and handles search queries
  - Bulk import correctly processes all PDFs in a selected directory
  - build pass
  - app does not crash
- **Duration:** 39m 54s

### Task_6_Portability_and_Final_Verify: Implement 'Notebook Sharing' to export/import series packages (metadata + PDFs) and add Database Backup/Restore functionality. Perform final run and verify stability. Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Series export creates a shareable package; import restores it correctly
  - Database backup and restore functions as expected
  - App icon, vibrant M3 theme, and edge-to-edge display are verified
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **StartTime:** 2026-05-23 12:48:11 ART

