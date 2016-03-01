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
package org.jboss.pnc.mavenrepositorymanager;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.commonjava.indy.client.core.Indy;
import org.commonjava.indy.client.core.util.UrlUtils;
import org.commonjava.indy.model.core.StoreType;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleArtifactRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.jboss.pnc.mavenrepositorymanager.fixture.TestBuildExecution;
import org.jboss.pnc.model.Artifact;
import org.jboss.pnc.model.BuiltArtifact;
import org.jboss.pnc.spi.repositorymanager.BuildExecution;
import org.jboss.pnc.spi.repositorymanager.RepositoryManagerResult;
import org.jboss.pnc.spi.repositorymanager.model.RepositorySession;
import org.jboss.pnc.test.category.ContainerTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

@Category(ContainerTest.class)
public class UploadTwoThenVerifyExtractedArtifactsContainThemTest 
    extends AbstractRepositoryManagerDriverTest
{

    @Test
    public void extractBuildArtifacts_ContainsTwoUploads() throws Exception {
        // create a dummy non-chained build execution and repo session based on it
        BuildExecution execution = new TestBuildExecution();
        RepositorySession rc = driver.createBuildRepository(execution);

        assertThat(rc, notNullValue());

        String baseUrl = rc.getConnectionInfo().getDeployUrl();
        String pomPath = "org/commonjava/indy/indy-core/0.17.0/indy-core-0.17.0.pom";
        String jarPath = "org/commonjava/indy/indy-core/0.17.0/indy-core-0.17.0.jar";

        CloseableHttpClient client = HttpClientBuilder.create().build();

        // upload a couple files related to a single GAV using the repo session deployment url
        // this simulates a build deploying one jar and its associated POM
        for (String path : new String[] { pomPath, jarPath }) {
            final String url = UrlUtils.buildUrl(baseUrl, path);

            HttpPut put = new HttpPut(url);
            put.setEntity(new StringEntity("This is a test"));

            boolean uploaded = client.execute(put, new ResponseHandler<Boolean>() {
                @Override
                public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    try {
                        return response.getStatusLine().getStatusCode() == 201;
                    } finally {
                        if (response instanceof CloseableHttpResponse) {
                            IOUtils.closeQuietly((CloseableHttpResponse) response);
                        }
                    }
                }
            });

            assertThat("Failed to upload: " + url, uploaded, equalTo(true));
        }

        // extract the "built" artifacts we uploaded above.
        RepositoryManagerResult repositoryManagerResult = rc.extractBuildArtifacts();

        // check that both files are present in extracted result
        List<BuiltArtifact> artifacts = repositoryManagerResult.getBuiltArtifacts();
        System.out.println(artifacts);

        assertThat(artifacts, notNullValue());
        assertThat(artifacts.size(), equalTo(2));

        ProjectVersionRef pvr = new SimpleProjectVersionRef("org.commonjava.indy", "indy-core", "0.17.0");
        Set<String> refs = new HashSet<>();
        refs.add(new SimpleArtifactRef(pvr, "pom", null).toString());
        refs.add(new SimpleArtifactRef(pvr, "jar", null).toString());

        // check that the artifact getIdentifier() stores GAVT[C] information in the standard Maven rendering
        for (Artifact artifact : artifacts) {
            assertThat(artifact + " is not in the expected list of built artifacts: " + refs,
                    refs.contains(artifact.getIdentifier()),
                    equalTo(true));
        }

        Indy indy = driver.getIndy();

        // check that we can download the two files from the build repository
        for (String path : new String[] { pomPath, jarPath }) {
            final String url = indy.content().contentUrl(StoreType.hosted, rc.getBuildRepositoryId(), path);
            boolean downloaded = client.execute(new HttpGet(url), new ResponseHandler<Boolean>() {
                @Override
                public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    try {
                        return response.getStatusLine().getStatusCode() == 200;
                    } finally {
                        if (response instanceof CloseableHttpResponse) {
                            IOUtils.closeQuietly((CloseableHttpResponse) response);
                        }
                    }
                }
            });

            assertThat("Failed to download: " + url, downloaded, equalTo(true));
        }

        client.close();

    }

}
