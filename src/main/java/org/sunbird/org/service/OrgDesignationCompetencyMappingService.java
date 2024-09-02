package org.sunbird.org.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.sunbird.common.model.SBApiResponse;

public interface OrgDesignationCompetencyMappingService {
    ResponseEntity<ByteArrayResource> bulkUploadOrganisationCompetencyMapping(String rootOrgId, String userAuthToken, String frameworkId);
}
