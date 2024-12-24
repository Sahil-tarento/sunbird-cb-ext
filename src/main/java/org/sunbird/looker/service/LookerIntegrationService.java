package org.sunbird.looker.service;

import org.sunbird.common.model.SBApiResponse;
import org.sunbird.common.model.SunbirdApiRequest;

public interface LookerIntegrationService {

    SBApiResponse createSignedEmbedUrl(SunbirdApiRequest requestBody);
}
