/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.dao;

import com.google.api.gax.paging.Page;
import com.google.cloud.healthcare.fdamystudies.bean.StoredResponseBean;
import com.google.cloud.healthcare.fdamystudies.config.ApplicationConfiguration;
import com.google.cloud.healthcare.fdamystudies.utils.AppConstants;
import com.google.cloud.healthcare.fdamystudies.utils.ProcessResponseException;
import com.google.cloud.healthcare.fdamystudies.utils.ResponseServerUtil;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class FileResponsesDaoImpl implements ResponsesDao {
  @Autowired private ApplicationConfiguration appConfig;

  private static final Logger logger = LoggerFactory.getLogger(FileResponsesDaoImpl.class);

  @Autowired ResponseServerUtil responseServerUtil;
  @Autowired private Storage storageService;

  @Override
  public void saveStudyMetadata(
      String studyCollectionName, String studyId, Map<String, Object> dataToStore)
      throws ProcessResponseException {
    if (studyCollectionName != null && dataToStore != null) {
      try {

        logger.info(
            "saveActivityResponseData() : \n Study Collection Name: " + studyCollectionName);
        Gson gson = new Gson();
        StringBuilder studyMetadataJsonStr = new StringBuilder();
        studyMetadataJsonStr.append(gson.toJson(dataToStore));
        String studyDirName = "responses/" + studyId + "/StudyMetadata";

        responseServerUtil.saveFile(
            studyCollectionName
                + AppConstants.HYPHEN
                + System.currentTimeMillis()
                + AppConstants.JSON_FILE_EXTENSION,
            studyMetadataJsonStr.toString(),
            studyDirName,
            false);

      } catch (Exception e) {
        logger.error(e.getMessage(), e);
        throw new ProcessResponseException(e.getMessage());
      }
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
      String studyDirName = "responses/" + studyId + "/" + "Activities/" + participantId;

      Page<Blob> blobs =
          storageService.list(
              appConfig.getCloudBucketName(), Storage.BlobListOption.prefix(studyDirName));
      Iterator<Blob> blobIterator = blobs.iterateAll().iterator();

      List<Map<String, Object>> activityResponseMapList = new ArrayList<>();
      while (blobIterator.hasNext()) {
        Blob blob = blobIterator.next();

        String s = responseServerUtil.getDocumentContent(blob.getName());
        String participantIdValue = JsonPath.read(s, String.format("$.participantId"));
        String siteIdValue = JsonPath.read(s, String.format("$.siteId"));
        String activityIdValue = JsonPath.read(s, String.format("$.activityId"));

        /*if (!StringUtils.isBlank(questionKey)) {
          String questionKeyValue = JsonPath.read(s, String.format("$.results[0].key"));
          if (participantIdValue.equals(participantId)
              && siteIdValue.equals(siteId)
              && activityIdValue.equals(activityId)
              && questionKeyValue.equals(questionKey)) {

            activityResponseMapList.add(
                new Gson().fromJson(s, new TypeToken<HashMap<String, Object>>() {}.getType()));
          }
        } else {*/
        if (participantIdValue.equals(participantId)
            && siteIdValue.equals(siteId)
            && activityIdValue.equals(activityId)) {
          activityResponseMapList.add(
              new Gson().fromJson(s, new TypeToken<HashMap<String, Object>>() {}.getType()));
        }
        // }
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
  public void saveActivityResponseData(
      String studyId,
      String studyCollectionName,
      String activitiesCollectionName,
      Map<String, Object> dataToStoreActivityResults)
      throws ProcessResponseException {
    // The conversion of the response data to a file is provided as sample implementation.
    // Implementors should use the file representation of the response object or the
    // JSON representation of the response object to store the responses in a suitable data store
    if (studyCollectionName != null && dataToStoreActivityResults != null) {
      try {

        logger.info(
            "saveActivityResponseData() : \n Study Collection Name: " + studyCollectionName);
        Gson gson = new Gson();
        StringBuilder studyResponseDataJsonStr = new StringBuilder();
        studyResponseDataJsonStr.append(gson.toJson(dataToStoreActivityResults));
        String participantId = (String) dataToStoreActivityResults.get("participantId");
        String studyDirName = "responses/" + studyId + "/Activities" + "/" + participantId;

        responseServerUtil.saveFile(
            studyCollectionName
                + AppConstants.HYPHEN
                + System.currentTimeMillis()
                + AppConstants.JSON_FILE_EXTENSION,
            studyResponseDataJsonStr.toString(),
            studyDirName,
            false);

      } catch (Exception e) {
        logger.error(e.getMessage(), e);
        throw new ProcessResponseException(
            "FileResponsesDaoImpl.saveActivityResponseData() - "
                + "Exception when saving data to file storage: "
                + e.getMessage());
      }
    } else {
      throw new ProcessResponseException(
          "FileResponsesDaoImpl.saveActivityResponseData() - "
              + "Study Collection is null or dataToStoreResults is null");
    }
  }

  @Override
  public void deleteActivityResponseDataForParticipant(
      String studyCollectionName,
      String studyId,
      String activitiesCollectionName,
      String participantId)
      throws ProcessResponseException {
    try {

      String studyDirName = "responses/" + studyId + "/" + "Activities/" + participantId;

      Page<Blob> blobs =
          storageService.list(
              appConfig.getCloudBucketName(), Storage.BlobListOption.prefix(studyDirName));
      Iterator<Blob> blobIterator = blobs.iterateAll().iterator();

      while (blobIterator.hasNext()) {
        Blob blob = blobIterator.next();
        responseServerUtil.deleteDocument(blob.getName());
      }
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
      String studyDirName = "responses/" + studyId + "/" + "Activities/" + participantId;

      Page<Blob> blobs =
          storageService.list(
              appConfig.getCloudBucketName(), Storage.BlobListOption.prefix(studyDirName));
      Iterator<Blob> blobIterator = blobs.iterateAll().iterator();

      while (blobIterator.hasNext()) {
        Blob blob = blobIterator.next();

        String s = responseServerUtil.getDocumentContent(blob.getName());

        JSONObject js = new JSONObject(s);
        js.put(AppConstants.WITHDRAWAL_STATUS_KEY, true);

        responseServerUtil.saveFile(blob.getName(), js.toString(), studyDirName, true);
      }

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ProcessResponseException(e.getMessage());
    }
  }
}
