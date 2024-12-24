package org.sunbird.looker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.sunbird.common.model.SBApiResponse;
import org.sunbird.common.model.SunbirdApiRequest;
import org.sunbird.common.util.CbExtServerProperties;
import org.sunbird.common.util.Constants;
import org.sunbird.common.util.ProjectUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class LookerIntegrationServiceImpl implements LookerIntegrationService {

    @Autowired
    CbExtServerProperties serverProperties;

    private static final int SESSION_LENGTH = 15 * 60;

    @Override
    public SBApiResponse createSignedEmbedUrl(SunbirdApiRequest requestBody) {
        SBApiResponse response = ProjectUtil.createDefaultResponse(Constants.LOOKER_SIGNED_URL);
        try {
            String nonce = generateNonce();
            long time = Instant.now().getEpochSecond();
            Map<String, Object> map = (Map<String, Object>) requestBody.getRequest();
            String embedUrl = (String) map.get("embedUrl");

            if (StringUtils.isEmpty(embedUrl)) {
                response.getParams().setErrmsg("embedUrl is missing from the request");
                response.getParams().setStatus(Constants.FAILED);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                return response;
            }
            Map<String, Object> userAttributesMap = (Map<String, Object>) map.get("userAttributes");
            if (MapUtils.isEmpty(userAttributesMap)) {
                response.getParams().setErrmsg("userAttributes is missing from the request");
                response.getParams().setStatus(Constants.FAILED);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                return response;
            }

            String externalUserId = (String) userAttributesMap.get("userId");

            if (StringUtils.isEmpty(externalUserId)) {
                response.getParams().setErrmsg("userId is missing from userAttributes from the request");
                response.getParams().setStatus(Constants.FAILED);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                return response;
            }

            String embedPath = "/login/embed/" + UriUtils.encode(embedUrl, StandardCharsets.UTF_8);

            List<String> permissions = serverProperties.getLookerUserDashboardPermission();
            List<String> models = serverProperties.getLookerUserModel();
            List<String> groupIds = serverProperties.getLookerUserGroupIDs();
            String externalGroupId = "5";
            Map<String, Object> userAttributes = new HashMap<>();
            userAttributes.put("user_id", externalUserId);

            Map<String, Object> accessFilters = new HashMap<>();
            Map<String, Object> fakeModel = new HashMap<>();
            fakeModel.put("id", 1);
            accessFilters.put("fake_model", fakeModel);
            ObjectMapper objectMapper = new ObjectMapper();

            String stringToSign = String.join("\n", Arrays.asList(
                    serverProperties.getLookerHost(),
                    embedPath,
                    "\"" + nonce + "\"",
                    "\"" + time + "\"",
                    "\"" + SESSION_LENGTH + "\"",
                    "\"" + externalUserId + "\"",
                    objectMapper.writeValueAsString(permissions),
                    objectMapper.writeValueAsString(models),
                    objectMapper.writeValueAsString(groupIds),
                    "\"" + externalGroupId + "\"",
                    objectMapper.writeValueAsString(userAttributes),
                    objectMapper.writeValueAsString(accessFilters)
            ));

            String signature = signString(serverProperties.getLookerSecretKey(), stringToSign);

            // Use UriComponentsBuilder for query parameter construction
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance()
                    .scheme("https")
                    .host(serverProperties.getLookerHost())
                    .path(embedPath)
                    .queryParam("nonce", objectMapper.writeValueAsString(nonce))
                    .queryParam("time", time)
                    .queryParam("session_length", SESSION_LENGTH)
                    .queryParam("external_user_id", objectMapper.writeValueAsString(externalUserId))
                    .queryParam("permissions", objectMapper.writeValueAsString(permissions))
                    .queryParam("models", objectMapper.writeValueAsString(models))
                    .queryParam("group_ids", objectMapper.writeValueAsString(groupIds))
                    .queryParam("external_group_id", objectMapper.writeValueAsString(externalGroupId))
                    .queryParam("user_attributes", objectMapper.writeValueAsString(userAttributes))
                    .queryParam("access_filters", objectMapper.writeValueAsString(accessFilters))
                    .queryParam("signature", signature)
                    .queryParam("force_logout_login", true);

            String signedUrl = uriBuilder.build().encode().toUriString();
            response.getResult().put("signedUrl", signedUrl);
        } catch (Exception e) {
            response.getParams().setErrmsg(e.getMessage());
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    private String signString(String secret, String stringToSign) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes).trim();
    }

    private String generateNonce() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

}

