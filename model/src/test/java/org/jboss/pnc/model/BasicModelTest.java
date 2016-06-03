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
package org.jboss.pnc.model;

import java.io.InputStream;
import java.time.Instant;
import java.util.Date;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import javax.persistence.RollbackException;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.ext.hsqldb.HsqldbDataTypeFactory;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.internal.SessionImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BasicModelTest extends AbstractModelTest {

    /** located in src/test/resources */
    private final static String DBUNIT_DATASET_FILE = "basic-model-test-data.xml";

    /**
     * Initialize a basic data set before each test run
     */
    @Before
    public void initTestData() throws Exception {
        // Initialize data from xml dataset file
        EntityManager em = getEmFactory().createEntityManager();
        IDatabaseConnection connection = new DatabaseConnection(em.unwrap(SessionImpl.class).connection());
        connection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new HsqldbDataTypeFactory());
        FlatXmlDataSetBuilder flatXmlDataSetBuilder = new FlatXmlDataSetBuilder();
        flatXmlDataSetBuilder.setColumnSensing(true);
        InputStream dataSetStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(DBUNIT_DATASET_FILE);
        IDataSet dataSet = flatXmlDataSetBuilder.build(dataSetStream);
        DatabaseOperation.INSERT.execute(connection, dataSet);

        // Initialize sample build configurations, these cannot be done by DBUnit because of the Hibernate Envers Auditing
        BuildConfiguration buildConfig1 = BuildConfiguration.Builder.newBuilder().name("Test Build Configuration 1")
                .description("Test Build Configuration 1 Description").project(Project.Builder.newBuilder().id(1).build())
                .scmRepoURL("http://www.github.com").buildScript("mvn install")
                .buildEnvironment(BuildEnvironment.Builder.newBuilder().id(1).build()).build();

        BuildConfiguration buildConfig2 = BuildConfiguration.Builder.newBuilder().name("Test Build Configuration 2")
                .description("Test Build Configuration 2 Description").project(Project.Builder.newBuilder().id(1).build())
                .scmRepoURL("http://www.github.com").buildScript("mvn install")
                .buildEnvironment(BuildEnvironment.Builder.newBuilder().id(1).build()).build();

        em.getTransaction().begin();
        em.persist(buildConfig1);
        em.persist(buildConfig2);
        em.getTransaction().commit();
        em.close();
    }

    @After
    public void cleanup() {
        clearDatabaseTables();
    }

    @Test
    public void testDataInitializationIsWorking() {
        EntityManager em = getEmFactory().createEntityManager();
        Assert.assertEquals(2, em.createQuery("from Product").getResultList().size());
        Assert.assertEquals(2, em.createQuery("from ProductVersion").getResultList().size());
        Assert.assertEquals(2, em.createQuery("from ProductMilestone").getResultList().size());
        Assert.assertEquals(2, em.createQuery("from License").getResultList().size());
        Assert.assertEquals(2, em.createQuery("from BuildConfiguration").getResultList().size());
    }

    @Test
    public void testSimpleProductInsertAndUpdate() {

        final String NEW_PRODUCT_INSERTED_NAME = "New Product Inserted";
        final String NEW_PRODUCT_UPDATED_NAME = "New Product Updated";

        EntityManager em = getEmFactory().createEntityManager();
        em.getTransaction().begin();
        Product newProduct = Product.Builder.newBuilder().name(NEW_PRODUCT_INSERTED_NAME).description("Product")
                .abbreviation("foo").build();
        em.persist(newProduct);
        em.getTransaction().commit();
        int productId = newProduct.getId();

        em.getTransaction().begin();
        Product productLoad = em.find(Product.class, productId);
        Assert.assertEquals(NEW_PRODUCT_INSERTED_NAME, productLoad.getName());

        productLoad.setName(NEW_PRODUCT_UPDATED_NAME);
        em.persist(productLoad);
        em.getTransaction().commit();

        Product productReload = em.find(Product.class, productId);
        Assert.assertEquals(NEW_PRODUCT_UPDATED_NAME, productReload.getName());
    }

    @Test
    public void testCreateBuildRecordAndArtifacts() {
        EntityManager em = getEmFactory().createEntityManager();

        Artifact artifact1 = Artifact.Builder.newBuilder().identifier("org.jboss:artifact1").checksum("ABCD1234")
                .filename("artifact1.jar").artifactQuality(ArtifactQuality.BUILT).repoType(RepositoryType.MAVEN).build();
        Artifact artifact2 = Artifact.Builder.newBuilder().identifier("org.jboss:artifact2").checksum("BBCD1234")
                .filename("artifact2.jar").artifactQuality(ArtifactQuality.BUILT).repoType(RepositoryType.MAVEN).build();
        Artifact artifact3 = Artifact.Builder.newBuilder().identifier("org.jboss:artifact3").checksum("CBCD1234")
                .filename("artifact3.jar").artifactQuality(ArtifactQuality.IMPORTED).repoType(RepositoryType.MAVEN).build();

        BuildConfigurationAudited buildConfigAud = (BuildConfigurationAudited) em.createQuery("from BuildConfigurationAudited")
                .getResultList().get(1);
        BuildConfiguration buildConfig1 = BuildConfiguration.Builder.newBuilder().id(buildConfigAud.getIdRev().getId()).build();
        BuildRecord buildRecord1 = BuildRecord.Builder.newBuilder().id(1).buildConfigurationAudited(buildConfigAud)
                .latestBuildConfiguration(buildConfig1).buildLog("Bulid Complete").buildContentId("foo")
                .submitTime(Date.from(Instant.now())).startTime(Date.from(Instant.now())).endTime(Date.from(Instant.now()))
                .builtArtifact(artifact1).builtArtifact(artifact2).dependency(artifact3).build();

        em.getTransaction().begin();
        em.persist(artifact1);
        em.persist(artifact2);
        em.persist(artifact3);
        em.persist(buildRecord1);
        em.getTransaction().commit();
    }

    @Test
    public void testBuildRecordPreventsAddingDuplicateArtifacts() {

        EntityManager em = getEmFactory().createEntityManager();

        Artifact builtArtifact = Artifact.Builder.newBuilder().identifier("org.jboss:builtArtifact").checksum("12345678")
                .filename("buildArtifact.jar").artifactQuality(ArtifactQuality.BUILT).repoType(RepositoryType.MAVEN).build();
        Artifact importedArtifact = Artifact.Builder.newBuilder().identifier("org.jboss:importedArtifact").checksum("12345678")
                .filename("importedArtifact.jar").artifactQuality(ArtifactQuality.IMPORTED).repoType(RepositoryType.MAVEN)
                .build();

        BuildConfigurationAudited buildConfigAud = (BuildConfigurationAudited) em.createQuery("from BuildConfigurationAudited")
                .getResultList().get(1);
        BuildConfiguration buildConfig1 = BuildConfiguration.Builder.newBuilder().id(buildConfigAud.getIdRev().getId()).build();
        BuildRecord buildRecord = BuildRecord.Builder.newBuilder().id(2).buildConfigurationAudited(buildConfigAud)
                .latestBuildConfiguration(buildConfig1).buildLog("Bulid Complete").buildContentId("foo")
                .submitTime(Date.from(Instant.now())).startTime(Date.from(Instant.now())).endTime(Date.from(Instant.now()))
                //Add the built artifact and dependency artifact twice
                .builtArtifact(builtArtifact).builtArtifact(builtArtifact).dependency(importedArtifact).dependency(importedArtifact)
                .build();

        em.getTransaction().begin();
        em.persist(builtArtifact);
        em.persist(importedArtifact);
        em.persist(buildRecord);
        em.getTransaction().commit();

        em.close();
    }

    @Test
    public void testProductMilestoneAndRelease() throws Exception {

        EntityManager em = getEmFactory().createEntityManager();
        ProductMilestone productMilestone1 = em.find(ProductMilestone.class, 1);

        Artifact artifact = Artifact.Builder.newBuilder().identifier("org.test:artifact1:1.0:jar").checksum("987654321")
                .filename("artifact1.jar").artifactQuality(ArtifactQuality.IMPORTED)
                .originUrl("http://central.maven.org/maven2/test.jar").importDate(Date.from(Instant.now()))
                .repoType(RepositoryType.MAVEN).build();
        productMilestone1.addDistributedArtifact(artifact);
        ProductRelease productRelease1 = ProductRelease.Builder.newBuilder().version("1.0.0.Beta1")
                .productMilestone(productMilestone1).build();

        productRelease1.setProductMilestone(productMilestone1);

        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();
            em.persist(artifact);
            em.persist(productMilestone1);
            em.persist(productRelease1);
            tx.commit();

            ProductRelease release = em.find(ProductRelease.class, productRelease1.getId());
            Assert.assertEquals(1, release.getProductMilestone().getDistributedArtifacts().size());
        } catch (RuntimeException e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Test
    public void testBuildConfigurationAudit() throws Exception {

        BuildConfiguration buildConfiguration1 = BuildConfiguration.Builder.newBuilder()
                .name("Build Configuration 1")
                .description("Build Configuration 1 Description")
                .project(Project.Builder.newBuilder().id(1).build())
                .scmRepoURL("http://www.github.com")
                .buildScript("mvn install")
                .buildEnvironment(BuildEnvironment.Builder.newBuilder().id(1).build())
                .build();

        buildConfiguration1.setProject(Project.Builder.newBuilder().id(1).build());
        buildConfiguration1.setBuildEnvironment(BuildEnvironment.Builder.newBuilder().id(1).build());

        EntityManager em = getEmFactory().createEntityManager();
        EntityTransaction tx1 = em.getTransaction();
        EntityTransaction tx2 = em.getTransaction();

        try {
            tx1.begin();
            em.persist(buildConfiguration1);
            tx1.commit();

            tx2.begin();
            buildConfiguration1 = em.find(BuildConfiguration.class, buildConfiguration1.getId());
            buildConfiguration1.setDescription("Updated build config description");
            em.merge(buildConfiguration1);
            tx2.commit();

            Query rowCountQuery = em
                    .createQuery("select count(*) from BuildConfigurationAudited bca where id=" + buildConfiguration1.getId());
            Long count = (Long) rowCountQuery.getSingleResult();
            // Should have 2 audit records, 1 for insert, and 1 for update
            Assert.assertEquals(2, count.longValue());

        } catch (RuntimeException e) {
            if (tx1 != null && tx1.isActive()) {
                tx1.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Test(expected = RollbackException.class)
    public void testProjectInsertConstraintFailure() throws Exception {

        EntityManager em = getEmFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        Project project1 = Project.Builder.newBuilder().name("Project 1").description("Project 1 Description")
                .license(License.Builder.newBuilder().id(100).build()).build();

        try {
            tx.begin();
            // Expect this to fail because of missing license foreign key
            em.persist(project1);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

}
