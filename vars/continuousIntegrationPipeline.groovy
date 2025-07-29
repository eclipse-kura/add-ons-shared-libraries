import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

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
    defaultParameters = [
        toolchain: [ jdk: "temurin-jdk17-latest", maven: "apache-maven-3.9.6" ],
        buildType: "install",
        sonarEnable: true,
        sonarExclusions: 'tests/**/*.java',
        pushArtifacts: true
    ]
    pipelineParams = defaultParameters << pipelineParams

    stage ("Pipeline parameters check") {
        // Check buildType is valid string, either "install" or "deploy"
        assert pipelineParams.buildType instanceof String
        assert pipelineParams.buildType.equals("install") || pipelineParams.buildType.equals("deploy")

        // Check toolchain option is set and valid
        valid_jdks = [ "temurin-jdk17-latest" ]
        valid_mavens = [ "apache-maven-3.9.6" ]

        assert pipelineParams.toolchain
        assert pipelineParams.toolchain.jdk instanceof String
        assert pipelineParams.toolchain.maven instanceof String
        assert valid_jdks.contains(pipelineParams.toolchain.jdk)
        assert valid_mavens.contains(pipelineParams.toolchain.maven)
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


    stage ("Archive artifacts") {
        dir("workdir") {
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/*.deb'
        }
    }

    // TODO: Fixme
    stage ("Sonar scan") {
        if (pipelineParams.sonarEnable) {
            timeout(time: 2, unit: 'HOURS') {
                dir("workdir") {
                    withMaven(jdk: 'temurin-jdk17-latest', maven: 'apache-maven-3.9.6', options: [artifactsPublisher(disabled: true)]) {
                        withCredentials([string(credentialsId: 'sonarcloud-token-kura-command', variable: 'SONARCLOUD_TOKEN')]) {
                            withSonarQubeEnv {
                                sh '''
                                    mvn sonar:sonar \
                                        -Dmaven.test.failure.ignore=true \
                                        -Dsonar.organization=eclipse-kura \
                                        -Dsonar.host.url=${SONAR_HOST_URL} \
                                        -Dsonar.token=${SONARCLOUD_TOKEN} \
                                        -Dsonar.pullrequest.branch=${CHANGE_BRANCH} \
                                        -Dsonar.pullrequest.base=${CHANGE_TARGET} \
                                        -Dsonar.pullrequest.key=${CHANGE_ID}\
                                        -Dsonar.java.binaries='target/' \
                                        -Dsonar.core.codeCoveragePlugin=jacoco \
                                        -Dsonar.projectKey=eclipse-kura_kura-command \
                                        -Dsonar.exclusions=tests/**/*.java
                                '''
                            }
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
        if (pipelineParams.sonarEnable) {
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
