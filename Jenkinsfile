pipeline {
  agent any
  stages {
    stage('Test') {
      steps {
        sh 'lein do clean, test'
      }
    }
    stage('Docs') {
      steps {
        sh 'lein do doc'
        sh 'publish-docs.sh irulan'
      }
    }
    stage('Deploy') {
      steps {
        sh 'lein do deploy'
        script {
          def vsn = sh(returnStdout: true, script: 'lein project-version').trim()
          def commit = sh(returnStdout: true, script: 'git log -1 | grep -v Date:').trim()
          slackSend (color: '#0088ff', message: "Deployed: *Irulan* ${vsn} (<${env.BUILD_URL}|Open>)\n${commit}")
        }
      }
    }
  }
}