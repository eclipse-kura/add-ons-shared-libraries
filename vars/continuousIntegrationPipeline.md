## Continuous Integration Pipeline

**Arguments**:

- `pipelineParams`: Map containing the parameters of the build:
   - [Optional] `toolchain`: The toolchain to be used for the build. Default value: `[ jdk: "temurin-jdk17-latest", maven: "apache-maven-3.9.6" ]`. Available values:
       - **jdk**: `temurin-jdk17-latest`
       - **maven**: `apache-maven-3.9.6`
   - [Optional] `buildType`: Maven build type, either "install" or "deploy". Default value: `install`. **Please note**: The `deploy` goal will only be set when the build is running on the _primary_ branch, for PRs the goal will be set to `install`.
   - [Optional] `sonarEnable`: Select whether to run the Sonar scan or not. `true` by default.
   - [Optional] `sonarExclusions`: Path to be excluded from Sonar analysis. Default value: `tests/**/*.java`
   - [Optional] `sonarOptions`: Additonal maven options to be set during the Sonar scan. Empty by default.

## Example caller pipeline script:

```groovy
@Library "add-ons-shared-libs@main"

node {
    continuousIntegrationPipeline(
        toolchain: [ jdk: "temurin-jdk17-latest", maven: "apache-maven-3.9.6" ]
    )
}
```
