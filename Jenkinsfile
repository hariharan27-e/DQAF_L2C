pipeline {
    agent any

    tools {
        maven 'maven-3.9.16'
        jdk 'jdk-17'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        GOOGLE_CLOUD_PROJECT = 'helix-gmpo-prod'
        GOOGLE_APPLICATION_CREDENTIALS = credentials('bigquery-service-account-key')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Run DQAF Validation Suite') {
            steps {
                sh 'mvn clean test'
            }
        }
    }

    post {
        always {
            publishHTML(target: [
                reportName          : 'DQAF Validation Report',
                reportDir           : 'target/dqaf-report',
                reportFiles         : 'index.html',
                keepAll             : true,
                alwaysLinkToLastBuild: true,
                allowMissing        : false
            ])

            publishHTML(target: [
                reportName          : 'Cucumber Dashboard',
                reportDir           : 'target/cucumber-html-reports',
                reportFiles         : 'overview-features.html',
                keepAll             : true,
                alwaysLinkToLastBuild: true,
                allowMissing        : true
            ])

            archiveArtifacts artifacts: 'target/dqaf-report/**, target/cucumber-reports/**, target/cucumber-html-reports/**',
                              allowEmptyArchive: true
        }
    }
}