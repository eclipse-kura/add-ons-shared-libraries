## Continuous Integration Pipeline

**Arguments**:

`pipelineParams`: Map containing the parameters of the build:
  - [Optional] `buildType`: Maven build type, either `install` or `deploy`. Default value: `install`. **Please note**: The `deploy` goal will only be set when the build is running on the _primary_ branch, for PRs the goal will be set to `install`.
  - [Optional] `pushArtifacts`: Publish the artifacts on debian repository, if found. Default value: `true`.
  - [Optional] `sonar`: Map containing Sonar scan parameters:
     - `enable`: Enables/Disables the Sonar scan steps. Default value: `false`.
     - `tokenId`: Jenkins Credentials Id of the corresponding Sonar token used for authentication. It is provided by Eclipse Foundation devops when the Sonar project is created and its credentials are loaded in the Jenkins instance.  **Must be set if Sonar scan is enabled**
     - `projectKey`: Sonar unique identifier for the project. Can be found on the SonarQube instance. **Must be set if Sonar scan is enabled**
     - `exclusions`: Path to be excluded from Sonar analysis. Default value: `tests/**/*.java`
  - [Optional] `toolchain`: The toolchain to be used for the build. Default value: `[ jdk: "temurin-jdk17-latest", maven: "apache-maven-3.9.6" ]`. Available values:
     - **jdk**: `temurin-jdk17-latest`
     - **maven**: `apache-maven-3.9.6`

## Example caller pipeline script:

```groovy
@Library "add-ons-shared-libs@develop"

node {
    continuousIntegrationPipeline(
        sonar: [
          enable: true,
          projectKey: "eclipse-kura_kura-something",
          tokenId: "sonarcloud-token-kura-something"
        ]
    )
}
```
