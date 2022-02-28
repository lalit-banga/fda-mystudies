package com.google.cloud.healthcare.fdamystudies.mapper;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.healthcare.v1.CloudHealthcare;
import com.google.api.services.healthcare.v1.CloudHealthcare.Projects.Locations.Datasets.ConsentStores.ConsentArtifacts;
import com.google.api.services.healthcare.v1.CloudHealthcare.Projects.Locations.Datasets.ConsentStores.Consents;
import com.google.api.services.healthcare.v1.CloudHealthcareScopes;
import com.google.api.services.healthcare.v1.model.Consent;
import com.google.api.services.healthcare.v1.model.ConsentArtifact;
import com.google.api.services.healthcare.v1.model.Image;
import com.google.api.services.healthcare.v1.model.ListConsentArtifactsResponse;
import com.google.api.services.healthcare.v1.model.ListConsentRevisionsResponse;
import com.google.api.services.healthcare.v1.model.ListConsentsResponse;
import com.google.api.services.healthcare.v1.model.RevokeConsentRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.healthcare.fdamystudies.common.ErrorCode;
import com.google.cloud.healthcare.fdamystudies.exceptions.ErrorCodeException;
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

  private static CloudHealthcare createClient() throws IOException {
    // Use Application Default Credentials (ADC) to authenticate the requests
    // For more information see https://cloud.google.com/docs/authentication/production
    GoogleCredentials credential =
        GoogleCredentials.getApplicationDefault()
            .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));

    // Create a HttpRequestInitializer, which will provide a baseline configuration to all requests.
    HttpRequestInitializer requestInitializer =
        request -> {
          new HttpCredentialsAdapter(credential).initialize(request);
          request.setConnectTimeout(60000);
          request.setReadTimeout(60000);
        };

    // Build the client for interacting with the service.
    return new CloudHealthcare.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
        .setApplicationName("your-application-name")
        .build();
  }

  public String createConsentArtifact(
      Map<String, String> metaData,
      String userId,
      String version,
      String gcsUri,
      String parentName) {
    logger.entry("Begin createConsentArtifact()");
    try {
      CloudHealthcare client = createClient();

      Image image = new Image();
      image.setGcsUri(gcsUri);
      List<Image> images = new ArrayList<>(Arrays.asList(image));

      ConsentArtifact consentArtifact =
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
              .create(parentName, consentArtifact);

      ConsentArtifact response = request.execute();
      logger.info("ConsentArtifact created: " + response.toPrettyString());
      return response.getName();
    } catch (IOException e) {
      logger.error("Consent artifact creation failed with an exception", e);
      throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
    }
  }

  public List<ConsentArtifact> getListOfConsentArtifact(String filter, String parentName) {
    logger.entry("Begin getListOfConsentArtifact()");
    try {
      CloudHealthcare client = createClient();

      ConsentArtifacts.List request =
          client
              .projects()
              .locations()
              .datasets()
              .consentStores()
              .consentArtifacts()
              .list(parentName)
              .setFilter(filter);

      ListConsentArtifactsResponse store = request.execute();
      logger.info("ListOfConsentArtifact retrieved: \n" + store.toPrettyString());
      return store.getConsentArtifacts();
    } catch (IOException e) {
      logger.error("Fetching of Consent artifact list failed with an exception", e);
      throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
    }
  }

  public ConsentArtifact getConsentArtifact(String parentName) {
    logger.entry("Begin getConsentArtifact()");
    try {
      CloudHealthcare client = createClient();

      ConsentArtifacts.Get request =
          client
              .projects()
              .locations()
              .datasets()
              .consentStores()
              .consentArtifacts()
              .get(parentName);

      ConsentArtifact store = request.execute();
      logger.info("getConsentArtifact retrieved: \n" + store.toPrettyString());
      return store;
    } catch (IOException e) {
      logger.error("Fetching of Consent artifact failed with an exception", e);
      throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
    }
  }

  public String createConsents(
      Map<String, String> metaData, String userId, String parentName, String consentArtifact) {
    logger.entry("Begin createConsents()");
    try {
      CloudHealthcare client = createClient();

      Consent content =
          new Consent().setMetadata(metaData).setUserId(userId).setConsentArtifact(consentArtifact);

      Consents.Create request =
          client
              .projects()
              .locations()
              .datasets()
              .consentStores()
              .consents()
              .create(parentName, content);

      Consent response = request.execute();
      logger.info("Consent created: " + response.toPrettyString());
      return response.toPrettyString();
    } catch (IOException e) {
      logger.error("Consent creation failed with an exception", e);
      throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
    }
  }

  public List<Consent> getListOfConsents(String filter, String parentName) {
    logger.entry("Begin getListOfConsents()");
    try {
      CloudHealthcare client = createClient();

      Consents.List request =
          client
              .projects()
              .locations()
              .datasets()
              .consentStores()
              .consents()
              .list(parentName)
              .setFilter(filter);

      ListConsentsResponse store = request.execute();
      logger.info("ListOfConsent retrieved: \n" + store.toPrettyString());
      return store.getConsents();
    } catch (IOException e) {
      logger.error("Fetching of Consent list failed with an exception", e);
      throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
    }
  }

  public String updateConsents(
      Map<String, String> metaData, String parentName, String consentArtifact) {
    logger.entry("Begin updateConsents()");
    try {
      CloudHealthcare client = createClient();

      Consent content = new Consent().setMetadata(metaData).setConsentArtifact(consentArtifact);

      Consents.Patch request =
          client
              .projects()
              .locations()
              .datasets()
              .consentStores()
              .consents()
              .patch(parentName, content)
              .setUpdateMask("metadata,consentArtifact");

      Consent response = request.execute();
      logger.info("Consent updated: " + response.toPrettyString());
      return response.toPrettyString();
    } catch (IOException e) {
      logger.error("Updating of Consent failed with an exception", e);
      throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
    }
  }

  public String revokeConsent(String parentName) {
    logger.entry("Begin revokeConsent()");
    try {
      CloudHealthcare client = createClient();

      RevokeConsentRequest content = new RevokeConsentRequest();

      Consents.Revoke request =
          client
              .projects()
              .locations()
              .datasets()
              .consentStores()
              .consents()
              .revoke(parentName, content);

      Consent response = request.execute();
      logger.info("Consent revoked: " + response.toPrettyString());
      return response.toPrettyString();
    } catch (IOException e) {
      logger.error("Revoke Consent failed with an exception", e);
      throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
    }
  }

  public List<Consent> getListOfRevision(String filter, String parentName) {
    logger.entry("Begin getListOfConsents()");
    try {
      CloudHealthcare client = createClient();

      Consents.ListRevisions request =
          client
              .projects()
              .locations()
              .datasets()
              .consentStores()
              .consents()
              .listRevisions(parentName)
              .setFilter(filter);

      ListConsentRevisionsResponse store = request.execute();
      logger.info("ListOfConsent retrieved: \n" + store.toPrettyString());
      return store.getConsents();
    } catch (IOException e) {
      logger.error("Fetching of Consent list failed with an exception", e);
      throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
    }
  }
}
