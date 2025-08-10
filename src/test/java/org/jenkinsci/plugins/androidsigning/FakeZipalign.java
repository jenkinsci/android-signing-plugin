package org.jenkinsci.plugins.androidsigning;

import org.jvnet.hudson.test.FakeLauncher;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;


class FakeZipalign implements FakeLauncher {

    Launcher.ProcStarter lastProc;

    @Override
    public Proc onLaunch(Launcher.ProcStarter p) throws IOException {
        if (!p.cmds().get(0).contains("zipalign")) {
            if (p.cmds().get(0).contains("find") ||
                p.cmds().get(0).contains("test") ||
                p.cmds().get(0).contains("cmd.exe")) {
                return null;
            }
            return new FinishedProc(0);
        }
        lastProc = p;
        PrintStream logger = new PrintStream(p.stdout());
        List<String> cmd = p.cmds();
        String inPath = cmd.get(cmd.size() - 2);
        String outPath = cmd.get(cmd.size() - 1);
        FilePath workspace = p.pwd();
        FilePath in = workspace.child(inPath);
        FilePath out = workspace.child(outPath);
        try {
            out.getParent().mkdirs();
            if (!out.getParent().isDirectory()) {
                throw new IOException("destination directory does not exist: " + out.getParent());
            }
            logger.printf("FakeZipalign copy %s to %s in pwd %s%n", in.getRemote(), out.getRemote(), workspace);
            System.setProperty("sun.io.serialization.extendedDebugInfo", "true");
            in.act(new CopyFileCallable(out.getRemote()));
            // TODO: this was resulting in incomplete copies and failing tests, for some reason
            // sometimes the output file would not have been completely written and reading the
            // aligned apk was failing
            // in.copyTo(out);
            logger.printf("FakeZipalign copy complete%n");
            if (!out.exists()) {
                throw new IOException("FakeZipalign copy output does not exist: " + out.getRemote());
            }
            long outSize = out.length(), inSize = in.length();
            if (outSize != inSize) {
                throw new IOException("FakeZipalign copy output size " + outSize + " is different from input size " + inSize);
            }
            return new FinishedProc(0);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
