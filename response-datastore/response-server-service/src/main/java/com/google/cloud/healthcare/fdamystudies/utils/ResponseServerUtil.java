/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.utils;

import com.google.cloud.WriteChannel;
import com.google.cloud.healthcare.fdamystudies.bean.ResponseRows;
import com.google.cloud.healthcare.fdamystudies.bean.SavedActivityResponse;
import com.google.cloud.healthcare.fdamystudies.bean.StoredResponseBean;
import com.google.cloud.healthcare.fdamystudies.common.ErrorCode;
import com.google.cloud.healthcare.fdamystudies.config.ApplicationConfiguration;
import com.google.cloud.healthcare.fdamystudies.exceptions.ErrorCodeException;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ResponseServerUtil {
  private static final Logger logger = LoggerFactory.getLogger(ResponseServerUtil.class);

  @Autowired private Storage storageService;
  @Autowired private ApplicationConfiguration appConfig;

  public enum ErrorCodes {
    INVALID_INPUT("INVALID_INPUT"),
    INVALID_INPUT_ERROR_MSG("Invalid input."),
    STATUS_101("101"),
    STATUS_102("102"), // Unknown Error;
    STATUS_103("103"), // No Data available.
    STATUS_104("104"), // Invalid Inputs (If any of the input parameter is missing).
    STATUS_128("128"), // Invalid UserId
    STATUS_129("129"), // Client Id is missing
    STATUS_130("130"), // Secret Key is missing
    STATUS_131("131"), // No Study Found
    SESSION_EXPIRED_MSG("Session expired."),
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE");
    private final String value;

    ErrorCodes(final String newValue) {
      value = newValue;
    }

    public String getValue() {
      return value;
    }
  }

  public static void getFailureResponse(
      String status, String title, String message, HttpServletResponse response) {
    try {
      response.setHeader("status", status);
      response.setHeader("title", title);
      response.setHeader("StatusMessage", message);

      if (status.equalsIgnoreCase(ErrorCodes.STATUS_104.getValue())) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
      }

      if (status.equalsIgnoreCase(ErrorCodes.STATUS_102.getValue())) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
      }
      if (status.equalsIgnoreCase(ErrorCodes.STATUS_101.getValue())
          || status.equalsIgnoreCase(ErrorCodes.STATUS_128.getValue())
          || status.equalsIgnoreCase(ErrorCodes.STATUS_131.getValue())) {
        if (message.equalsIgnoreCase(ErrorCodes.SESSION_EXPIRED_MSG.getValue())) {
          response.sendError(
              HttpServletResponse.SC_UNAUTHORIZED, ErrorCodes.SESSION_EXPIRED_MSG.getValue());
        } else {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
        }
      }

      if (status.equalsIgnoreCase(ErrorCodes.STATUS_103.getValue())) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
      }

    } catch (Exception e) {
      logger.info("ResponseServerUtil - getFailureResponse() :: ERROR ", e);
    }
  }

  public static String getHashedValue(String secretToHash) {
    logger.info("ResponseServerUtil - getHashedValue() - starts");
    String generatedHash = null;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] bytes = md.digest(secretToHash.getBytes());
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < bytes.length; i++) {
        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
      }
      generatedHash = sb.toString();
    } catch (NoSuchAlgorithmException e) {
      logger.info("ResponseServerUtil getHashedValue() - error() ", e);
    }
    logger.info("ResponseServerUtil - getHashedValue() - ends");
    return generatedHash;
  }

  public String saveFile(
      String fileName, String content, String underDirectory, boolean isAbsoluteFileName) {
    String absoluteFileName = null;
    if (!StringUtils.isBlank(content)) {
      if (isAbsoluteFileName) {
        absoluteFileName = fileName;
      } else {
        absoluteFileName = underDirectory == null ? fileName : underDirectory + "/" + fileName;
      }

      BlobInfo blobInfo =
          BlobInfo.newBuilder(appConfig.getCloudBucketName(), absoluteFileName).build();
      byte[] bytes = null;

      try (WriteChannel writer = storageService.writer(blobInfo)) {
        bytes = content.getBytes();
        writer.write(ByteBuffer.wrap(bytes, 0, bytes.length));
      } catch (IOException e) {
        logger.error("Save file in cloud storage failed", e);
        throw new ErrorCodeException(ErrorCode.APPLICATION_ERROR);
      }
    }
    return absoluteFileName;
  }

  public String getDocumentContent(String filepath) {
    if (StringUtils.isNotBlank(filepath)) {
      Blob blob = storageService.get(BlobId.of(appConfig.getCloudBucketName(), filepath));
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      blob.downloadTo(outputStream);
      return new String(blob.getContent());
    }

    return StringUtils.EMPTY;
  }

  public void deleteDocument(String filepath) {
    if (StringUtils.isNotBlank(filepath)) {
      storageService.delete(BlobId.of(appConfig.getCloudBucketName(), filepath));
    }
  }

  public StoredResponseBean convertResponseDataToBean(
      String participantId,
      List<Map<String, Object>> activityResponseMapList,
      StoredResponseBean storedResponseBean) {
    logger.info("begin convertResponseDataToBean()");
    List<ResponseRows> responsesList = new ArrayList<>();
    for (Map<String, Object> activityResponseMap : activityResponseMapList) {
      ResponseRows responsesRow = new ResponseRows();
      // Add participant Id
      Map<Object, Object> mapPartId = new HashMap<>();
      Map<Object, Object> mapPartIdValue = new HashMap<>();
      mapPartIdValue.put(AppConstants.VALUE_KEY_STR, participantId);
      mapPartId.put(AppConstants.PARTICIPANT_ID_RESPONSE, mapPartIdValue);
      responsesRow.getData().add(mapPartId);

      // Add Created Timestamp
      Map<Object, Object> mapTS = new HashMap<>();
      Map<Object, Object> mapTsValue = new HashMap<>();

      // Format timestamp to date
      long timestampFromResponse = 0;
      try {
        timestampFromResponse =
            Long.parseLong((String) activityResponseMap.get(AppConstants.CREATED_TS_KEY));

        DateFormat simpleDateFormat = new SimpleDateFormat(AppConstants.ISO_DATE_FORMAT_RESPONSE);
        String formattedDate = simpleDateFormat.format(timestampFromResponse);
        mapTsValue.put(AppConstants.VALUE_KEY_STR, formattedDate);

      } catch (NumberFormatException ne) {
        logger.error(
            "Could not format createdTimestamp field to long. createdTimestamp value is: "
                + timestampFromResponse);
        mapTsValue.put(AppConstants.VALUE_KEY_STR, String.valueOf(timestampFromResponse));
      }

      mapTS.put(AppConstants.CREATED_RESPONSE, mapTsValue);
      responsesRow.getData().add(mapTS);
      SavedActivityResponse savedActivityResponse =
          new Gson().fromJson(new Gson().toJson(activityResponseMap), SavedActivityResponse.class);
      List<Object> results = savedActivityResponse.getResults();
      this.addResponsesToMap(responsesRow, results);
      responsesList.add(responsesRow);
      storedResponseBean.setRows(responsesList);
    }
    if (storedResponseBean.getRows() != null) {
      storedResponseBean.setRowCount(storedResponseBean.getRows().size());
    }
    return storedResponseBean;
  }

  private void addResponsesToMap(ResponseRows responsesRow, List<Object> results) {
    logger.info("begin addResponsesToMap()");
    if (results != null) {
      for (Object result : results) {
        if (result instanceof Map) {
          Map<String, Object> mapResult = (Map<String, Object>) result;
          String questionResultType = (String) mapResult.get(AppConstants.RESULT_TYPE_KEY);
          String questionIdKey = null;
          String questionValue = null;
          Map<Object, Object> tempMapForQuestions = new HashMap<>();
          Map<Object, Object> tempMapQuestionsValue = new HashMap<>();

          if (!StringUtils.isBlank(questionResultType)) {
            if (questionResultType.equalsIgnoreCase(AppConstants.GROUPED_FIELD_KEY)) {
              Map<String, Object> resultsForm =
                  (Map<String, Object>) mapResult.get("actvityValueGroup");
              List<Object> obj = (List<Object>) resultsForm.get("results");
              this.addResponsesToMap(responsesRow, obj);

            } else {
              questionIdKey = (String) mapResult.get(AppConstants.QUESTION_ID_KEY);
              questionValue = (String) mapResult.get(AppConstants.VALUE_KEY_STR);
              if (StringUtils.containsIgnoreCase(
                      appConfig.getResponseSupportedQTypeDouble(), questionResultType)
                  && !StringUtils.isBlank(questionValue)) {
                Double questionValueDouble = null;
                try {
                  questionValueDouble = Double.parseDouble(questionValue);
                  tempMapQuestionsValue.put(AppConstants.VALUE_KEY_STR, questionValueDouble);
                  tempMapForQuestions.put(questionIdKey, tempMapQuestionsValue);
                  responsesRow.getData().add(tempMapForQuestions);
                } catch (NumberFormatException e) {
                  logger.error(
                      "Could not format value to Double. Value input string is: " + questionValue);
                }
              } else if (StringUtils.containsIgnoreCase(
                      appConfig.getResponseSupportedQTypeDate(), questionResultType)
                  && !StringUtils.isBlank(questionValue)) {
                tempMapQuestionsValue.put(AppConstants.VALUE_KEY_STR, questionValue);
                tempMapForQuestions.put(questionIdKey, tempMapQuestionsValue);
                responsesRow.getData().add(tempMapForQuestions);
              } else {
                if (appConfig.getSupportStringResponse().equalsIgnoreCase(AppConstants.TRUE_STR)
                    && StringUtils.containsIgnoreCase(
                        appConfig.getResponseSupportedQTypeString(), questionResultType)
                    && !StringUtils.isBlank(questionValue)) {
                  tempMapQuestionsValue.put(AppConstants.VALUE_KEY_STR, questionValue);
                  tempMapForQuestions.put(questionIdKey, tempMapQuestionsValue);
                  responsesRow.getData().add(tempMapForQuestions);
                }
              }
            }
          }
        }
      }
    }
  }

  public List<Map<String, Object>> filterResponseListByTimestamp(
      List<Map<String, Object>> activityResponseMapList) {

    activityResponseMapList.sort(
        Comparator.nullsLast(
            Comparator.comparing(
                m -> Long.parseLong((String) m.get(AppConstants.CREATED_TS_KEY)),
                Comparator.nullsLast(Comparator.reverseOrder()))));
    // Get the latest response for activityId, bases on ordering by timestamp value
    activityResponseMapList = Arrays.asList(activityResponseMapList.get(0));

    return activityResponseMapList;
  }

  public StoredResponseBean initStoredResponseBean() {
    StoredResponseBean retStoredResponseBean = new StoredResponseBean();
    List<String> schemaNameList = Arrays.asList(AppConstants.RESPONSE_DATA_SCHEMA_NAME_LEGACY);
    retStoredResponseBean.setSchemaName(schemaNameList);
    retStoredResponseBean.setQueryName(AppConstants.RESPONSE_DATA_QUERY_NAME_LEGACY);
    return retStoredResponseBean;
  }
}
