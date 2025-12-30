package org.jenkinsci.plugins.androidsigning;

import hudson.model.FreeStyleBuild;
import hudson.model.Run;


record BuildArtifact(FreeStyleBuild build, Run.Artifact artifact) {
}
