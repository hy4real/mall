package com.macro.mall.common.domain;

public class SwaggerProperties {
    private String apiBasePackage;
    private boolean enableSecurity;
    private String title;
    private String description;
    private String version;
    private String contactName;
    private String contactUrl;
    private String contactEmail;

    public SwaggerProperties() {
    }

    public SwaggerProperties(String apiBasePackage, boolean enableSecurity, String title,
                              String description, String version, String contactName,
                              String contactUrl, String contactEmail) {
        this.apiBasePackage = apiBasePackage;
        this.enableSecurity = enableSecurity;
        this.title = title;
        this.description = description;
        this.version = version;
        this.contactName = contactName;
        this.contactUrl = contactUrl;
        this.contactEmail = contactEmail;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiBasePackage() { return apiBasePackage; }
    public void setApiBasePackage(String apiBasePackage) { this.apiBasePackage = apiBasePackage; }
    public boolean isEnableSecurity() { return enableSecurity; }
    public void setEnableSecurity(boolean enableSecurity) { this.enableSecurity = enableSecurity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactUrl() { return contactUrl; }
    public void setContactUrl(String contactUrl) { this.contactUrl = contactUrl; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public static class Builder {
        private String apiBasePackage;
        private boolean enableSecurity;
        private String title;
        private String description;
        private String version;
        private String contactName;
        private String contactUrl;
        private String contactEmail;

        public Builder apiBasePackage(String apiBasePackage) { this.apiBasePackage = apiBasePackage; return this; }
        public Builder enableSecurity(boolean enableSecurity) { this.enableSecurity = enableSecurity; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder contactName(String contactName) { this.contactName = contactName; return this; }
        public Builder contactUrl(String contactUrl) { this.contactUrl = contactUrl; return this; }
        public Builder contactEmail(String contactEmail) { this.contactEmail = contactEmail; return this; }

        public SwaggerProperties build() {
            return new SwaggerProperties(apiBasePackage, enableSecurity, title, description,
                    version, contactName, contactUrl, contactEmail);
        }
    }
}
