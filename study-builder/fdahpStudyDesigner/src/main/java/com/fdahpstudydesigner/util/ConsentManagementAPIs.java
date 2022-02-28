package com.fdahpstudydesigner.util;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.healthcare.v1.CloudHealthcare;
import com.google.api.services.healthcare.v1.CloudHealthcare.Projects.Locations.Datasets.ConsentStores.ConsentArtifacts;
import com.google.api.services.healthcare.v1.CloudHealthcareScopes;
import com.google.api.services.healthcare.v1.model.ConsentArtifact;
import com.google.api.services.healthcare.v1.model.Image;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsentManagementAPIs {
  public static final JsonFactory JSON_FACTORY = new JacksonFactory();
  public static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  private XLogger logger = XLoggerFactory.getXLogger(ConsentManagementAPIs.class.getName());

  protected static final Map<String, String> configMap = FdahpStudyDesignerUtil.getAppProperties();

  private static CloudHealthcare createClient() throws IOException {
    // Use Application Default Credentials (ADC) to authenticate the requests
    // For more information see https://cloud.google.com/docs/authentication/production
    final GoogleCredentials credential =
        GoogleCredentials.getApplicationDefault()
            .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));

    // Create a HttpRequestInitializer, which will provide a baseline configuration to all requests.
    HttpRequestInitializer requestInitializer =
        new HttpRequestInitializer() {
          @Override
          public void initialize(com.google.api.client.http.HttpRequest httpRequest)
              throws IOException {
            new HttpCredentialsAdapter(credential).initialize(httpRequest);
            httpRequest.setConnectTimeout(60000); // 1 minutes connect timeout
            httpRequest.setReadTimeout(60000); // 1 minutes read timeout
          }
        };

    // Build the client for interacting with the service.
    return new CloudHealthcare.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
        .setApplicationName("your-application-name")
        .build();
  }

  public String createConsentArtifact(
      Map<String, String> metaData, String userId, String version, String gcsUri) throws Exception {
    logger.entry("Begin createConsentArtifact()");
    try {
      CloudHealthcare client = createClient();

      String parentName =
          String.format(
              "projects/%s/locations/%s/datasets/%s/consentStores/%s",
              configMap.get("projectId"),
              configMap.get("regionId"),
              configMap.get("consentDatasetId"),
              configMap.get("consentstoreId"));

      Image image = new Image();
      image.setGcsUri(gcsUri);
      List<Image> images = new ArrayList<>(Arrays.asList(image));

      ConsentArtifact content =
          new ConsentArtifact()
              .setMetadata(metaData)
              .setUserId(userId)
              .setConsentContentVersion(version)
              .setConsentContentScreenshots(images);

      ConsentArtifacts.Create request =
          client
              .projects()
              .locations()
              .datasets()
              .consentStores()
              .consentArtifacts()
              .create(parentName, content);

      ConsentArtifact response = request.execute();
      logger.info("ConsentArtifact created: " + response.toPrettyString());
      return response.toPrettyString();
    } catch (IOException e) {
      logger.error("Consent artifact creation failed with an exception", e);
      throw new Exception(FdahpStudyDesignerConstants.FAILURE_CONSENT_STORE_MESSAGE);
    }
  }
}
