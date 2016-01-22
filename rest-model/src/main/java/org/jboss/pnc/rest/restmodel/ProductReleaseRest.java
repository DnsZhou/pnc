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
package org.jboss.pnc.rest.restmodel;

import java.util.Date;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import javax.xml.bind.annotation.XmlRootElement;

import org.jboss.pnc.model.ProductRelease;
import org.jboss.pnc.model.ProductRelease.SupportLevel;
import org.jboss.pnc.rest.validation.groups.WhenCreatingNew;
import org.jboss.pnc.rest.validation.groups.WhenUpdating;

import io.swagger.annotations.ApiModelProperty;

@XmlRootElement(name = "ProductRelease")
public class ProductReleaseRest implements GenericRestEntity<Integer> {

    @NotNull(groups = WhenUpdating.class)
    @Null(groups = WhenCreatingNew.class)
    private Integer id;

    private String version;

    private Date releaseDate;

    private String downloadUrl;

    private Integer productVersionId;

    private Integer productMilestoneId;

    @ApiModelProperty(dataType = "string")
    private SupportLevel supportLevel;

    public ProductReleaseRest() {
    }

    public ProductReleaseRest(ProductRelease productRelease) {
        this.id = productRelease.getId();
        this.version = productRelease.getVersion();
        this.releaseDate = productRelease.getReleaseDate();

        this.downloadUrl = productRelease.getDownloadUrl();
        this.productVersionId = productRelease.getProductVersion().getId();
        if (productRelease.getProductMilestone() != null) {
            this.productMilestoneId = productRelease.getProductMilestone().getId();
        }

        this.supportLevel = productRelease.getSupportLevel();
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Integer getProductVersionId() {
        return productVersionId;
    }

    public void setProductVersionId(Integer productVersionId) {
        this.productVersionId = productVersionId;
    }

    public Integer getProductMilestoneId() {
        return productMilestoneId;
    }

    public void setProductMilestoneId(Integer productMilestoneId) {
        this.productMilestoneId = productMilestoneId;
    }

    public SupportLevel getSupportLevel() {
        return supportLevel;
    }

    public void setSupportLevel(SupportLevel supportLevel) {
        this.supportLevel = supportLevel;
    }

    public ProductRelease toProductRelease(ProductRelease productRelease) {
        productRelease.setId(id);
        productRelease.setVersion(version);
        productRelease.setReleaseDate(releaseDate);
        productRelease.setDownloadUrl(downloadUrl);
        productRelease.setSupportLevel(supportLevel);

        return productRelease;
    }

}
