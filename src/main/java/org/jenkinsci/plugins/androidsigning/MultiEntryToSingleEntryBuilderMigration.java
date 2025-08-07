package org.jenkinsci.plugins.androidsigning;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.listeners.ItemListener;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;


@Extension
public class MultiEntryToSingleEntryBuilderMigration extends ItemListener {

    private static final Logger log = Logger.getLogger(MultiEntryToSingleEntryBuilderMigration.class.getName());

    @Override
    public void onLoaded() {
        Jenkins jenkins = Jenkins.get();
        List<Project> jobs = jenkins.getAllItems(Project.class);
        for (Project<?,?> job : jobs) {
            migrateBuildersOfJob(job);
        }
    }

    private void migrateBuildersOfJob(Project<?,?> job) {
        DescribableList<Builder, Descriptor<Builder>> old = job.getBuildersList();
        boolean isMigrated = old.stream().allMatch(builder -> {
            if (builder instanceof  SignApksBuilder) {
                return ((SignApksBuilder) builder).isMigrated();
            }
            return true;
        });
        if (isMigrated) {
            return;
        }
        final List<Builder> migrated = new ArrayList<>();
        for (Builder builder : old) {
            if (builder instanceof SignApksBuilder) {
                migrated.addAll(SignApksBuilder.singleEntryBuildersFromEntriesOfBuilder((SignApksBuilder) builder));
            }
            else {
                migrated.add(builder);
            }
        }
        try {
            job.getBuildersList().replaceBy(migrated);
        }
        catch (IOException e) {
            log.log(Level.WARNING, "error migrating " + SignApksBuilder.class.getSimpleName() + " steps of job " + job, e);
        }
    }

}
