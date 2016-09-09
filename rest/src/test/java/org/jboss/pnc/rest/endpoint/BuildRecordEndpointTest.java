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
package org.jboss.pnc.rest.endpoint;

import org.jboss.pnc.executor.DefaultBuildExecutionConfiguration;
import org.jboss.pnc.executor.DefaultBuildExecutionSession;
import org.jboss.pnc.model.BuildRecord;
import org.jboss.pnc.model.SystemImageType;
import org.jboss.pnc.model.User;
import org.jboss.pnc.rest.provider.BuildRecordProvider;
import org.jboss.pnc.rest.utils.EndpointAuthenticationProvider;
import org.jboss.pnc.spi.datastore.Datastore;
import org.jboss.pnc.spi.datastore.repositories.BuildRecordRepository;
import org.jboss.pnc.spi.executor.BuildExecutionConfiguration;
import org.jboss.pnc.spi.executor.BuildExecutionSession;
import org.jboss.pnc.spi.executor.BuildExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.common.util.RandomUtils.randInt;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 
 * @author Jakub Bartecek <jbartece@redhat.com>
 *
 */
public class BuildRecordEndpointTest {

    private static final int CURRENT_USER = randInt(1000, 100000);

    @Mock
    private BuildExecutor buildExecutor;
    @Mock
    private BuildRecordRepository buildRecordRepository;
    @Mock
    private Datastore datastore;
    @Mock
    private EndpointAuthenticationProvider authProvider;
    @InjectMocks
    private BuildRecordProvider buildRecordProvider = new BuildRecordProvider();
    private BuildRecordEndpoint endpoint;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        endpoint = new BuildRecordEndpoint(buildRecordProvider, null, authProvider);

        User user = mock(User.class);
        when(user.getId()).thenReturn(CURRENT_USER);
        when(authProvider.getCurrentUser(any())).thenReturn(user);
    }

    @Test
    public void shouldGetLogsNoContent() {
        // given
        int logId = 1;
        String logContent = "";

        // when
        endpointReturnsLog(logId, logContent);

        // then
        assertThat(endpoint.getLogs(logId).getStatus()).isEqualTo(204);
    }

    @Test
    public void shouldGetLogsWithContent() {
        // given
        int logId = 1;
        String logContent = "LOG CONTENT";
        
        // when
        endpointReturnsLog(logId, logContent);

        // then
        assertThat(endpoint.getLogs(logId).getStatus()).isEqualTo(200);
    }

    private void endpointReturnsLog(int logId, String logContent) {
        configureBuildExecutorMock(5684);
        BuildRecord buildRecord = mock(BuildRecord.class);

        when(buildRecord.getBuildLog()).thenReturn(logContent);
        when(buildRecordRepository.findByIdFetchAllProperties(logId)).thenReturn(buildRecord);
    }

    private void configureBuildExecutorMock(int buildExecutionTaskId) {

        BuildExecutionConfiguration buildExecutionConfiguration = new DefaultBuildExecutionConfiguration(
                buildExecutionTaskId,
                "build-content-id",
                1,
                "",
                "build-1",
                "",
                "",
                "",
                "",
                SystemImageType.DOCKER_IMAGE,
                false);

        BuildExecutionSession buildExecutionSession = new DefaultBuildExecutionSession(buildExecutionConfiguration, null);
        when(buildExecutor.getRunningExecution(buildExecutionTaskId)).thenReturn(buildExecutionSession);
    }

}
