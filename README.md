# Bitmovin Android Player Nielsen Measurement SDK

Sample Bitmovin Android Player integration that wires Nielsen App SDK measurement into a reusable library module. The repo includes a demo app showing initialization, consent-aware opt-out, metadata building, and tracking of playback/ads via `NielsenPlayerTracker`.

## Project Overview

- `NielsenSdkManager` — wraps `AppSdk` creation, holds a singleton, and logs SDK callbacks.
- `NielsenInitSettings` — strongly typed init configuration → Nielsen JSON.
- `NielsenMetadata` — builds content metadata (length coercion, `islivestn` mapping, optional Mediametrie fields).
- `NielsenPlayerTracker` — maps Bitmovin events to Nielsen SDK calls; handles playhead pings, ads, and buffering.
- `MyApplication` / `MainActivity` — initialize and forward lifecycle events to resume/pause/end tracking.

## Requirements

- Nielsen App ID for your environment.
- Bitmovin Player license configured.
- User consent handling that meets privacy requirements.

## Nielsen SDK Initialization

### Main Configuration Options

| Setting        | Type    | Required | Description                                                      |
|----------------|---------|----------|------------------------------------------------------------------|
| `appId`        | String  | Yes      | Your Nielsen App ID (e.g. `PXXXXXXXX-...`).                      |
| `optOut`       | Boolean | Yes      | `true` disables measurement (use for consent/opt‑out).           |
| `enableFpid`   | Boolean | No       | Enables First‑Party ID collection. The default value is `true`.  |
| `debugLogging` | Boolean | No       | If `true`, enables Nielsen dev debug (`nol_devDebug = "DEBUG"`). |

## Content Metadata

`NielsenMetadata` builds the content metadata JSONObject expected by Nielsen. Typical fields:

| Field       | Type                       | Required | Notes                                                                                 |
|-------------|----------------------------|----------|---------------------------------------------------------------------------------------|
| `type`      | String                     | Yes      | Nielsen content type (e.g., `"content"`).                                            |
| `assetId`   | String                     | Yes      | Unique content ID.                                                                    |
| `program`   | String                     | Yes      | Program/show name.                                                                    |
| `title`     | String                     | Yes      | Episode/title.                                                                        |
| `length`    | Double?                    | Yes      | Seconds. Non‑positive/NaN/∞ are coerced to a safe live default internally.            |
| `isLivestn` | Boolean                    | Yes      | Converted to `"y"` / `"n"` for `islivestn`.                                           |
| `cli_md`    | MediametrieStreamingType?  | No       | France-specific streaming type (if applicable).                                       |
| `cli_ch`    | String?                    | No       | France channel code (if applicable).                                                  |
| `subbrand`  | String?                    | No       | Optional brand/subbrand.                                                              |

For more details, see the official [Nielsen SDK documentation](https://engineeringportal.nielsen.com/wiki/France_SDK_Metadata#Content_Metadata).

## Module Structure

### App Module (`app`)
- UI logic and Bitmovin player configuration (Compose UI in `ui/theme`, `PlaybackScreen.kt`, `PlayerView.kt`).
- Lifecycle entry points (`MainActivity.kt`, `MyApplication.kt`) and state management (`PlaybackViewModel.kt`).

### Library Module (`nielsen-mediametrie-sdk`)
- `model` — API data models (e.g., `NielsenInitSettings.kt`, `NielsenMetadata.kt`).
- `tracking` — core tracking (`NielsenPlayerTracker.kt`) wiring Bitmovin events to Nielsen.
- `utils` — internals such as `Constants.kt`, `MediametrieStreamingType.kt`.
- `test` — unit tests (e.g., `NielsenPlayerTrackerTest.kt`).

## Consuming the Library

Add the Bitmovin repository and the dependency in your app module:
```kotlin
maven {
    url = uri("https://artifacts.bitmovin.com/artifactory/public-releases")
}

dependencies {
    implementation("com.bitmovin.player.integration:nielsen-mediametrie-sdk:VERSION_NAME")
}
```
Replace `VERSION_NAME` with the released version you want to consume.

## Publishing the Library

### Automated Release Publish (GitHub Actions)
1. Bump the version in `gradle.properties`.
2. Commit the change.
3. Tag the commit (e.g., `vX.Y.Z`) and push the tag.
4. Create a GitHub Release using the same tag to trigger the publish workflow.

Prerequisites: repository secrets `PLAYER_KEY`, `MAVEN_USERNAME`, and `MAVEN_PASSWORD` must be configured.

After publishing, update consumers to the new `VERSION_NAME` in their dependencies.

## Maintenance and Updates

As an open source project, this library is not part of a regular maintenance or update schedule and is updated on an adhoc basis when contributions are made.

## Raising a Feature Suggestion

If you see something missing that might be useful but are unable to contribute the feature yourself, please feel free to submit a feature request through the Bitmovin Community. Feature suggestions will be considered by Bitmovin’s Product team for future roadmap plans.

## Reporting a Bug

If you come across a bug related to this SDK, please raise this through the support ticketing system accessible in your Bitmovin Dashboard.

## Support and SLA Disclaimer

As an open-source project and not a core product offering, any request, issue or query related to this project is excluded from any SLA and Support terms that a customer might have with either Bitmovin or another third-party service provider or Company contributing to this project. Any and all updates are purely at the contributor's discretion.

## Need More Help?

Should you need further help, please raise your request to your Bitmovin account team. We can assist in a number of ways, from providing you professional services help to putting you in touch with preferred system integrators who can work with you to achieve your goals.
