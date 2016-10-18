#!/usr/bin/env groovy

// these wrappers don't seem to be built in yet
void ansicolor(Closure<Void> wrapped) {
  wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm', 'defaultFg': 1, 'defaultBg': 2]) {
    wrapped.call()
  }
}
void xvfb(Closure<Void> wrapped) {
  wrap([$class: 'Xvfb']) {
    wrapped.call()
  }
}

void withPorts(Closure<Void> wrapped) {
  def ports = sh(script: 'etc/scripts/allocate-jboss-ports', returnStdout: true)
  withEnv(ports.trim().readLines()) {
    wrapped.call()
  }
}

void printNode() {
  println "running on node ${env.NODE_NAME}"
}

def notifyStarted() {
  hipchatSend color: "GRAY", notify: true, message: "STARTED: Job <a href=\"${env.BUILD_URL}\">${env.JOB_NAME} #${env.BUILD_NUMBER}</a>"
}

// def notifyPassed(appserver) {
//   hipchatSend color: "GRAY", notify: true, message: "TESTS PASSED ($appserver): Job ${env.JOB_NAME} [${env.BUILD_NUMBER}] (${env.BUILD_URL})"
// }

// def notifySuccessful() {
//   hipchatSend color: "GREEN", notify: true, message: "SUCCESSFUL: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}] (${env.BUILD_URL})"
// }

// def notifyFailed() {
//   hipchatSend color: "RED", notify: true, message: "FAILED: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}] (${env.BUILD_URL})"
// }

timestamps {
  node {
    ansicolor {
      stage('Checkout') {
        printNode()
        notifyStarted()
        // Checkout code from repository
        checkout scm
      }

      stage('Install build tools') {
        printNode()
        // TODO yum install the following
        // Xvfb libaio xorg-x11-server-Xvfb wget unzip git-core
        // https://dl.google.com/linux/direct/google-chrome-stable_current_x86_64.rpm
        // gcc-c++
        // java-devel; set alternatives for java
        // groovy firefox rpm-build docker
        // download chromedriver

        // Note: if next step happens on another node, mvnw might have to download again
        sh "./mvnw --version"
      }

      // TODO build and archive binaries with unit tests, then in parallel, unarchive and run:
      //   arquillian tests
      //   eap, wildfly functional tests
      //   mysql 5.6, functional tests (on wildfly)
      //   and (later) mariadb 10 functional tests (on wildfly)


      stage('Build') {
        printNode()
        sh """./mvnw clean package \
                     --batch-mode \
                     --settings .travis-settings.xml \
                     --update-snapshots \
                     -Dmaven.test.failure.ignore \
                     -DstaticAnalysis \
                     -Dchromefirefox \
                     -DskipFuncTests \
                     -DskipArqTests \
-DexcludeFrontend \
        """
// TODO remove -DexcludeFrontend (just for faster testing)

// TODO build zanata-test-war but don't run functional tests (need to modify zanata-test-war pom)

        def testFiles = '**/target/surefire-reports/TEST-*.xml'
        setJUnitPrefix("UNIT", testFiles)
        junit testFiles

        // TODO notify if compile+unit test successful

        archive '**/target/*.war'
//        archive '**/target/*.jar, **/target/*.war'
      }

      stage('stash') {
        stash name: 'workspace', includes: '**'
      }
    }
  }

  stage('Parallel tests') {
    def tasks = [:]
    tasks['Integration tests: wildfly'] = {
      node {
        ansicolor {
          unstash 'workspace'
          printNode()
          integrationTests('wildfly8')
        }
      }
    }
    tasks['Integration tests: jbosseap'] = {
      node {
        ansicolor {
          unstash 'workspace'
          printNode()
          integrationTests('jbosseap6')
        }
      }
    }
    tasks.failFast = true
    parallel tasks
  }
  // TODO in case of failure, notify culprits via IRC and/or email
  // https://wiki.jenkins-ci.org/display/JENKINS/Email-ext+plugin#Email-extplugin-PipelineExamples
  // http://stackoverflow.com/a/39535424/14379
  // IRC: https://issues.jenkins-ci.org/browse/JENKINS-33922
  // possible alternatives: Slack, HipChat, RocketChat, Telegram?
}

def archiveTestResultsIfUnstable() {
  // if tests have failed currentBuild.result will be 'UNSTABLE'
  if (currentBuild.result != null) {
      archive(
        includes: '*/target/**/*.log,*/target/screenshots/**,**/target/site/jacoco/**,**/target/site/xref/**',
        excludes: '**/BACKUP-*.log')
  }
}

def integrationTests(def appserver) {
  xvfb {
    withPorts {
      // Run the maven build
      sh """./mvnw verify \
                   --batch-mode \
                   --settings .travis-settings.xml \
                   -DstaticAnalysis=false \
                   -Dcheckstyle.skip \
                   -Dappserver=$appserver \
                   -DallFuncTests \
                   -Dmaven.test.failure.ignore \
                   -Dmaven.main.skip \
                   -Dgwt.compiler.skip \
                   -Dmaven.war.skip \
-DexcludeFrontend \
      """
      // -Dmaven.war.skip (but we might need zanata-test-war)

      // TODO might need some or all of these:
      // -Dfunctional-test - probably obsolete
      // -Dwebdriver.display=$DISPLAY
      // -Dcargo.debug.jvm.args= -Dwebdriver.type=chrome -Dwebdriver.chrome.driver=/opt/chromedriver

    }
  }
  def testFiles = '**/target/failsafe-reports/TEST-*.xml'
  setJUnitPrefix(appserver.toUpperCase(), testFiles)
  junit testFiles
  archiveTestResultsIfUnstable()
}

// from https://issues.jenkins-ci.org/browse/JENKINS-27395?focusedCommentId=256459&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-256459
def setJUnitPrefix(prefix, files) {
  // add prefix to qualified classname
  sh "shopt -s globstar && sed -i \"s/\\(<testcase .*classname=['\\\"]\\)\\([a-z]\\)/\\1${prefix}.\\2/g\" $files"
}
