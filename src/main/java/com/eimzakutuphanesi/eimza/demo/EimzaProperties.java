package com.eimzakutuphanesi.eimza.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "eimza")
public class EimzaProperties {

    private boolean onlineRevocation = true;
    private boolean bundledTrustStore = true;
    private boolean lenientAugmentation;
    private boolean depotDownload = true;
    private String depotCache;
    private List<String> svtFiles = new ArrayList<>();
    private List<String> trustedCertificateDirectories = new ArrayList<>();
    private List<String> librarySearchDirectories = new ArrayList<>();
    private Map<String, String> libraryOverrides = new LinkedHashMap<>();
    private List<Timestamp> timestampProviders = new ArrayList<>();
    private String hsmLibrary;

    public boolean isOnlineRevocation() {
        return onlineRevocation;
    }

    public void setOnlineRevocation(boolean onlineRevocation) {
        this.onlineRevocation = onlineRevocation;
    }

    public boolean isBundledTrustStore() {
        return bundledTrustStore;
    }

    public void setBundledTrustStore(boolean bundledTrustStore) {
        this.bundledTrustStore = bundledTrustStore;
    }

    public boolean isLenientAugmentation() {
        return lenientAugmentation;
    }

    public void setLenientAugmentation(boolean lenientAugmentation) {
        this.lenientAugmentation = lenientAugmentation;
    }

    public boolean isDepotDownload() {
        return depotDownload;
    }

    public void setDepotDownload(boolean depotDownload) {
        this.depotDownload = depotDownload;
    }

    public String getDepotCache() {
        return depotCache;
    }

    public void setDepotCache(String depotCache) {
        this.depotCache = depotCache;
    }

    public List<String> getSvtFiles() {
        return svtFiles;
    }

    public void setSvtFiles(List<String> svtFiles) {
        this.svtFiles = svtFiles;
    }

    public List<String> getTrustedCertificateDirectories() {
        return trustedCertificateDirectories;
    }

    public void setTrustedCertificateDirectories(List<String> trustedCertificateDirectories) {
        this.trustedCertificateDirectories = trustedCertificateDirectories;
    }

    public List<String> getLibrarySearchDirectories() {
        return librarySearchDirectories;
    }

    public void setLibrarySearchDirectories(List<String> librarySearchDirectories) {
        this.librarySearchDirectories = librarySearchDirectories;
    }

    public Map<String, String> getLibraryOverrides() {
        return libraryOverrides;
    }

    public void setLibraryOverrides(Map<String, String> libraryOverrides) {
        this.libraryOverrides = libraryOverrides;
    }

    public List<Timestamp> getTimestampProviders() {
        return timestampProviders;
    }

    public void setTimestampProviders(List<Timestamp> timestampProviders) {
        this.timestampProviders = timestampProviders;
    }

    public String getHsmLibrary() {
        return hsmLibrary;
    }

    public void setHsmLibrary(String hsmLibrary) {
        this.hsmLibrary = hsmLibrary;
    }

    public static class Timestamp {
        private String url;
        private String userId;
        private String password;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
