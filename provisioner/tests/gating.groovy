pipeline {

    agent { label 'jenkins-jnlp-agent' }

    stages {

        stage('Build Information') {
            steps {
                script {
                    TOWER_VERSION = '3.5.3'
                }
                echo """Tower Version under test: ${TOWER_VERSION}
Workshop branch under test: ${env.BRANCH_NAME} | ${env.CHANGE_NAME}
Build Tag: ${env.BUILD_TAG}"""
            }
        }

        stage('Prep Environment') {
            steps {
                withCredentials([file(credentialsId: 'workshops_tower_license', variable: 'TOWER_LICENSE')]) {
                    sh 'cp ${TOWER_LICENSE} provisioner/tower_license.json'
                }
                sh 'pip install netaddr'
                sh 'yum -y install sshpass'
                sh 'ansible --version | tee ansible_version.log'
                archiveArtifacts artifacts: 'ansible_version.log'
                script {
                    if (env.CHANGE_ID) {
                        ANSIBLE_WORKSHOPS_REFSPEC = "+refs/pull/${env.CHANGE_ID}/head:refs/remotes/origin/${env.BRANCH_NAME}"
                    } else {
                        ANSIBLE_WORKSHOPS_REFSPEC = "+refs/heads/${env.BRANCH_NAME}:refs/remotes/origin/${env.BRANCH_NAME}"
                    }

                    if (TOWER_VERSION == 'devel') {
                        tower_installer_url = "${AWX_NIGHTLY_REPO_URL}/${TOWER_VERSION}/setup/ansible-tower-setup-latest.tar.gz"
                        gpgcheck = 0
                        aw_repo_url = "${AWX_NIGHTLY_REPO_URL}/${TOWER_VERSION}"
                    } else {
                        tower_installer_url = "https://releases.ansible.com/ansible-tower/setup/ansible-tower-setup-${TOWER_VERSION}-1.tar.gz"
                        gpgcheck = 1
                        aw_repo_url = "https://releases.ansible.com/ansible-tower"
                    }
                }

                sh """tee provisioner/tests/ci-common.yml << EOF
tower_installer_url: ${tower_installer_url}
gpgcheck: ${gpgcheck}
aw_repo_url: ${aw_repo_url}
ansible_workshops_refspec: ${ANSIBLE_WORKSHOPS_REFSPEC}
EOF
"""
                sh """tee provisioner/tests/ci-rhel.yml << EOF
workshop_type: rhel
ec2_name_prefix: tower-qe-rhel-tower-${TOWER_VERSION}-${env.BRANCH_NAME}-${env.BUILD_ID}
EOF
"""

                sh """tee provisioner/tests/ci-networking.yml << EOF
workshop_type: networking
ec2_name_prefix: tower-qe-networking-tower-${TOWER_VERSION}-${env.BRANCH_NAME}-${env.BUILD_ID}
EOF
"""

                sh """tee provisioner/tests/ci-f5.yml << EOF
workshop_type: f5
ec2_name_prefix: tower-qe-f5-tower-${TOWER_VERSION}-${env.BRANCH_NAME}-${env.BUILD_ID}
EOF
"""
            }
        }

        stage('Workshop Type') {
            parallel {
                stage('RHEL') {
                    steps {
                        script {
                            stage('RHEL-deploy') {
                                withCredentials([string(credentialsId: 'workshops_aws_access_key', variable: 'AWS_ACCESS_KEY'),
                                                 string(credentialsId: 'workshops_aws_secret_key', variable: 'AWS_SECRET_KEY')]) {
                                    withEnv(["AWS_SECRET_KEY=${AWS_SECRET_KEY}",
                                             "AWS_ACCESS_KEY=${AWS_ACCESS_KEY}",
                                             "ANSIBLE_CONFIG=provisioner/ansible.cfg",
                                             "ANSIBLE_FORCE_COLOR=true"]) {
                                        sh """ansible-playbook provisioner/provision_lab.yml \
                                               -e @provisioner/tests/vars.yml \
                                               -e @provisioner/tests/ci-common.yml \
                                               -e @provisioner/tests/ci-rhel.yml"""
                                    }
                                }
                            }
                        }
                        script {
                            stage('RHEL-teardown') {
                                withCredentials([string(credentialsId: 'workshops_aws_access_key', variable: 'AWS_ACCESS_KEY'),
                                                 string(credentialsId: 'workshops_aws_secret_key', variable: 'AWS_SECRET_KEY')]) {
                                    withEnv(["AWS_SECRET_KEY=${AWS_SECRET_KEY}",
                                             "AWS_ACCESS_KEY=${AWS_ACCESS_KEY}",
                                             "ANSIBLE_CONFIG=provisioner/ansible.cfg",
                                             "ANSIBLE_FORCE_COLOR=true"]) {
                                        sh """ansible-playbook provisioner/teardown_lab.yml \
                                                -e @provisioner/tests/vars.yml \
                                                -e @provisioner/tests/ci-rhel.yml"""
                                    }
                                }
                            }
                        }
                    }
                }

                stage('Networking') {
                    steps {
                        script {
                            stage('networking-deploy') {
                                withCredentials([string(credentialsId: 'workshops_aws_access_key', variable: 'AWS_ACCESS_KEY'),
                                                 string(credentialsId: 'workshops_aws_secret_key', variable: 'AWS_SECRET_KEY')]) {
                                    withEnv(["AWS_SECRET_KEY=${AWS_SECRET_KEY}",
                                             "AWS_ACCESS_KEY=${AWS_ACCESS_KEY}",
                                             "ANSIBLE_CONFIG=provisioner/ansible.cfg",
                                             "ANSIBLE_FORCE_COLOR=true"]) {
                                        sh """ansible-playbook provisioner/provision_lab.yml \
                                                -e @provisioner/tests/vars.yml \
                                                -e @provisioner/tests/ci-common.yml \
                                                -e @provisioner/tests/ci-networking.yml"""
                                    }
                                }
                            }
                        }
                        script {
                            stage('networking-teardown') {
                                withCredentials([string(credentialsId: 'workshops_aws_access_key', variable: 'AWS_ACCESS_KEY'),
                                                 string(credentialsId: 'workshops_aws_secret_key', variable: 'AWS_SECRET_KEY')]) {
                                    withEnv(["AWS_SECRET_KEY=${AWS_SECRET_KEY}",
                                             "AWS_ACCESS_KEY=${AWS_ACCESS_KEY}",
                                             "ANSIBLE_CONFIG=provisioner/ansible.cfg",
                                             "ANSIBLE_FORCE_COLOR=true"]) {
                                        sh """ansible-playbook provisioner/teardown_lab.yml \
                                                -e @provisioner/tests/vars.yml \
                                                -e @provisioner/tests/ci-networking.yml"""
                                    }
                                }
                            }
                        }
                    }
                }

                stage('F5') {
                    steps {
                        script {
                            stage('F5-deploy') {
                                withCredentials([string(credentialsId: 'workshops_aws_access_key', variable: 'AWS_ACCESS_KEY'),
                                                 string(credentialsId: 'workshops_aws_secret_key', variable: 'AWS_SECRET_KEY')]) {
                                    withEnv(["AWS_SECRET_KEY=${AWS_SECRET_KEY}",
                                             "AWS_ACCESS_KEY=${AWS_ACCESS_KEY}",
                                             "ANSIBLE_CONFIG=provisioner/ansible.cfg",
                                             "ANSIBLE_FORCE_COLOR=true"]) {
                                        sh """ansible-playbook provisioner/provision_lab.yml \
                                                -e @provisioner/tests/vars.yml \
                                                -e @provisioner/tests/ci-common.yml \
                                                -e @provisioner/tests/ci-f5.yml"""
                                    }
                                }
                            }
                        }
                        script {
                            stage('F5-exercises') {
                                sh "cat provisioner/tower-qe-f5-tower-${TOWER_VERSION}-${env.BRANCH_NAME}-${env.BUILD_ID}/student1-instances.txt | grep -A 1 control | tail -n 1 | cut -d' ' -f 2 | cut -d'=' -f2 | tee control_host"
                                CONTROL_NODE_HOST = readFile('control_host').trim()
                                RUN_ALL_PLAYBOOKS = 'find . -name "*.yml" -o -name "*.yaml" | grep -v "2.0" | sort | xargs -I {} bash -c "echo {} && ANSIBLE_FORCE_COLOR=true ansible-playbook {}"'
                                sh "sshpass -p 'ansible' ssh -o StrictHostKeyChecking=no student1@${CONTROL_NODE_HOST} 'cd networking-workshop && ${RUN_ALL_PLAYBOOKS}'"
                            }
                        }
                        script {
                            stage('F5-teardown') {
                                withCredentials([string(credentialsId: 'workshops_aws_access_key', variable: 'AWS_ACCESS_KEY'),
                                                 string(credentialsId: 'workshops_aws_secret_key', variable: 'AWS_SECRET_KEY')]) {
                                    withEnv(["AWS_SECRET_KEY=${AWS_SECRET_KEY}",
                                             "AWS_ACCESS_KEY=${AWS_ACCESS_KEY}",
                                             "ANSIBLE_CONFIG=provisioner/ansible.cfg",
                                             "ANSIBLE_FORCE_COLOR=true"]) {
                                        sh """ansible-playbook provisioner/teardown_lab.yml \
                                                -e @provisioner/tests/vars.yml \
                                                -e @provisioner/tests/ci-f5.yml"""
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        cleanup {
            script {
                stage('Cleaning up') {
                    withCredentials([string(credentialsId: 'workshops_aws_access_key', variable: 'AWS_ACCESS_KEY'),
                                     string(credentialsId: 'workshops_aws_secret_key', variable: 'AWS_SECRET_KEY')]) {
                        withEnv(["AWS_SECRET_KEY=${AWS_SECRET_KEY}",
                                 "AWS_ACCESS_KEY=${AWS_ACCESS_KEY}",
                                 "ANSIBLE_CONFIG=provisioner/ansible.cfg",
                                 "ANSIBLE_FORCE_COLOR=true"]) {
                            sh "ansible-playbook provisioner/teardown_lab.yml -e @provisioner/tests/vars.yml -e @provisioner/tests/ci-rhel.yml"
                            sh "ansible-playbook provisioner/teardown_lab.yml -e @provisioner/tests/vars.yml -e @provisioner/tests/ci-networking.yml"
                            sh "ansible-playbook provisioner/teardown_lab.yml -e @provisioner/tests/vars.yml -e @provisioner/tests/ci-f5.yml"
                        }
                    }
                }
            }
        }
    }
}
