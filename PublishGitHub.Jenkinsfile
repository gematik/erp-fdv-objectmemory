@Library('gematik-jenkins-shared-library') _

def CREDENTIAL_ID_GEMATIK_GIT = 'svc_gitlab_prod_credentials'
//def REPO_URL_GITLAB = createGitUrl('git/Verwaltung/ospo/github-startpage')
def REPO_URL_GITLAB = createGitUrl('git/erezept/e-rezept-app-poc-objektspeicher')
def BRANCH_GITLAB = 'main'
def GITHUB_SSH_KEY = "GITHUB_PRIVATE_KEY"
//def REPO_URL_GITHUB = "git@github.com:gematik/gematik.github.io.git"
def REPO_URL_GITHUB = "git@github.com:gematik/erp-fdv-objectmemory.git"
def BRANCH_GITHUB = 'main'
def POM_PATH = 'pom.xml'

pipeline {
    options {
        disableConcurrentBuilds()
    }
    agent { label 'k8-backend-small' }

    environment {
        COMMITTER_NAME = "gematik-Entwicklung"
        COMMITTER_EMAIL = "software-development@gematik.de"
    }

    stages {
        stage('Initialize') {
            steps {
                useJdk('OPENJDK17')
                gitSetIdentity()
            }
        }
        stage('Check Guidelines') {
            steps {
                dir("source") {
                    script {
                        openSourceGuidelineCheck()
                    }
                }
            }
        }
        stage("Apply .githubignore rules") {
            steps {
                dir("target") {
                    sh(libraryResource('applyGithubIgnoreRules.sh'))
                }
            }
        }

        stage('Checkout Branch GitLab') {
            steps {
                git branch: BRANCH_GITLAB,
                        credentialsId: CREDENTIAL_ID_GEMATIK_GIT,
                        url: REPO_URL_GITLAB
            }
        }

        stage('Build Object Memory') {
            steps {
                mavenBuild(POM_PATH)
            }
        }

        stage('Checkout Branch GitHub') {
            steps {
                gitLoadSshKey(GITHUB_SSH_KEY, "github")
                dir("target") {
                    git branch: BRANCH_GITHUB,
                            url: REPO_URL_GITHUB
                }
            }
        }
    }

    post {
        always {
            sendEMailNotification("a.ibrokhimov@gematik.de")
        }
    }
}