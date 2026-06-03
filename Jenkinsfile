pipeline {
    agent any

    environment {
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
                    sh './mvnw -B test jacoco:report'
                }
            }
            post {
                always {
                    junit 'backend/target/surefire-reports/*.xml'
                    jacoco(
                        execPattern: '**/target/jacoco.exec',
                        classPattern: '**/target/classes',
                        sourcePattern: '**/src/main/java',
                        minimumLineCoverage: '60',
                        minimumBranchCoverage: '60'
                    )
                    publishHTML(target: [
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'backend/target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Code Coverage'
                    ])
                }
            }
        }

        stage('SonarQube') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('SonarCloud') {
                        sh """
                            ./mvnw -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                            -DskipITs \
                            -Dsonar.projectKey=yuhangzzzz_rapp-tycoon-backend \
                            -Dsonar.organization=yuhangzzzz
                        """
                    }
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
                sh 'kubectl apply -f k8s/secret.yaml'
                sh 'kubectl apply -f k8s/configmap.yaml'
                sh 'kubectl apply -f k8s/mysql-pvc.yaml'
                sh 'kubectl apply -f k8s/mysql-deployment.yaml'
                sh 'kubectl apply -f k8s/mysql-service.yaml'
                sh 'kubectl apply -f k8s/backend-deployment.yaml'
                sh 'kubectl apply -f k8s/backend-service.yaml'
                sh 'kubectl apply -f k8s/event-generator-deployment.yaml'
                sh 'kubectl apply -f k8s/frontend-deployment.yaml'
                sh 'kubectl apply -f k8s/frontend-service.yaml'
                sh 'kubectl apply -f k8s/frontend-hpa.yaml'
                sh "kubectl set image deployment/backend backend=${IMAGE_BACKEND}"
                sh "kubectl set image deployment/event-generator event-generator=${IMAGE_EVENT_GENERATOR}"
                sh "kubectl set image deployment/frontend frontend=${IMAGE_FRONTEND}"
                sh 'kubectl rollout status deployment/backend'
                sh 'kubectl rollout status deployment/event-generator'
                sh 'kubectl rollout status deployment/frontend'
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
