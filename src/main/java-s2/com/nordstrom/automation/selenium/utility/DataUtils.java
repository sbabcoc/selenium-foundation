package com.nordstrom.automation.selenium.utility;

import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * This static utility class contains methods related to representational transformation.
 */
public final class DataUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DataUtils.class);
    
    /**
     * Private constructor to prevent instantiation.
     */
    private DataUtils() {
        throw new AssertionError("DataUtils is a static utility class that cannot be instantiated");
    }
    
    /**
     * Transform the specified JSON string into the specified type.
     * 
     * @param <T> desired object type
     * @param json JSON object string
     * @param type target object type
     * @return new instance of the specified type
     */
    public static <T> T fromString(final String json, final Type type) {
        try {
            return new Gson().fromJson(json, type);
        } catch (JsonSyntaxException e) {
            LOGGER.debug("Failed to deserialize JSON object string: " + json, e);
            return null;
        }
    }
    
    /**
     * Transform the specified Java object into its JSON string representation.
     * 
     * @param object Java object to be transformed
     * @return JSON representation of {@code object}
     */
    public static String toString(final Object object) {
        return new Gson().toJson(object);
    }

}
