Jenkins Android Signing Plugin
============

## Summary and Purpose

The Android Signing plugin provides a simple build step for 
[signing Android APK](https://developer.android.com/studio/publish/app-signing.html#signing-manually)
build artifacts.  The advantage of this plugin is that you can use Jenkins to
centrally manage, protect, and provide all of your Android release signing
certificates without the need to distribute private keys and passwords to
every developer.  This is especially useful in multi-node/cloud environments
so you do not need to copy the signing keystore to every Jenkins node.
Furthermore, using this plugin externalizes your keystore and private key 
passwords from your build script, and keeps them encrypted rather than storing
them in a plain-text properties file.  This plugin also does not use a shell 
command to perform the signing, eliminating the potential that private key 
passwords appear on a command-line invocation.

## Background

This version is a fork from Big Nerd Ranch's now deprecated
[original repository](https://github.com/bignerdranch/jenkins-android-signing)
which is the basis of a nice blog post,
[Continuous Delivery for Android](https://www.bignerdranch.com/blog/continuous-delivery-for-android/).
Thanks to Big Nerd Ranch for the original work.

This plugin depends on the
[Jenkins Credentials Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)
for retrieving private key credentials for signing APKs.  Thanks to
[CloudBees](https://www.cloudbees.com/) and
[Stephen Connolly](https://github.com/stephenc) for the Credentials Plugin.

This plugin also depends on Android's  [`apksig`](https://android.googlesource.com/platform/tools/apksig/)
library to sign APKs programmatically. `apksig` backs the [`apksigner`](https://developer.android.com/studio/command-line/apksigner.html)
utility in the Android SDK Developer Tools package.  Using `apksig` ensures the signed APKs
this plugin produces comply with the newer
[APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html) and [APK Signature Scheme v3](https://source.android.com/security/apksigning/v3).
Thanks to Google/Android for making that library available as a
[Maven dependency](https://bintray.com/android/android-tools/com.android.tools.build.apksig).

## Building

Run `mvn package` to build a deployable HPI bundle for Jenkins.

## Installation

First, make sure your Jenkins instance has the Credentials Plugin (linked above).
Check the [POM](pom.xml) for version requirements.  Copy the `target/android-signing.hpi`
plugin bundle to `$JENKINS_HOME/plugins/` directory, and restart Jenkins.

As of this writing, this plugin is not yet hosted in the Jenkins Update Centre, so you
cannot install using the Jenkins UI.

## Usage

Before adding a _Sign Android APKs_ build step to a job, you must configure a certificate
credential using the Credentials Plugin's UI.  As of this writing, this plugin
requires a password-protected PKCS12 keystore containing a private key entry
protected by the same password.  Because of how the Credentials Plugin loads
key stores, you must protect your key store with a non-empty password.  You
were doing that anyway, right?

This plugin requires access to the Android SDK's 
[`zipalign`](https://developer.android.com/studio/command-line/zipalign.html)
command.  This implies that whatever Jenkins node is performing your build has 
access to an installed Android SDK, which is likely the case if you built your 
APK in a Jenkins job as well.

Once the prerequisites are setup, you can now add the _Sign Android APKs_ build step to
a job.  The configuration UI is fairly straight forward.  Select the certificate
credential you created previously, supply the alias of the private key/certificate
chain (optional if you have only one key entry), and finally supply the name or 
[Ant-style glob](https://ant.apache.org/manual/dirtasks.html)
pattern specifying the APK files relative to the job workspace you want to sign.
You can specify multiple glob patterns separated by commas if you wish.  For most
projects `**/*-unsigned.apk` should suffice.

![Sign Android APKs form](android-signing.png)

You can tell a _Sign Android APKs_ build step the location of `zipalign`
in the following ways, in order of precedence:
1. _Zipalign Path_ form input (expands environment variable references, e.g., `${CUSTOM_ANDROID_ZIPALIGN}`)
1. _ANDROID_HOME Override_ form input (expands environment variable references, e.g., `${CUSTOM_ANDROID_HOME}`)
1. `ANDROID_ZIPALIGN`[build variable](http://javadoc.jenkins-ci.org/hudson/model/AbstractBuild.html#getBuildVariables--)
1. `ANDROID_ZIPALIGN` [environment variable](http://javadoc.jenkins-ci.org/hudson/model/Run.html#getEnvironment-hudson.model.TaskListener-)
1. `ANDROID_HOME` build variable
1. `ANDROID_HOME` environment variable
1. `PATH` environment variable
    1. A directory in `PATH` containing a file called `zipalign`
    1. A directory in `PATH` that appears to be an Android SDK home, i.e., contains the `android` or `sdkmanager` utilities
    
To access the first two override form parameters above, click the _Advanced_ button on the _Sign Android APKs_
build step form group.
    
![Sign Android APKs form](android-signing-advanced.png)

Environment variables can come from various plugins, such as 
[Environment Injetor](https://wiki.jenkins-ci.org/display/JENKINS/EnvInject+Plugin) Plugin or
[Custom Tools](https://plugins.jenkins.io/custom-tools-plugin) Plugin.  The plugin searches
an Android SDK home directory by finding the latest version under the `build-tools` directory 
installed in your SDK, and attempting to use the `zipalign` file that should be there, such
as `${ANDROID_HOME}/build-tools/25.0.2/zipalign`.  Hence. be sure your Android SDK has the 
_Build Tools_ package installed.  I recommend setting up the SDK using the Custom Tools Plugin.
To cover the Windows case, the plugin will search for `zipalign.exe` as well.

Note that this plugin assumes your Android build has produced an unsigned, 
unaligned APK.  If you are using the Gradle Android plugin to build your APK, 
that means a previous Jenkins build step probably invoked the `assembleRelease` 
task on your build script and there were no [`signingConfig`](https://developer.android.com/studio/publish/app-signing.html#gradle-sign)
blocks that applied to your APK.  In that case Gradle will have produced the 
necessary unsigned, unaligned APK, ready for the Android Signing Plugin to sign.  

### Output Signed APKs

As of version 2.2.0, there are two choices for the location where a _Sign Android APKs_ build
step will write signed APKs.  You can change this behavior by clicking the _Advanced_
button in the _Sign Android APKs_ step form group of a Freestyle job, and checking the desired
radio button in the _Signed APK Destination_ group.  
* _Output to unsigned APK sibling_ - The new and default choice writes the signed APK to 
the same directory where the input unsigned APK resides, the same as the standard Android 
Gradle build would do.  This option is useful when you want to use your Gradle build script
to do something like publish the signed APK in a Gradle build step after your _Sign Android APKs_ 
build step runs.  The standard Gradle Android plugin build should produce an unsigned APK 
named with the `-unsigned.apk` suffix.  In that case, the _Android Signing Plugin_ plugin 
will simply remove the `-unsigned` component to create the signed APK file name.  Otherwise, 
the plugin will insert `-signed` before `.apk` in the unsigned APK name.  For example, 
`myApp-release-unsigned.apk` becomes `myApp-release.apk`, whereas `myApp-forElmo.apk` 
becomes `myApp-forElmo-signed.apk`.
* _Output to separate directory_ - The original behavior writes signed APKs to a
directory named like `SignApksBuilder-out/my-app-unsigned.apk/my-app-signed.apk`,
where `my-app-unsigned.apk` is a directory named after the unsigned input APK.
This is to avoid multiple signing steps in a single job overwriting each other's 
output APKs, and multiple APKs matched within a signing step colliding.  It's 
clearly not fool-proof, however, so be mindful if you are signing multple APKs
in a single job and/or signing step.

Regardless of the output option you choose, if you use the plugin's 
_Archive Signed APKs_ and/or _Archive Unsigned APKs_ option, the plugin 
archives the artifacts under the `SignApksBuilder-out/<KEY_STORE_ID>/<KEY_ALIAS>/my-app-unsigned.apk/`
directory in the build's archive.

### Pipeline

Here is an example of signing APKs from a [Pipeline](https://jenkins.io/doc/book/pipeline/) script:
```
node {
    // ... steps to build unsigned APK ...
    signAndroidApks (
        keyStoreId: "myApp.signerKeyStore",
        keyAlias: "myTeam",
        apksToSign: "**/*-unsigned.apk"
        // uncomment the following line to output the signed APK to a separate directory as described above
        // signedApkMapping: [ $class: UnsignedApkBuilderDirMapping ]
        // uncomment the following line to output the signed APK as a sibling of the unsigned APK, as described above, or just omit signedApkMapping
        // you can override these within the script if necessary
        // androidHome: env.ANDROID_HOME
        // zipalignPath: env.ANDROID_ZIPALIGN
    )
}
```
Like the Free Style Job build step described above, the Pipeline step will attempt
to use `ANDROID_ZIPALIGN` and `ANDROID_HOME`, in that priority order, from the
Jenkins environment variables.  Note the wrapping 
[`node`](https://jenkins.io/doc/pipeline/steps/workflow-durable-task-step/#node-allocate-node)
context; this plugin assumes the Pipeline step will have a workspace available.

### Job DSL

This plugin offers a [Job DSL](https://github.com/jenkinsci/job-dsl-plugin/wiki) extension.
You can include a _Sign Android APKs_ build step in the `steps` context of a Job DSL script:
```
freeStyleJob('myApp.seed') {
    scm {
        git 'https://github.com/mygithub/myApp.git', 'master', {
            extensions {
                relativeTragetDirectory 'myApp'
            }
        }
    }
    steps {
        gradle {
            rootBuildScriptDir 'myApp'
            useWrapper true
            tasks 'clean assembleRelease'
        }
        signAndroidApks '**/myApp-unsigned.apk', {
            keyStoreId 'myApp.keyStore'
            keyAlias 'myAppKey'
            // uncomment the following line to output the signed APK to a separate directory as described above
            // signedApkMapping unsignedApkNameDir()
            // uncomment the following line to output the signed APK as a sibling of the unsigned APK, as described above, or just omit signedApkMapping
            // signedApkMapping unsignedApkSibling()
            archiveSignedApks true
            archiveUnsignedApks true
            androidHome '/opt/android-sdk'
        }
    }
}
```
The availble options are analogous to those in the build step configuration web UI.

## Support

Please submit all issues to [Jenkins Jira](https://issues.jenkins-ci.org/issues/?jql=project%3DJENKINS%20AND%20component%3Dandroid-signing-plugin).
Do not use GitHub issues.

## Release Notes

See the [change log](CHANGELOG.md).

## License and Copyright

See the included LICENSE and NOTICE text files for original Work and Derivative
Work copyright and license information.
