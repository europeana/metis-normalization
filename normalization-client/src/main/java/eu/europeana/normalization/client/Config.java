package eu.europeana.normalization.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Config of the client
 * 
 * @author Nuno Freire (nfreire@gmail.com)
 * @since 16/05/2016
 */
public class Config {

    private static String normalizationServiceUrl;

    public Config() {
        Properties props = new Properties();
        try {
            props.load(Config.class.getResourceAsStream("/normalization-service.properties"));
            normalizationServiceUrl = props.getProperty("normalization.service.url");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNormalizationServiceUrl() {
        return normalizationServiceUrl;
    }

}
