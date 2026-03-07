package com.omumu.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OmumuConfig {

    @JsonProperty("default")
    private Profile defaultProfile;

    private Map<String, Profile> sites = new LinkedHashMap<>();

    public Profile getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(Profile defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public Map<String, Profile> getSites() {
        return sites;
    }

    public void setSites(Map<String, Profile> sites) {
        this.sites = sites;
    }

    public Profile resolveProfile(String siteName) {
        if (siteName != null && sites.containsKey(siteName)) {
            return sites.get(siteName);
        }
        return defaultProfile;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        private String url;
        @JsonProperty("api_key")
        private String apiKey;

        public Profile() {}

        public Profile(String url, String apiKey) {
            this.url = url;
            this.apiKey = apiKey;
        }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
