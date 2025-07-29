# Eclipse Kura™ Add-ons Jenkins Shared Libraries

Repository for the Eclipse Kura™ Add-ons Jenkins Shared Libraries.

This repository contains the reusable Jenkins pipelines shared among all the Eclipse Kura™ projects. For sharing the Jenkinsfiles contained in this repository we leverage [Jenkins' "Shared Libraries"](https://www.jenkins.io/doc/book/pipeline/shared-libraries/). Since all the Eclipse Kura™ add-ons pipelines are almost identical, all our pipelines are defined as ["Global Variables"](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#defining-global-variables) within a Jenkins Shared Library. This allows us to share the entire pipelines instead of having a collection of reusable functions in the Shared Library (reference: [this](https://www.jenkins.io/blog/2017/10/02/pipeline-templates-with-shared-libraries/) Jenkins blog post). A "caller script" is still required to call the "Global Variable" and pass the required parameters to the shared pipeline.

### Implemented pipelines

This repo contains the following shared pipelines:
- [Continuous Integration Build pipeline](./vars/continuousIntegrationPipeline.md)

### Divergent add-ons

Among all our maintened add-ons there's a small number of them for which the pipelines diverge significantly. To make up for this we decided to maintain a branch for each of these add-ons. The branches related to these add-ons are named `plugin/*`. The `main` branch contains the shared pipelines for all the other add-ons.

Caller pipelines will need to specify the branch to use when calling the shared pipeline. The branch to use is specified by using the `@` character followed by the branch name. For example, to use the `plugin/foobar` branch, the caller pipeline will need to call the shared pipeline as follows:

```groovy
@Library('add-ons-shared-libs@plugin/foobar') _

node {
    continuousIntegrationPipeline()
}
```

Whenever we need to change something in the `develop` branch and want to propagate the changes to the `plugin/*` branches, we can just leverage our "Backport" Github action.
