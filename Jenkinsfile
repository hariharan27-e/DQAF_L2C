pipeline {
    agent any

    // Configure these under: Manage Jenkins > Tools
    // (names below must match exactly what you configure there)
    tools {
        maven 'maven-3.9'
jdk 'jdk-17'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        // Billing/quota project used for BigQuery queries.
        // Change this to whichever project your service account can run jobs in.
        GOOGLE_CLOUD_PROJECT = 'helix-gmpo-prod'

        // A GCP service account JSON key uploaded to Jenkins as a
        // "Secret file" credential (Manage Jenkins > Credentials > Add Credentials
        // > Kind: Secret file). Put that credential's ID below.
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
            // Publishes the custom per-table colored report with a permanent
            // Jenkins URL, e.g.:
            // http://<jenkins-host>/job/dqaf-validation-suite/lastBuild/DQAF_20Validation_20Report/
            publishHTML(target: [
                reportName          : 'DQAF Validation Report',
                reportDir           : 'target/dqaf-report',
                reportFiles         : 'index.html',
                keepAll             : true,
                alwaysLinkToLastBuild: true,
                allowMissing        : false
            ])

            // Publishes the standard Cucumber Masterthought dashboard too.
            publishHTML(target: [
                reportName          : 'Cucumber Dashboard',
                reportDir           : 'target/cucumber-html-reports',
                reportFiles         : 'overview-features.html',
                keepAll             : true,
                alwaysLinkToLastBuild: true,
                allowMissing        : true
            ])

            // Keeps a raw copy of every report on the build's Jenkins page too,
            // downloadable as a zip from "Build Artifacts".
            archiveArtifacts artifacts: 'target/dqaf-report/**, target/cucumber-reports/**, target/cucumber-html-reports/**',
                              allowEmptyArchive: true
        }
    }
}
