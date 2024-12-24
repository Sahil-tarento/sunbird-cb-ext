package org.sunbird.looker.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sunbird.common.model.SBApiResponse;
import org.sunbird.common.model.SunbirdApiRequest;
import org.sunbird.looker.service.LookerIntegrationService;

@RestController
@RequestMapping("looker")
public class LookerIntegrationController {

    @Autowired
    LookerIntegrationService lookerIntegrationService;

    @PostMapping("/generateSignedUrl")
    public @ResponseBody ResponseEntity<SBApiResponse> generateSignedUrl(@RequestBody SunbirdApiRequest requestBody) {

        SBApiResponse response = lookerIntegrationService.createSignedEmbedUrl(requestBody);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        return ResponseEntity.status(response.getResponseCode()).headers(headers).body(response);
    }
}
