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

    stage("Upload .deb packages to Artifactory") {
        def debFilesOutput = sh(script: "find workdir -type f -name '*.deb'", returnStdout: true).trim()
        def debFiles = debFilesOutput ? debFilesOutput.split("\n") : []

        // Debug print all found .deb files
        echo "Found .deb files:"
        debFiles.each { echo it.toString() }
        assert debFiles.size() > 0
        echo "Total .deb files found: ${debFiles.size()}"

        debFiles.each {
            // Split file name to get the architecture
            def fileName = it.toString().split("/").last()
            def architecture = fileName.split("_").last().split("\\.")[0]
            assert VALID_DEB_ARCHITECTURES.contains(architecture)

            // Print filename and architecture
            echo "Uploading file: ${fileName} with architecture: ${architecture}"

            withCredentials([usernameColonPassword(credentialsId: 'repo.eclipse.org-bot-account', variable: 'USERPASS')]) {
                sh(
                    script: "curl -u \"\$USERPASS\" -H \"Content-Type: multipart/form-data\" --data-binary \"@./${it}\" \"https://repo3.eclipse.org/repository/kura-apt/\"",
                    returnStatus: true
                )
            }
        }

        if (setupPromotion) {
            // TODO
        }

    }
}
