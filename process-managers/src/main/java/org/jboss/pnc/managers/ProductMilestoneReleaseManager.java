/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.managers;

import org.jboss.pnc.bpm.BpmEventType;
import org.jboss.pnc.bpm.BpmManager;
import org.jboss.pnc.bpm.task.MilestoneReleaseTask;
import org.jboss.pnc.model.Artifact;
import org.jboss.pnc.model.BuildConfigurationAudited;
import org.jboss.pnc.model.BuildRecord;
import org.jboss.pnc.model.MilestoneReleaseStatus;
import org.jboss.pnc.model.ProductMilestone;
import org.jboss.pnc.model.ProductMilestoneRelease;
import org.jboss.pnc.rest.restmodel.bpm.BpmNotificationRest;
import org.jboss.pnc.rest.restmodel.bpm.BpmStringMapNotificationRest;
import org.jboss.pnc.rest.restmodel.causeway.ArtifactImportError;
import org.jboss.pnc.rest.restmodel.causeway.BuildImportResultRest;
import org.jboss.pnc.rest.restmodel.causeway.BuildImportStatus;
import org.jboss.pnc.rest.restmodel.causeway.MilestoneReleaseResultRest;
import org.jboss.pnc.spi.datastore.repositories.ArtifactRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildRecordRepository;
import org.jboss.pnc.spi.datastore.repositories.ProductMilestoneReleaseRepository;
import org.jboss.pnc.spi.datastore.repositories.ProductMilestoneRepository;
import org.jboss.pnc.spi.exception.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Author: Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * Date: 8/31/16
 * Time: 8:42 AM
 */
@Stateless
public class ProductMilestoneReleaseManager {

    private static final Logger log = LoggerFactory.getLogger(ProductMilestoneReleaseManager.class);

    private BpmManager bpmManager;

    private ArtifactRepository artifactRepository;
    private BuildRecordRepository buildRecordRepository;
    private ProductMilestoneReleaseRepository releaseRepository;
    private ProductMilestoneRepository milestoneRepository;

    @Deprecated // for ejb
    public ProductMilestoneReleaseManager() {
    }

    @Inject
    public ProductMilestoneReleaseManager(
            ProductMilestoneReleaseRepository releaseRepository,
            BpmManager bpmManager,
            ArtifactRepository artifactRepository,
            BuildRecordRepository buildRecordRepository,
            ProductMilestoneRepository milestoneRepository) {
        this.releaseRepository = releaseRepository;
        this.bpmManager = bpmManager;
        this.artifactRepository = artifactRepository;
        this.buildRecordRepository = buildRecordRepository;
        this.milestoneRepository = milestoneRepository;
    }

    /**
     * Starts milestone release process
     * @param milestone product milestone to start the release for
     */
    public void startRelease(ProductMilestone milestone) {
        ProductMilestoneRelease release = triggerRelease(milestone);
        releaseRepository.save(release);
    }

    public boolean noReleaseInProgress(ProductMilestone milestone) {
        ProductMilestoneRelease latestRelease = releaseRepository.findLatestByMilestone(milestone);

        return latestRelease == null || latestRelease.getStatus() != MilestoneReleaseStatus.IN_PROGRESS;
    }

    private <T extends BpmNotificationRest> ProductMilestoneRelease triggerRelease(ProductMilestone milestone) {
        ProductMilestoneRelease release = new ProductMilestoneRelease();
        release.setStartingDate(new Date());
        release.setMilestone(milestone);
        try {
            MilestoneReleaseTask releaseTask = new MilestoneReleaseTask(milestone);
            releaseTask.addListener(BpmEventType.BREW_IMPORT_SUCCESS, this::onSuccessfulPush);
            releaseTask.<BpmStringMapNotificationRest>addListener(BpmEventType.BREW_IMPORT_ERROR, r -> onFailedPush(milestone.getId(), r));
            bpmManager.startTask(releaseTask);
            release.setLog("Brew push task started\n");

            return release;
        } catch (CoreException e) {
            log.error("Error trying to start brew push task for milestone: {}", milestone.getId(), e);
            release.setLog("Brew push BPM task creation failed. Check log for more details " + e.getMessage() + "\n");
            release.setStatus(MilestoneReleaseStatus.SYSTEM_ERROR);
            release.setEndDate(new Date());
            return release;
        }
    }

    private void onSuccessfulPush(MilestoneReleaseResultRest result) {
        int milestoneId = result.getMilestoneId();
        ProductMilestone milestone = milestoneRepository.queryById(milestoneId);
        String message = describeCompletedPush(result);
        updateRelease(milestone, message, result.getReleaseStatus().getMilestoneReleaseStatus());
    }

    private void updateRelease(ProductMilestone milestone, String message, MilestoneReleaseStatus status) {
        ProductMilestoneRelease release = releaseRepository.findLatestByMilestone(milestone);
        release.setStatus(status);
        if (status != MilestoneReleaseStatus.IN_PROGRESS) {
            release.setEndDate(new Date());
        }
        release.setLog(release.getLog() + message);
    }

    private void onFailedPush(Integer milestoneId, BpmStringMapNotificationRest result) {
        ProductMilestone milestone = milestoneRepository.queryById(milestoneId);
        updateRelease(milestone, "BREW IMPORT FAILED\nResult: " + result, MilestoneReleaseStatus.SYSTEM_ERROR);
    }

    private String describeCompletedPush(MilestoneReleaseResultRest result) {
        boolean success = result.isSuccessful();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Brew push ").append(success ? "SUCCEEDED" : "FAILED").append("\n");
        stringBuilder.append("Import details:\n");

        for (BuildImportResultRest buildImport : result.getBuilds()) {
            describeBuildImport(stringBuilder, buildImport);
        }

        return stringBuilder.toString();
    }

    private void describeBuildImport(StringBuilder stringBuilder, BuildImportResultRest buildImport) {
        Integer buildRecordId = buildImport.getBuildRecordId();
        BuildRecord record = orNull(buildRecordId, buildRecordRepository::queryById);
        BuildConfigurationAudited buildConfiguration = orNull(record, BuildRecord::getBuildConfigurationAudited);
        stringBuilder.append("\n-------------------------------------------------------------------------\n");
        String buildMessage =
                String.format("%s [buildRecordId: %d, built from %s rev %s] import %s. Brew build id: %d, Brew build url: %s\n",
                        orNull(buildConfiguration, BuildConfigurationAudited::getName),
                        orNull(record, BuildRecord::getId),
                        orNull(record, BuildRecord::getScmRepoURL),
                        orNull(record, BuildRecord::getScmRevision),
                        buildImport.getStatus(),
                        buildImport.getBrewBuildId(),
                        buildImport.getBrewBuildUrl());
        stringBuilder.append(buildMessage);
        if (buildImport.getStatus() != BuildImportStatus.SUCCESSFUL) {
            stringBuilder.append("Error message: ").append(buildImport.getErrorMessage());
            List<ArtifactImportError> errors = buildImport.getErrors();
            if (errors != null && !errors.isEmpty()) {
                errors.forEach(e -> describeArtifactImportError(stringBuilder, e));
            }
        }
        stringBuilder.append("\n");
    }

    private void describeArtifactImportError(StringBuilder stringBuilder, ArtifactImportError e) {
        Integer artifactId = e.getArtifactId();
        Artifact artifact = artifactRepository.queryById(artifactId);

        stringBuilder.append(
                String.format("Failed to import %s [artifactId:%d]. Error message: %s\n",
                        orNull(artifact, Artifact::getIdentifier),
                        artifactId,
                        e.getErrorMessage())
        );
    }

    private static <T, R> R orNull(T value, Function<T, R> f) {
        return value == null ? null : f.apply(value);
    }
}
