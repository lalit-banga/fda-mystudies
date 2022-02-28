/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class ApplicationPropertyConfiguration {

  @Value("${bucketName}")
  private String bucketName;

  @Value("${projectId}")
  private String projectId;

  @Value("${regionId}")
  private String regionId;

  @Value("${consentDatasetId}")
  private String consentDatasetId;

  @Value("${consentstoreId}")
  private String consentstoreId;

  @Value("${enableConsentManagementAPI}")
  private String enableConsentManagementAPI;
}
