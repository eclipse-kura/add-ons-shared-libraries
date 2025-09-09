def call(String repoDistribution, String repoModule, Boolean setupPromotion = false) {
    def DEV_REPO = "kura-develop-deb"
    def PROD_REPO = "kura-deb"
    def DEB_COMPONENT = "main"
    def VALID_DEB_ARCHITECTURES = ["amd64", "arm64", "all"]

    stage ("Upload packages parameters check") {

        echo "Distribution: ${repoDistribution}"
        echo "Module: ${repoModule}"

        // Check "distribution" parameter is set and valid
        assert repoDistribution instanceof String
        assert repoDistribution ==~ /kura-\d+/

        // Check "module" parameter is set and valid
        def valid_modules = [
            "base"
        ]

        assert repoModule instanceof String
        assert valid_modules.contains(repoModule)
    }

    stage("Upload setup") {
        server = Artifactory.server 'artifactory'

        withCredentials([usernamePassword(credentialsId: 'artifactory-jenkins-esf-apitoken', passwordVariable: 'password', usernameVariable: 'username')]) {
            server.username = "${username}"
            server.password = "${password}"
        }

        dir("workdir") {
            // Traceability info
            GIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        }

    }

    stage("Upload .deb packages to Artifactory") {
        def debFilesOutput = sh(script: "find workdir -type f -name '*.deb'", returnStdout: true).trim()
        def debFiles = debFilesOutput ? debFilesOutput.split("\n") : []

        debFiles.each {
            // Split file name to get the architecture
            def fileName = it.toString().split("/").last()
            def architecture = fileName.split("_").last().split("\\.")[0]
            assert VALID_DEB_ARCHITECTURES.contains(architecture)

            withCredentials([usernameColonPassword(credentialsId: 'repo.eclipse.org-bot-account', variable: 'USERPASS')]) {
                sh '''
                curl -u "$USERPASS" -H "Content-Type: multipart/form-data" --data-binary "@./${it}" "https://repo3.eclipse.org/repository/kura-apt/"
                '''
            }
        }

        if (setupPromotion) {
            // TODO
        }

    }
}
