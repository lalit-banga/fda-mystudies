/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.config;

import com.google.cloud.healthcare.fdamystudies.dao.CloudFirestoreResponsesDaoImpl;
import com.google.cloud.healthcare.fdamystudies.dao.FileResponsesDaoImpl;
import com.google.cloud.healthcare.fdamystudies.dao.ResponsesDao;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class AppConfig extends CommonModuleConfiguration {

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @ConditionalOnProperty(name = "response.storage.type", havingValue = "firestore")
  @Bean
  @Primary
  public ResponsesDao cloudFireStoreResponse() {
    return new CloudFirestoreResponsesDaoImpl();
  }

  @ConditionalOnProperty(name = "response.storage.type", havingValue = "filestore")
  @Bean
  @Primary
  public ResponsesDao fileResponse() {
    return new FileResponsesDaoImpl();
  }

  @Bean
  public Storage storageService() {
    return StorageOptions.getDefaultInstance().getService();
  }
}
