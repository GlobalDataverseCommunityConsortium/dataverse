package edu.harvard.iq.dataverse.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

public class VersionUtilTest {

    @Test
    public void testParseVersion_ValidVersions() {
        // Test case: "6.0"
        Map<String, Integer> version1 = VersionUtil.parseVersion("6.0");
        assertEquals(6, version1.get("major"));
        assertEquals(0, version1.get("minor"));
        assertEquals(0, version1.get("patch"));
        
        // Test case: "6.7.1"
        Map<String, Integer> version2 = VersionUtil.parseVersion("6.7.1");
        assertEquals(6, version2.get("major"));
        assertEquals(7, version2.get("minor"));
        assertEquals(1, version2.get("patch"));
        
        // Test case: "6.7.1-qdr"
        Map<String, Integer> version3 = VersionUtil.parseVersion("6.7.1-qdr");
        assertEquals(6, version3.get("major"));
        assertEquals(7, version3.get("minor"));
        assertEquals(1, version3.get("patch"));
        
        // Test case: "6.8 build 1234"
        Map<String, Integer> version4 = VersionUtil.parseVersion("6.8 build 1234");
        assertEquals(6, version4.get("major"));
        assertEquals(8, version4.get("minor"));
        assertEquals(0, version4.get("patch"));
    }
    
    @Test
    public void testParseVersion_InvalidVersions() {
        // Test null input
        assertThrows(IllegalArgumentException.class, () -> VersionUtil.parseVersion(null));
        
        // Test empty input
        assertThrows(IllegalArgumentException.class, () -> VersionUtil.parseVersion(""));
        
        // Test invalid format (missing minor version)
        assertThrows(IllegalArgumentException.class, () -> VersionUtil.parseVersion("6"));
        
        // Test non-numeric major version
        assertThrows(IllegalArgumentException.class, () -> VersionUtil.parseVersion("a.b"));
    }
    
    @ParameterizedTest
    @CsvSource({
        // version1, version2, expected result
        // Same versions
        "6.0, 6.0, true",
        "6.7.1, 6.7.1, true",
        
        // Major version differences
        "7.0, 6.0, true",
        "6.0, 7.0, false",
        
        // Minor version differences
        "6.8, 6.7, true",
        "6.7, 6.8, false",
        
        // Patch version differences
        "6.7.2, 6.7.1, true",
        "6.7.1, 6.7.2, false",
        
        // Complex versions
        "6.7.1-qdr, 6.7.1, true",
        "6.7.1, 6.7.1-qdr, true",
        "6.8 build 1234, 6.7.1, true",
        "6.7.1, 6.8 build 1234, false",
        
        // Equal with different formats
        "6.7.0, 6.7, true",
        "6.7, 6.7.0, true"
    })
    public void testIsVersionEqualOrGreater(String version1, String version2, boolean expected) {
        assertEquals(expected, VersionUtil.isVersionEqualOrGreater(version1, version2));
    }
    
    @Test
    public void testEdgeCases() {
        // Test with non-numeric characters in version parts
        Map<String, Integer> version = VersionUtil.parseVersion("6.7a.1b");
        assertEquals(6, version.get("major"));
        assertEquals(7, version.get("minor"));
        assertEquals(1, version.get("patch"));
        
        // Test with extra version parts
        Map<String, Integer> versionExtra = VersionUtil.parseVersion("6.7.1.2.3");
        assertEquals(6, versionExtra.get("major"));
        assertEquals(7, versionExtra.get("minor"));
        assertEquals(1, versionExtra.get("patch"));
    }
}