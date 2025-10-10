def call(String repoDistribution, String repoModule, Boolean setupPromotion = false) {
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
        def debFiles = findFiles(glob: 'workdir/**/*.deb')

        if (debFiles.size() == 0) {
            error("No .deb files found to upload")
        }

        debFiles.each {
            withCredentials([usernameColonPassword(credentialsId: 'repo.eclipse.org-bot-account', variable: 'USERPASS')]) {
                int status = sh(
                    script: """
                        curl -u \"\$USERPASS\" \
                        -w '%{http_code}' \
                        -H \"Content-Type: multipart/form-data\" \
                        --data-binary \"@./${it}\" \
                        \"https://repo3.eclipse.org/repository/kura-apt-dev/\" \
                        -o /dev/null
                    """,
                    returnStatus: true
                ).trim()
            }

            if (status != 200) {
                error("Returned status code = $status")
            }
        }

        if (setupPromotion) {
            // TODO
        }
    }
}
