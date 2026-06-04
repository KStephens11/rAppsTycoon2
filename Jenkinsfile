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
                    bat 'mvnw.cmd -B test jacoco:report'
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
                        allowMissing: false,
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
                    withSonarQubeEnv('LocalSonar') {
                        bat """
                            mvnw.cmd -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar ^
                            -DskipITs ^
                            -Dsonar.projectKey=yuhangzzzz_rapp-tycoon-backend ^
                            -Dsonar.organization=yuhangzzzz
                        """
                    }
                }
            }
        }

        stage('Build Images') {
            steps {
                bat "docker build -t ${IMAGE_BACKEND} ./backend"
                bat "docker build -t ${IMAGE_EVENT_GENERATOR} ./event-generator"
                bat "docker build -t ${IMAGE_FRONTEND} ./frontend"
            }
        }

        stage('Deploy') {
            when {
                expression {
                    env.BRANCH_NAME == 'main' ||
                    env.GIT_BRANCH == 'main' ||
                    env.GIT_BRANCH == 'origin/main' ||
                    env.BRANCH_NAME == 'fix-jenkins' ||
                    env.GIT_BRANCH == 'fix-jenkins' ||
                    env.GIT_BRANCH == 'origin/fix-jenkins'
                }
            }
            steps {
                withCredentials([
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG'),
                    string(credentialsId: 'mysql-root-password', variable: 'MYSQL_ROOT_PASSWORD'),
                    string(credentialsId: 'mysql-password', variable: 'MYSQL_PASSWORD'),
                    string(credentialsId: 'internal-api-key', variable: 'INTERNAL_API_KEY')
                ]) {
                    bat """
                        kubectl create secret generic rapp-secret ^
                            --from-literal=MYSQL_ROOT_PASSWORD=%MYSQL_ROOT_PASSWORD% ^
                            --from-literal=MYSQL_DATABASE=rapptycoon ^
                            --from-literal=MYSQL_USER=rapptycoon ^
                            --from-literal=MYSQL_PASSWORD=%MYSQL_PASSWORD% ^
                            --from-literal=INTERNAL_API_KEY=%INTERNAL_API_KEY% ^
                            --dry-run=client -o yaml | kubectl apply -f -
                    """
                    bat 'kubectl apply -f k8s/configmap.yaml'
                    bat 'kubectl apply -f k8s/mysql-pvc.yaml'
                    bat 'kubectl apply -f k8s/mysql-deployment.yaml'
                    bat 'kubectl apply -f k8s/mysql-service.yaml'
                    bat 'kubectl apply -f k8s/backend-deployment.yaml'
                    bat 'kubectl apply -f k8s/backend-service.yaml'
                    bat 'kubectl apply -f k8s/event-generator-deployment.yaml'
                    bat 'kubectl apply -f k8s/frontend-deployment.yaml'
                    bat 'kubectl apply -f k8s/frontend-service.yaml'
                    bat 'kubectl apply -f k8s/frontend-hpa.yaml'
                    bat "kubectl set image deployment/backend backend=${IMAGE_BACKEND}"
                    bat "kubectl set image deployment/event-generator event-generator=${IMAGE_EVENT_GENERATOR}"
                    bat "kubectl set image deployment/frontend frontend=${IMAGE_FRONTEND}"
                    bat 'kubectl rollout status deployment/backend'
                    bat 'kubectl rollout status deployment/event-generator'
                    bat 'kubectl rollout status deployment/frontend'
                }
            }
        }
    }

    post {
        failure {
            echo "Pipeline failed on branch ${env.BRANCH_NAME ?: env.GIT_BRANCH}, build #${BUILD_NUMBER}"
        }
        success {
            echo "Pipeline succeeded on branch ${env.BRANCH_NAME ?: env.GIT_BRANCH}, build #${BUILD_NUMBER}"
        }
    }
}
