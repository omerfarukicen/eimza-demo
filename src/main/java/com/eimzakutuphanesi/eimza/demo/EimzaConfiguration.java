package com.eimzakutuphanesi.eimza.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.eimzakutuphanesi.eimza.EimzaService;
import com.eimzakutuphanesi.eimza.config.EimzaConfig;
import com.eimzakutuphanesi.eimza.tsp.TimestampProvider;

import java.nio.file.Path;

@Configuration
public class EimzaConfiguration {

    @Bean
    public EimzaService eimzaService(EimzaProperties properties) {
        EimzaConfig.Builder builder = EimzaConfig.builder()
                .onlineRevocation(properties.isOnlineRevocation())
                .bundledTrustStore(properties.isBundledTrustStore())
                .lenientAugmentation(properties.isLenientAugmentation())
                .depotDownload(properties.isDepotDownload());
        if (properties.getDepotCache() != null && !properties.getDepotCache().isBlank()) {
            builder.depotCache(Path.of(properties.getDepotCache()));
        }
        properties.getSvtFiles().forEach(file -> builder.svtFile(Path.of(file)));
        properties.getTrustedCertificateDirectories().forEach(dir -> builder.trustedCertificateDirectory(Path.of(dir)));
        properties.getLibrarySearchDirectories().forEach(dir -> builder.librarySearchDirectory(Path.of(dir)));
        properties.getLibraryOverrides().forEach((card, lib) -> builder.libraryOverride(card, Path.of(lib)));
        properties.getTimestampProviders().stream()
                .filter(ts -> ts.getUrl() != null && !ts.getUrl().isBlank())
                .forEach(ts -> builder.timestampProvider(new TimestampProvider(ts.getUrl(), ts.getUserId(), ts.getPassword())));
        return new EimzaService(builder.build());
    }
}
