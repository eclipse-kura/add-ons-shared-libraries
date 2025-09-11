import static groovy.json.JsonOutput.toJson
import static groovy.json.JsonOutput.prettyPrint

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import java.util.regex.Pattern

def boolean onlyDocumentationFilesChangedIn(String workDirectory) {
    if (!env.CHANGE_TARGET) {
        echo "CHANGE_TARGET not set. Skipping check"
        return false
    }

    def changedFiles = sh(script: "cd ${workDirectory} && git diff --name-only origin/${env.CHANGE_TARGET} origin/${env.BRANCH_NAME}", returnStdout: true).trim().split("\n")

    echo "Changed files: ${changedFiles}" // Debug

    return changedFiles && changedFiles.every { it.endsWith(".md") || it.endsWith(".txt") }
}

def call(Map pipelineParams = [:]) {

    properties([
        disableConcurrentBuilds(abortPrevious: true),
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '2', daysToKeepStr: '', numToKeepStr: '5')),
        gitLabConnection('gitlab.eclipse.org'),
        [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
        [$class: 'JobLocalConfiguration', changeReasonComment: '']
    ])

    // Populate keys that are not set with default parameters
    def defaultParameters = [
        toolchain: [ jdk: "temurin-jdk17-latest", maven: "apache-maven-3.9.6" ],
        buildType: "install",
        sonar: [ enable: false, projectKey: null, tokenId: null, exclusions: null ],
        pushArtifacts: true
    ]
    pipelineParams = defaultParameters << pipelineParams

    stage ("Pipeline parameters check") {
        // Print effective pipeline parameters for debugging
        echo "Pipeline parameters:"
        println prettyPrint(toJson(pipelineParams))

        // Check buildType is valid string, either "install" or "deploy"
        assert pipelineParams.buildType instanceof String
        assert pipelineParams.buildType.equals("install") || pipelineParams.buildType.equals("deploy")

        // Check toolchain option is set and valid
        def valid_jdks = [ "temurin-jdk17-latest" ]
        def valid_mavens = [ "apache-maven-3.9.6" ]

        assert pipelineParams.toolchain
        assert pipelineParams.toolchain.jdk instanceof String
        assert pipelineParams.toolchain.maven instanceof String
        assert valid_jdks.contains(pipelineParams.toolchain.jdk)
        assert valid_mavens.contains(pipelineParams.toolchain.maven)

        // Check sonar configuration is set and valid
        assert pipelineParams.sonar
        assert pipelineParams.sonar.enable instanceof Boolean

        // If sonar is enabled, ensure required fields are not empty
        if (pipelineParams.sonar.enable) {
            assert pipelineParams.sonar.projectKey instanceof String
            assert pipelineParams.sonar.tokenId instanceof String
            assert !pipelineParams.sonar.projectKey.isEmpty() : "sonar.projectKey cannot be empty when sonar is enabled"
            assert !pipelineParams.sonar.tokenId.isEmpty() : "sonar.tokenId cannot be empty when sonar is enabled"

            // Validate that projectKey contains only safe characters
            assert pipelineParams.sonar.projectKey.matches(/^[a-zA-Z0-9_-]+$/) : "sonar.projectKey contains invalid characters. Only alphanumeric, underscores and hyphens are allowed"
            // Validate that exclusions don't contain potentially dangerous characters
            assert pipelineParams.sonar.exclusions instanceof String
            assert !pipelineParams.sonar.exclusions.matches(/.*[;&|`$(){}\[\]\"].*/) : "sonar.exclusions contains potentially dangerous characters"
        }
    }

    stage ("Checkout repository") {
        dir("workdir") {
            checkout scm
        }
    }

    // Skip build if target branch is a documentation branch
    if (env.CHANGE_TARGET && env.CHANGE_TARGET.startsWith("docs-")) {
        echo "Skipping build for documentation branch"
        currentBuild.result = 'SUCCESS'
        return
    }

    // Skip build if only documentation files (i.e. *.md and *.txt) have changed
    if (onlyDocumentationFilesChangedIn("workdir")) {
        echo "Skipping build for documentation changes"
        currentBuild.result = 'SUCCESS'
        return
    }

    stage ("Build") {
        // Only perform deploy if on main branch
        def mavenBuildType = pipelineParams.buildType
        if (!env.BRANCH_IS_PRIMARY && pipelineParams.buildType.equals("deploy")) {
            echo "Skipping deploy for non-main branch"
            mavenBuildType = "install"
        }

        timeout(time: 2, unit: 'HOURS') {
            dir("workdir") {
                withMaven(
                    jdk: pipelineParams.toolchain.jdk,
                    maven: pipelineParams.toolchain.maven,
                    options: [artifactsPublisher(disabled: true)]
                ) {
                    sh "mvn clean ${mavenBuildType}"
                }
            }
        }
    }

    stage ("Deploy on Nexus Repository") {
        // Call uploadPackages only if we are on the default branch,
        // if we have DEB packages to upload and if the user has set the pushArtifacts parameter to true
        // if (debFiles && env.BRANCH_IS_PRIMARY && pipelineParams.pushArtifacts) {
        if (true) {
            echo "Uploading DEB packages..."

            def repoDistribution
            def repoModule

            withMaven(
                jdk: pipelineParams.toolchain.jdk,
                maven: pipelineParams.toolchain.maven,
                options: [artifactsPublisher(disabled: true)]
            ) {
                repoDistribution = sh(script: '''
                    mvn -f workdir/distrib/pom.xml \
                        -Dexec.executable=echo \
                        -Dexec.args="${kura.repo.distribution}" \
                        -q exec:exec --non-recursive
                ''', returnStdout: true).trim()

                repoModule = sh(script: '''
                    mvn -f workdir/distrib/pom.xml \
                    -Dexec.executable=echo \
                    -Dexec.args="${kura.repo.module}" \
                    -q exec:exec --non-recursive
                ''', returnStdout: true).trim()
            }

            uploadPackages(repoDistribution, repoModule)
        } else {
            echo "Skipping DEB packages upload."
            Utils.markStageSkippedForConditional(STAGE_NAME)
        }
    }

    stage ("Archive artifacts") {
        dir("workdir") {
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/*.deb'
        }
    }

    stage ("Sonar scan") {
        if (pipelineParams.sonar.enable) {
            timeout(time: 2, unit: 'HOURS') {
                dir("workdir") {
                    withMaven(jdk: 'temurin-jdk17-latest', maven: 'apache-maven-3.9.6', options: [artifactsPublisher(disabled: true)]) {
                        withSonarQubeEnv( credentialsId: pipelineParams.sonar.tokenId ) {

                            // Check if on primary branch
                            def analysisParameters = ""
                            if (isPullRequest()) {
                                analysisParameters = "-Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} -Dsonar.pullrequest.base=${env.CHANGE_TARGET} -Dsonar.pullrequest.key=${env.CHANGE_ID}"
                            } else {
                                analysisParameters = "-Dsonar.branch.name=${env.BRANCH_NAME}"
                            }

                            sh """
                                mvn sonar:sonar \
                                    -Dmaven.test.failure.ignore=true \
                                    -Dsonar.organization=eclipse-kura \
                                    -Dsonar.host.url=${SONAR_HOST_URL} \
                                    -Dsonar.java.binaries='target/' \
                                    ${analysisParameters} \
                                    -Dsonar.core.codeCoveragePlugin=jacoco \
                                    -Dsonar.projectKey=${pipelineParams.sonar.projectKey} \
                                    -Dsonar.exclusions=${pipelineParams.sonar.exclusions}
                            """
                        }
                    }
                }
            }
        } else {
            echo "Sonar scan disabled. Skipping"
            Utils.markStageSkippedForConditional(STAGE_NAME)
        }
    }

    stage('Sonar quality gate') {
        if (pipelineParams.sonar.enable) {
            sleep(30) // Wait for Sonar to complete its scan
            timeout(time: 1, unit: 'MINUTES') {
                def qg = waitForQualityGate()
                if (qg.status != 'OK') {
                    error "Pipeline aborted due to sonar quality gate failure: ${qg.status}"
                }
            }
        } else {
            echo "Sonar scan disabled. Skipping"
            Utils.markStageSkippedForConditional(STAGE_NAME)
        }
    }
}

private Boolean isPullRequest(){
    return env.CHANGE_ID
}
