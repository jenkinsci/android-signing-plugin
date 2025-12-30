package org.jenkinsci.plugins.androidsigning;

import hudson.model.FreeStyleProject;
import hudson.model.Items;
import hudson.model.listeners.ItemListener;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@WithJenkins
class MultiEntryToSingleEntryBuilderMigrationTest {

    private JenkinsRule testJenkins;
    
    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        testJenkins = rule;
    }

    @Test
    @WithoutJenkins
    void builderIsNotMigratedIfItHasEntries() {
        InputStream oldConfigIn = getClass().getResourceAsStream("compatibility/config-2.0.8.xml");
        FreeStyleProject job = (FreeStyleProject) Items.XSTREAM.fromXML(oldConfigIn);

        assertThat(job.getBuildersList().size(), equalTo(2));

        SignApksBuilder builder = (SignApksBuilder) job.getBuildersList().get(0);

        assertThat(builder.getEntries().size(), equalTo(3));
        assertThat(builder.isMigrated(), is(false));

        builder = (SignApksBuilder) job.getBuildersList().get(1);

        assertThat(builder.getEntries().size(), equalTo(2));
        assertThat(builder.isMigrated(), is(false));

        builder.getEntries().clear();

        assertThat(builder.isMigrated(), is(true));
    }

    @Test
    @WithoutJenkins
    void builderIsMigratedWhenEntriesIsNull() {
        SignApksBuilder builder = new SignApksBuilder();

        assertThat(builder.getEntries(), nullValue());
        assertThat(builder.isMigrated(), is(true));
    }

    @Test
    void migratesOldData() throws Exception {
        MultiEntryToSingleEntryBuilderMigration migration = (MultiEntryToSingleEntryBuilderMigration) ItemListener.all().stream().filter(it -> it instanceof MultiEntryToSingleEntryBuilderMigration).findFirst().orElseThrow();
        InputStream configIn = getClass().getResourceAsStream("compatibility/config-2.0.8.xml");
        FreeStyleProject job = (FreeStyleProject) Mockito.spy(Items.XSTREAM.fromXML(configIn));
        testJenkins.jenkins.add(job, getClass().getSimpleName());
        job.onLoad(testJenkins.jenkins, getClass().getSimpleName());

        assertThat(job.getBuildersList().size(), equalTo(2));

        int oldEntryCount = job.getBuildersList().stream().mapToInt((builder) -> builder instanceof SignApksBuilder apksBuilder ? apksBuilder.getEntries().size() : 0).sum();

        migration.onLoaded();

        Mockito.verify(job).save();
        assertThat(job.getBuildersList().size(), equalTo(oldEntryCount));
    }

    @Test
    void doesNotMigrateAlreadyMigratedData() throws Exception {
        MultiEntryToSingleEntryBuilderMigration migration = (MultiEntryToSingleEntryBuilderMigration) ItemListener.all().stream().filter(it -> it instanceof MultiEntryToSingleEntryBuilderMigration).findFirst().orElseThrow();
        InputStream configIn = getClass().getResourceAsStream("compatibility/config-2.1.0.xml");
        FreeStyleProject job = (FreeStyleProject) Mockito.spy(Items.XSTREAM.fromXML(configIn));
        testJenkins.jenkins.add(job, getClass().getSimpleName());
        job.onLoad(testJenkins.jenkins, getClass().getSimpleName());

        List<Builder> loadedBuilders = new ArrayList<>(job.getBuilders());
        loadedBuilders.forEach(it ->  assertThat(((SignApksBuilder) it).isMigrated(), is(true)));

        migration.onLoaded();

        Mockito.verify(job, Mockito.never()).save();
        assertThat(job.getBuilders().size(), equalTo(loadedBuilders.size()));
        for (int i = 0; i < job.getBuilders().size(); i++) {
            assertThat(job.getBuilders().get(i), sameInstance(loadedBuilders.get(i)));
        }
    }

    @Test
    void leavesOtherBuildStepsInPlace() throws Exception {
        MultiEntryToSingleEntryBuilderMigration migration = (MultiEntryToSingleEntryBuilderMigration) ItemListener.all().stream().filter(it -> it instanceof MultiEntryToSingleEntryBuilderMigration).findFirst().orElseThrow();
        InputStream configIn = getClass().getResourceAsStream("compatibility/config-2.0.8.xml");
        FreeStyleProject job = (FreeStyleProject) Items.XSTREAM.fromXML(configIn);
        testJenkins.jenkins.add(job, getClass().getSimpleName());
        job.onLoad(testJenkins.jenkins, getClass().getSimpleName());

        assertThat(job.getBuildersList().size(), equalTo(2));

        int oldEntryCount = job.getBuildersList().stream().mapToInt((builder) -> builder instanceof SignApksBuilder apksBuilder ? apksBuilder.getEntries().size() : 0).sum();

        List<Builder> buildersMod = new ArrayList<>(job.getBuildersList());
        buildersMod.add(1, new Shell("echo \"${this.class}\""));
        job.getBuildersList().replaceBy(buildersMod);

        migration.onLoaded();

        assertThat(job.getBuildersList().size(), equalTo(oldEntryCount + 1));
        assertThat(job.getBuildersList().get(0), instanceOf(SignApksBuilder.class));
        assertThat(job.getBuildersList().get(1), instanceOf(SignApksBuilder.class));
        assertThat(job.getBuildersList().get(2), instanceOf(SignApksBuilder.class));
        assertThat(job.getBuildersList().get(3), instanceOf(Shell.class));
        assertThat(job.getBuildersList().get(4), instanceOf(SignApksBuilder.class));
        assertThat(job.getBuildersList().get(5), instanceOf(SignApksBuilder.class));
    }

}
