pipeline {
    agent any

    environment {
        SONAR_TOKEN = credentials('sonar-token1')
        DOCKER_HOST = 'tcp://localhost:2375'
        IMAGE_BACKEND = "rapp-backend:${BUILD_NUMBER}"
        IMAGE_EVENT_GENERATOR = "rapp-event-generator:${BUILD_NUMBER}"
        IMAGE_FRONTEND = "rapp-tycoon-frontend:${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                dir('backend') {
                    sh './mvnw -B test -DskipITs'
                }
            }
            post {
                always {
                    junit 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('SonarQube') {
            steps {
                dir('backend') {
                    sh """
                        ./mvnw -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                        -Dsonar.projectKey=yuhangzzzz_rapp-tycoon-backend \
                        -Dsonar.organization=yuhangzzzz \
                        -Dsonar.host.url=https://sonarcloud.io \
                        -Dsonar.token=${SONAR_TOKEN}
                    """
                }
            }
        }

        stage('Build Images') {
            steps {
                sh "docker build -t ${IMAGE_BACKEND} ./backend"
                sh "docker build -t ${IMAGE_EVENT_GENERATOR} ./event-generator"
                sh "docker build -t ${IMAGE_FRONTEND} ./frontend"
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                sh "kubectl set image deployment/backend backend=${IMAGE_BACKEND}"
                sh "kubectl set image deployment/event-generator event-generator=${IMAGE_EVENT_GENERATOR}"
                sh "kubectl set image deployment/frontend frontend=${IMAGE_FRONTEND}"
                sh "kubectl rollout status deployment/backend"
                sh "kubectl rollout status deployment/event-generator"
                sh "kubectl rollout status deployment/frontend"
            }
        }
    }

    post {
        failure {
            echo "Pipeline failed on branch ${env.BRANCH_NAME}, build #${BUILD_NUMBER}"
        }
        success {
            echo "Pipeline succeeded on branch ${env.BRANCH_NAME}, build #${BUILD_NUMBER}"
        }
    }
}
