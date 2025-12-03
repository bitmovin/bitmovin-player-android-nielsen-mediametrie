# Bitmovin Player + Nielsen Measurement (Android, Kotlin)

This sample demonstrates how to integrate the **Nielsen App SDK** into an **Android** app using the **Bitmovin Player**. It includes:
- A lightweight SDK wrapper (`NielsenSdkManager`) and typed init settings (`NielsenInitSettings`).
- A `NielsenMetadata` model for building safe content metadata JSON for the Nielsen SDK.
- A `NielsenPlayerTracker` that maps Bitmovin player events to Nielsen SDK calls, manages playhead pings, ads, and buffering stalls.
- Unit tests with **JUnit/MockK/Robolectric** and a **JaCoCo** report task for coverage.

---

## Project Overview

**Key classes**

- `NielsenSdkManager` — wraps `AppSdk` creation, holds a singleton, and logs SDK callbacks (via `IAppNotifier`).
- `NielsenInitSettings` — strongly typed init configuration → converts to Nielsen JSON.
- `NielsenMetadata` — builds content metadata (length coercion, `islivestn` mapping, optional Mediametrie fields).
- `NielsenPlayerTracker` — stateful tracker that wires Bitmovin events to Nielsen: content/ad states, buffering stalls, and playhead loop.
- `MyApplication` — initializes Nielsen on app start.
- `MainActivity` — forwards lifecycle events to the view-model to resume/pause/end tracking.

---

## Requirements

- A valid **Nielsen App ID** for your environment.
- Bitmovin Player license configured in the project.
- A User Consent Management system. Your application is responsible for gathering user consent for data collection in accordance with privacy regulations.

---

## Development

To be able to start development and launch the application or the SDK library, modify the `gradle.properties.example` file (on the root directory) to your needs. Remove the `.example` from the file name so that Android Studio identifies the file as the rootProject file and loads the necessary variables for the `build.gradle.kts` files.

---

## Nielsen SDK Initialization

### Main Configuration Options

| Setting        | Type    | Required | Description                                                      |
|----------------|---------|----------|------------------------------------------------------------------|
| `appId`        | String  | Yes      | Your Nielsen **App ID** (e.g. `PXXXXXXXX-...`).                  |
| `optOut`       | Boolean | Yes      | `true` disables measurement (use for consent/opt‑out).           |
| `enableFpid`   | Boolean | No       | Enables First‑Party ID collection. The default value is `true`    |
| `debugLogging` | Boolean | No       | If `true`, enables Nielsen dev debug (`nol_devDebug = "DEBUG"`). |


## Content Metadata

`NielsenMetadata` builds the **content** metadata JSONObject expected by Nielsen. Typical fields:

| Field       | Type                       | Required | Notes                                                                                 |
|-------------|----------------------------|----------|---------------------------------------------------------------------------------------|
| `type`      | String                     | Yes      | Nielsen content type (e.g., `"content"`).                                            |
| `assetId`   | String                     | Yes      | Unique content ID.                                                                    |
| `program`   | String                     | Yes      | Program/show name.                                                                    |
| `title`     | String                     | Yes      | Episode/title.                                                                        |
| `length`    | Double?                    | Yes      | Seconds. Non‑positive/NaN/∞ are coerced to a safe **live** default internally.       |
| `isLivestn` | Boolean                    | Yes      | Converted to `"y"` / `"n"` for `islivestn`.                                          |
| `cli_md`    | MediametrieStreamingType?  | No       | France-specific streaming type (if applicable).                                       |
| `cli_ch`    | String?                    | No       | France channel code (if applicable).                                                  |
| `subbrand`  | String?                    | No       | Optional brand/subbrand.                                                              |

---

For more information regarding the Content Metadata, refer to the official [Neilsen SDK Documentation](https://engineeringportal.nielsen.com/wiki/France_SDK_Metadata#Content_Metadata) 


## Running the Tests

### Before running tests: set `JAVA_HOME` to Android Studio’s JBR

Gradle must launch with JDK **21** (or 17). Point `JAVA_HOME` at **Android Studio’s JBR** in your shell, then run Gradle from the same terminal.

**Windows (PowerShell)**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

**Windows (Git Bash)**
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
```

**macOS**
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```
---

### From Android Studio
- **Run a test/class:** Right‑click the test (or class) → **Run**.
- **Run with coverage:** Right‑click → **Run with Coverage**

### From the command line (unit tests / Robolectric)
```bash
# all unit tests in src/test
./gradlew test

# filter to one class / method
./gradlew testDebugUnitTest --tests "*NielsenPlayerTrackerTest"
./gradlew testDebugUnitTest --tests "com.example.myapp.NielsenPlayerTrackerTest.methodName"
```

> If you see errors from the CLI, make sure the terminal uses JDK **21** (see `org.gradle.java.home` above).

---

## Test Coverage (JaCoCo)

**Unit test coverage (JaCoCo):**
```bash
./gradlew clean jacocoTestReport
```
Open the HTML report:
```
app/build/reports/jacoco/jacocoTestReport/html/index.html
```

---

# **Nielsen Mediametrie SDK for Android**

## **Introduction** 

This document describes how the data collector works as a bridge between the **Bitmovin Player** and the **Nielsen Médiamétrie SDK** for Android, from its structure to the publication process. This project was designed so that the library is available through an internal **Maven** repository.

## **Application Module Structure (nielsen-player-android-analytics-nielsen)**

The application module (**app**) handles the user interface logic and the Bitmovin player's configuration. Its clean architecture ensures a separation of concerns, keeping the presentation layer within this module.

* **PlaybackViewModel.kt**: The main class that manages the view's state and interaction with the SDK library.  
* **MainActivity.kt** and **MyApplication.kt**: The entry points and lifecycle management for the application.  
* **ui/theme**: Contains the **Compose** user interface elements.  
* **PlaybackScreen.kt** and **PlayerView.kt**: Classes that define the player's user interface.

## **Library Module Structure (nielsen-mediametrie-sdk)**

The library module is designed to be reusable and self-contained, encapsulating all the business logic for the Nielsen tracking collector.

* **kotlin+java**:  
  * **model**: Contains the data models that define the SDK's API, such as `NielsenInitSettings.kt` and `NielsenMetadata.kt`.  
  * **tracking**: Includes the core tracking logic that collects events from the **Bitmovin Player** and sends them to **Nielsen Médiamétrie** via its SDK in `NielsenPlayerTracker.kt`.  
  * **utils**: Contains utility classes internal to the SDK, such as `Constants.kt` and `MediametrieStreamingType.kt`.  
* **test**: Contains the unit tests that verify the correct functionality of the SDK in isolation (e.g., `NielsenPlayerTrackerTest.kt`).

## **Publication Process and Dependency Management**

The library is published as an **AAR** file in a local **Maven** repository, which allows it to be consumed by the application module.

### **Publishing the Library**

Before being able to publish the library, make sure to clean & build the library with the following command in the root directory:

```
./gradlew :nielsen-mediametrie-sdk:clean :nielsen-mediametrie-sdk:assembleRelease
```

To publish the **AAR** to the local repository, use the following Gradle command from the command line:

```
./gradlew publish
```

* **build.gradle.kts Configuration**: The publication configuration is located in the library's `build.gradle.kts` file and is ready to be adapted for an internal **Maven** repository.  
* **Repository Management**:   
  * The project includes configurations for both a local repository and a public **Maven** repository.
* Credentials for the **Maven** repository are securely managed in `local.properties`.

### **Consuming the Library**

To use the library in the application module, you need to import the **AAR** as a dependency. The library's version, defined in `gradle.properties`, must be manually updated each time a new version is published.

```
dependencies {
    // ... other dependencies
    implementation("com.bitmovin.player.integration:nielsen-mediametrie-sdk:0.1.2")
}
```

## **Publishing to the Internal Maven Repository**

To publish the library to the **internal** **Maven** repository, follow these steps:

1. **Update Credentials**: In the `local.properties` file of the root project, update the following properties with the provided credentials:  
   * `mavenUsername`  
   * `mavenPassword`  
2. **Increment Version**: In the `gradle.properties` file, update the library's version number. For example, change it from `0.1.2` to `0.1.3` to indicate a new release.
3. **Execute the Publication Command**:Run the following command to upload the **AAR** to the internal repository.

```
./gradlew publish
```

4. **Update the Dependency in the App**: After the new library version has been published, update the dependency in the app module's `build.gradle.kts` file to point to the new version.

```
dependencies {
    // ...
    implementation("com.bitmovin.player.integration:nielsen-mediametrie-sdk:0.1.3")
}
```
Maintenance and Updates
As an open source project, this library is not part of a regular maintenance or update schedule and is updated on an adhoc basis when contributions are made.

Raising a Feature Suggestion
If you see something missing that might be useful but are unable to contribute the feature yourself, please feel free to submit a feature request through the Bitmovin Community. Feature suggestions will be considered by Bitmovin’s Product team for future roadmap plans.

Reporting a bug
If you come across a bug related to this SDK, please raise this through the support ticketing system accessible in your Bitmovin Dashboard.

Support and SLA Disclaimer
As an open-source project and not a core product offering, any request, issue or query related to this project is excluded from any SLA and Support terms that a customer might have with either Bitmovin or another third-party service provider or Company contributing to this project. Any and all updates are purely at the contributor's discretion.

Need more help?
Should you need further help, please raise your request to your Bitmovin account team. We can assist in a number of ways, from providing you professional services help to putting you in touch with preferred system integrators who can work with you to achieve your goals.
