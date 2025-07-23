/*
 * Copyright 2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@Library('gematik-jenkins-shared-library')

def DEV_BRANCH = 'main'
def BUILD_DESCRIPTION = 'SNAPSHOT BUILD'
def dockerRegistry = "europe-west3-docker.pkg.dev/gematik-objectstore-dev"

String DOCKER_LATEST_TAG = 'latest'
String IMAGE_NAME = 'e-rezept/omem-poc'
String IMAGE_VERSION = "1.0.0"
def BUILD_DATE = new Date().format("dd-MM-yyyy'T'HH:mm")


pipeline {

    agent { label 'k8-maven-medium' }

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '3'))
    }

    tools {
        maven 'Default'
    }

    stages {
        stage('Initialise') {
            steps {
                useJdk('OPENJDK17')
            }
        }
        /*stage('Verify Copyright Header') {
            steps {
                verifyCopyrightHeaderExistsInFilesOfType("*.java")
                verifyCopyrightHeaderExistsInFilesOfType("*.feature")
            }
        }
        stage('Check Code Style') {
            steps {
                sh "mvn spotless:check"
            }
        }*/
        stage('build') {
            steps {
                script {
                    try {
                        currentBuild.displayName = "${BUILD_DESCRIPTION}"
                        mavenBuild("pom.xml", "-Pdist")
                    } finally {
                        archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/*.jar', fingerprint: true
                    }
                }

            }
        }
        stage('test') {
            steps {
                mavenTest()
            }
        }
        /*stage('sonar') {
            steps {
                mavenCheckWithSonarQube()
            }
        }*/
        /*stage ('deploy to nexus') {
            when {
                anyOf {
                    branch DEV_BRANCH
                }
            }
            steps {
                mavenDeploy()
            }
        }*/
        stage('Docker Build') {
            when {
                anyOf {
                    branch DEV_BRANCH
                }
            }
            steps {
                script {
                    def buildDir = "./"
                    def dockerFile = "Dockerfile"
                    //def dockerRegistry = dockerGetGematikRegistry()
                    def buildArgs = "--build-arg BUILD_DATE=${BUILD_DATE}"
                    dockerBuild(IMAGE_NAME, DOCKER_LATEST_TAG, IMAGE_VERSION, buildArgs, dockerFile, dockerRegistry, buildDir)
                }
            }
        }


        stage('Docker Push') {
            when {
                anyOf {
                    branch DEV_BRANCH
                }
            }
            steps {
                dockerPushImage(IMAGE_NAME, DOCKER_LATEST_TAG, '', dockerRegistry)
            }
        }

    }
    post {
        always {
            // == Test-Reports in Jenkins ==
            // JUnit Plugin
            step([$class: 'JUnitResultArchiver', allowEmptyResults: true, testResults: '**/TEST-*.xml'])
            dockerRemoveLocalImage(IMAGE_NAME, DOCKER_LATEST_TAG)
        }
        failure {
            sendEMailNotification("a.ibrokhimov@gematik.de")
        }
    }
}
