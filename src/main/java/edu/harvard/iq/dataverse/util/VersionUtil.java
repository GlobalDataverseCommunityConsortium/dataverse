package edu.harvard.iq.dataverse.util;

import java.util.HashMap;
import java.util.Map;

/*** Version parsing/math */

public class VersionUtil {

    /**
     * Parse a version string of the form "X.Y.Z<suffix>" into major, minor, and patch integers.
     * 
     * @param versionString The version string to parse (e.g., "6.7.1-qdr" or "5.10 build 1234")
     * @return An array of integers [major, minor, patch]
     * @throws HttpResponseException If the version string cannot be parsed
     */
    
    public static Map<String, Integer> parseVersion(String versionString) throws IllegalArgumentException {
    
        if (versionString == null || versionString.isEmpty()) {
            throw new IllegalArgumentException("Version string is empty or null");
        }
    
        // Split by dots
        String[] parts = versionString.split("\\.");
    
        // We need at least major and minor version
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid version format: " + versionString + ". Expected format is X.Y<.Z>*");
        }
    
        Map<String, Integer> versionMap = new HashMap<String, Integer>();
        try {
            // Parse major version
            versionMap.put("major", Integer.parseInt(parts[0]));
    
            // Parse minor version
            int endIndex = findFirstNonDigitIndex(parts[1]);
            if (endIndex == 0) {
                throw new IllegalArgumentException("Invalid version format: " + versionString);
            }
            if(endIndex == -1) {
                versionMap.put("minor", Integer.parseInt(parts[1]));
            } else {
                versionMap.put("minor", Integer.parseInt(parts[1].substring(0, endIndex)));
            }
            // Parse patch version if available, otherwise default to 0
            versionMap.put("patch", 0);
            if (parts.length > 2 && parts[2].length() > 0) {
                endIndex = findFirstNonDigitIndex(parts[2]);
                if (endIndex != 0) {
                    if(endIndex == -1) {
                        versionMap.put("patch", Integer.parseInt(parts[2]));
                    } else {
                        versionMap.put("patch", Integer.parseInt(parts[2].substring(0, endIndex)));
                    }
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse version numbers from: " + versionString);
        }
    
        return versionMap;
    }

    public static boolean isVersionEqualOrGreater(String version1, String version2) {
            Map<String, Integer> version1Map = parseVersion(version1);
            Map<String, Integer> version2Map = parseVersion(version2);
        if(version1Map.get("major") > version2Map.get("major")) {
            return true;
        } else if(version1Map.get("major") == version2Map.get("major")) {
            if (version1Map.get("minor") > version2Map.get("minor")) {
                return true;
            } else if(version1Map.get("minor") == version2Map.get("minor")) {
                if(version1Map.get("patch") >= version2Map.get("patch")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Find the index of the first non-digit character in a string
     * 
     * @param input The input string
     * @return The index of the first non-digit character, or -1 if all characters
     *         are digits
     */
    private static int findFirstNonDigitIndex(String input) {
        for (int i = 0; i < input.length(); i++) {
            if (!Character.isDigit(input.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

}
