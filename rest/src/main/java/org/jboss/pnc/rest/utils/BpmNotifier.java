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

package org.jboss.pnc.rest.utils; //TODO move out of utils

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jboss.logging.Logger;
import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.common.json.ConfigurationParseException;
import org.jboss.pnc.common.json.moduleconfig.BpmModuleConfig;
import org.jboss.pnc.common.json.moduleprovider.PncConfigProvider;
import org.jboss.pnc.common.util.HttpUtils;
import org.jboss.pnc.rest.restmodel.BuildResultRest;
import org.jboss.pnc.spi.BuildResult;
import org.jboss.pnc.spi.builddriver.exception.BuildDriverException;
import org.jboss.pnc.spi.executor.BuildExecutionConfiguration;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class BpmNotifier { //TODO rename: remove bpm for name

    private final Logger log = Logger.getLogger(BpmNotifier.class);
    private BpmModuleConfig bpmConfig;

    @Deprecated
    public BpmNotifier() { //CDI workaround
    }

    @Inject
    public BpmNotifier(Configuration configuration) throws ConfigurationParseException {
        bpmConfig = configuration.getModuleConfig(new PncConfigProvider<BpmModuleConfig>(BpmModuleConfig.class));
    }

    public void sendBuildExecutionCompleted(String uri, BuildResult buildResult) {
        log.debug("Preparing to send build result to BPM " + buildResult + ".");
        BuildResultRest buildResultRest = null;
        String errMessage = "";
        try {
            buildResultRest = new BuildResultRest(buildResult);
            log.debug("Sending build result to BPM " + buildResultRest.toString() + ".");
        } catch (BuildDriverException e) {
            log.error("Cannot construct rest result.", e);
            errMessage = "Cannot construct rest result: " + e.getMessage();
        }

        HttpPost request = new HttpPost(uri);

        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("event", buildResultRest != null ? buildResultRest.toString() : "{\"error\", \"" + errMessage + "\"}"));

        try {
            UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(parameters);
            request.setEntity(formEntity);
        } catch (UnsupportedEncodingException e) {
            log.error("Error occurred preparing callback request.", e);
        }

        request.addHeader("Authorization", getAuthHeader());

        //get id for logging
        String buildExecutionConfigurationId;
        if (buildResult.getBuildExecutionConfiguration().isPresent()) {
            BuildExecutionConfiguration buildExecutionConfiguration = buildResult.getBuildExecutionConfiguration().get();
            buildExecutionConfigurationId = buildExecutionConfiguration.getId() + "";
        } else {
            buildExecutionConfigurationId = "NO BuildExecutionConfiguration.";
        }

        log.info("Sending buildResult of buildExecutionConfiguration.id " + buildExecutionConfigurationId + ": " + request.getRequestLine());

        try (CloseableHttpClient httpClient = HttpUtils.getPermissiveHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                log.info(response.getStatusLine());
                try {
                    if (response.getStatusLine().getStatusCode() != 200) {
                        InputStream content = response.getEntity().getContent();
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(content, writer);
                        log.debug("Received message: " + writer.toString());
                    }
                } catch (Exception e) {
                    log.warn("Cannot write http response message to log.", e);
                }
            }
        } catch (IOException e) {
            log.error("Error occurred executing the callback.", e);
        }
    }

    private String getAuthHeader() {
        byte[] encodedBytes = Base64.encodeBase64((bpmConfig.getUsername() + ":" + bpmConfig.getPassword()).getBytes());
        return "Basic " + new String(encodedBytes);
    }

    public void simpleHttpPostCallback(String uri) {
        HttpPost request = new HttpPost(uri);
        request.addHeader("Authorization", getAuthHeader());
        log.info("Executing request " + request.getRequestLine());

        try (CloseableHttpClient httpClient = HttpUtils.getPermissiveHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                log.info(response.getStatusLine());
            }
        } catch (IOException e) {
            log.error("Error occurred executing the callback.", e);
        }
    }
}
