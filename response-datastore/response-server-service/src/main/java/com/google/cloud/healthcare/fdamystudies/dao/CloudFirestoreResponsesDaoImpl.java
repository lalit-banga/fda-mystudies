/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.dao;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.healthcare.fdamystudies.bean.StoredResponseBean;
import com.google.cloud.healthcare.fdamystudies.config.ApplicationConfiguration;
import com.google.cloud.healthcare.fdamystudies.utils.AppConstants;
import com.google.cloud.healthcare.fdamystudies.utils.ProcessResponseException;
import com.google.cloud.healthcare.fdamystudies.utils.ResponseServerUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Repository;

@Repository
public class CloudFirestoreResponsesDaoImpl implements ResponsesDao {
  @Autowired private ApplicationConfiguration appConfig;
  private Firestore responsesDb;
  private XLogger logger =
      XLoggerFactory.getXLogger(CloudFirestoreResponsesDaoImpl.class.getName());
  @Autowired ResponseServerUtil responseServerUtil;

  @Override
  @Retryable(
      value = {Exception.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 500))
  public void saveStudyMetadata(
      String studyCollectionName, String studyId, Map<String, Object> dataToStore)
      throws ProcessResponseException {
    if (studyCollectionName != null && studyId != null && dataToStore != null) {
      try {

        logger.info("saveStudyMetadata() : \n Study Collection Name: " + studyCollectionName);
        initializeFirestore();
        ApiFuture<WriteResult> cr =
            this.responsesDb.collection(studyCollectionName).document(studyId).set(dataToStore);
        logger.debug(
            "saveStudyMetadata() : \n Study Collection Name: "
                + studyCollectionName
                + " added successfully");

      } catch (Exception e) {
        logger.error(e.getMessage(), e);
        throw new ProcessResponseException(e.getMessage());
      }
    } else {

      throw new ProcessResponseException(
          "CloudFirestoreResponsesDaoImpl.saveStudyMetadata() "
              + "- Study Collection is null or dataToStore is null");
    }
  }

  @Override
  @Retryable(
      value = {Exception.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 500))
  public void saveActivityResponseData(
      String studyId,
      String studyCollectionName,
      String activitiesCollectionName,
      Map<String, Object> dataToStoreActivityResults)
      throws ProcessResponseException {
    try {
      initializeFirestore();

      Map<String, Object> studyVersionMap = new HashMap<>();
      studyVersionMap.put("studyVersion", dataToStoreActivityResults.get("studyVersion"));
      ApiFuture<WriteResult> futuresStudyColl =
          this.responsesDb.collection(studyCollectionName).document(studyId).set(studyVersionMap);
      WriteResult wresultStudy = futuresStudyColl.get();
      logger.debug("Updated time: " + wresultStudy.getUpdateTime());
      ApiFuture<WriteResult> futuresActivities =
          this.responsesDb
              .collection(studyCollectionName)
              .document(studyId)
              .collection(activitiesCollectionName)
              .document()
              .set(dataToStoreActivityResults);
      WriteResult wresult = futuresActivities.get();
      logger.debug("Updated time: " + wresult.getUpdateTime());
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }
  }

  @Override
  public StoredResponseBean getActivityResponseDataForParticipant(
      String studyCollectionName,
      String studyId,
      String siteId,
      String participantId,
      String activityId,
      String questionKey)
      throws ProcessResponseException {
    try {
      initializeFirestore();
      // Firestore does not allow compound queries without creating an index. Indexes can be created
      // only through the console or CLI, not programmatically. So this method will not depend on
      // the index to sort the data, based on timestamp in firestore. It will do the sort on the
      // query result object
      //
      Query activitiesQuery =
          this.responsesDb
              .collection(studyCollectionName)
              .document(studyId)
              .collection(AppConstants.ACTIVITIES_COLLECTION_NAME)
              .whereEqualTo(AppConstants.PARTICIPANT_ID_KEY, participantId)
              .whereEqualTo(AppConstants.SITE_ID_KEY, siteId)
              .whereEqualTo(AppConstants.ACTIVITY_ID_KEY, activityId)
              .whereEqualTo("results." + AppConstants.QUESTION_ID_KEY, questionKey);

      final ApiFuture<QuerySnapshot> querySnapshotActivities = activitiesQuery.get();
      List<QueryDocumentSnapshot> documentsActivities =
          querySnapshotActivities.get().getDocuments();
      List<Map<String, Object>> activityResponseMapList = new ArrayList<>();
      for (QueryDocumentSnapshot documentActivity : documentsActivities) {
        Map<String, Object> mapObj = documentActivity.getData();
        activityResponseMapList.add(mapObj);
      }

      if (!activityResponseMapList.isEmpty()) {
        String lastResponseOnly = appConfig.getLastResponseOnly();
        if (!StringUtils.isBlank(lastResponseOnly)
            && lastResponseOnly.equalsIgnoreCase(AppConstants.TRUE_STR)) {
          activityResponseMapList =
              responseServerUtil.filterResponseListByTimestamp(activityResponseMapList);
        }
        StoredResponseBean storedResponseBean = responseServerUtil.initStoredResponseBean();
        storedResponseBean =
            responseServerUtil.convertResponseDataToBean(
                participantId, activityResponseMapList, storedResponseBean);
        return storedResponseBean;
      }
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }
    return null;
  }

  @Override
  public void deleteActivityResponseDataForParticipant(
      String studyCollectionName,
      String studyId,
      String activitiesCollectionName,
      String participantId)
      throws ProcessResponseException {
    try {
      initializeFirestore();
      final Query activitiesQueryByParticipantId =
          this.responsesDb
              .collection(studyCollectionName)
              .document(studyId)
              .collection(AppConstants.ACTIVITIES_COLLECTION_NAME)
              .whereEqualTo(AppConstants.PARTICIPANT_ID_KEY, participantId);

      final ApiFuture<QuerySnapshot> querySnapshot = activitiesQueryByParticipantId.get();
      List<QueryDocumentSnapshot> documents;

      documents = querySnapshot.get().getDocuments();

      WriteBatch deleteBatch = this.responsesDb.batch();
      int batchCount = 0;
      for (QueryDocumentSnapshot document : documents) {
        deleteBatch.delete(document.getReference());
        batchCount++;
        // Firestore - Each transaction or batch of writes can write to a maximum of 500 documents.
        if (batchCount == AppConstants.FS_BATCH_COMMIT_LIMIT) {
          deleteBatch.commit();
          // Reset the batch, once it has been committed, so it is available again
          deleteBatch = this.responsesDb.batch();
          batchCount = 0;
        }
      }
      deleteBatch.commit();

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }
  }

  @Override
  public void updateWithdrawalStatusForParticipant(
      String studyCollectionName, String studyId, String participantId)
      throws ProcessResponseException {
    try {
      initializeFirestore();
      final Query activitiesQueryByParticipantId =
          this.responsesDb
              .collection(studyCollectionName)
              .document(studyId)
              .collection(AppConstants.ACTIVITIES_COLLECTION_NAME)
              .whereEqualTo(AppConstants.PARTICIPANT_ID_KEY, participantId);

      final ApiFuture<QuerySnapshot> querySnapshot = activitiesQueryByParticipantId.get();
      List<QueryDocumentSnapshot> documents;

      documents = querySnapshot.get().getDocuments();

      WriteBatch updateBatch = this.responsesDb.batch();
      int batchCount = 0;
      for (QueryDocumentSnapshot document : documents) {

        updateBatch.update(document.getReference(), AppConstants.WITHDRAWAL_STATUS_KEY, true);
        batchCount++;
        // Firestore - Each transaction or batch of writes can write to a maximum of 500 documents.
        if (batchCount == AppConstants.FS_BATCH_COMMIT_LIMIT) {
          updateBatch.commit();
          // Reset the batch, once it has been committed, so it is available again
          updateBatch = this.responsesDb.batch();
          batchCount = 0;
        }
      }
      updateBatch.commit();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }
  }

  public void getResponseDataScenarios(
      String studyCollectionName,
      String studyId,
      String siteId,
      String participantId,
      String activityId)
      throws ProcessResponseException {
    initializeFirestore();
    // Sample queries
    // This is example code, to retrieve the response.
    // Do something with the response, based on the implementation

    // 1. Get response data for an activity for a study
    try {
      final Query queryByActivity =
          this.responsesDb
              .collection(studyCollectionName)
              .document(studyId)
              .collection(AppConstants.ACTIVITIES_COLLECTION_NAME)
              .whereEqualTo(AppConstants.ACTIVITY_ID_KEY, activityId);
      ;
      List<Map<String, Object>> responseList = this.getResponseForQuery(queryByActivity);
      // Do something with the response
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }
    // 2. Get response data for all activities, by participant Id
    try {
      final Query queryParticipant =
          this.responsesDb
              .collection(studyCollectionName)
              .document(studyId)
              .collection(AppConstants.ACTIVITIES_COLLECTION_NAME)
              .whereEqualTo(AppConstants.PARTICIPANT_ID_KEY, participantId);
      List<Map<String, Object>> responseList = this.getResponseForQuery(queryParticipant);
      // Do something with the response
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }

    // 3. Get response data for all activities, by site Id
    try {
      final Query querySite =
          this.responsesDb
              .collection(studyCollectionName)
              .document(studyId)
              .collection(AppConstants.ACTIVITIES_COLLECTION_NAME)
              .whereEqualTo(AppConstants.SITE_ID_KEY, siteId);
      List<Map<String, Object>> responseList = this.getResponseForQuery(querySite);
      // Do something with the response
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }
    // 4. Get response data by activity id, by site Id
    try {
      final Query activitiesQueryById =
          this.responsesDb
              .collection(studyCollectionName)
              .document(studyId)
              .collection(AppConstants.ACTIVITIES_COLLECTION_NAME)
              .whereEqualTo(AppConstants.SITE_ID_KEY, siteId)
              .whereEqualTo(AppConstants.ACTIVITY_ID_KEY, activityId);
      List<Map<String, Object>> responseList = this.getResponseForQuery(activitiesQueryById);
      // Do something with the response
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }
  }

  private List<Map<String, Object>> getResponseForQuery(final Query query)
      throws InterruptedException, ExecutionException {
    final ApiFuture<QuerySnapshot> querySnapshot = query.get();
    List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
    List<Map<String, Object>> responseListRet = new ArrayList<>();
    for (QueryDocumentSnapshot document : documents) {
      responseListRet.add(document.getData());
    }
    return responseListRet;
  }

  private void initializeFirestore() {
    if (this.responsesDb == null) {
      logger.debug("In CloudFirestoreResponsesDaoImpl constructor, initializing Firestore");
      FirestoreOptions firestoreOptions =
          FirestoreOptions.getDefaultInstance()
              .toBuilder()
              .setProjectId(appConfig.getFirestoreProjectId())
              .build();
      logger.debug("In CloudFirestoreResponsesDaoImpl constructor, Firestore Options initialized");
      this.responsesDb = firestoreOptions.getService();
      logger.debug("In CloudFirestoreResponsesDaoImpl constructor, Firestore DB initialized");
    }
  }
}
