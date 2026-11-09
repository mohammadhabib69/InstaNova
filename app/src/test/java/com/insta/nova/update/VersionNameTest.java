package com.insta.nova.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Fallback comparison used only when a manifest omits {@code versionCode}. */
public class VersionNameTest {

    @Test
    public void comparesDottedNumbers() {
        assertTrue(VersionName.compare("1.1.0", "1.0.9") > 0);
        assertTrue(VersionName.compare("1.0.9", "1.1.0") < 0);
        assertEquals(0, VersionName.compare("1.1.0", "1.1.0"));
    }

    @Test
    public void padsMissingSegments() {
        assertEquals(0, VersionName.compare("2.0", "2.0.0"));
        assertTrue(VersionName.compare("2.0.1", "2.0") > 0);
        assertTrue(VersionName.compare("2.1", "2.0.9") > 0);
    }

    @Test
    public void ignoresTagPrefixAndSuffix() {
        assertEquals(0, VersionName.compare("v1.2.3", "1.2.3"));
        assertEquals(0, VersionName.compare("1.2.3-beta.2", "1.2.3"));
        assertTrue(VersionName.compare("1.2.4", "1.2.3-beta.9") > 0);
    }

    @Test
    public void handlesNullAndBlank() {
        assertEquals(0, VersionName.compare(null, null));
        assertTrue(VersionName.compare("1.0.0", null) > 0);
        assertTrue(VersionName.compare(null, "1.0.0") < 0);
    }

    @Test
    public void updateInfoPrefersVersionCode() {
        UpdateInfo byCode = new UpdateInfo(5, "9.9.9", "https://example.com/a.apk",
                "", 0L, "", java.util.Collections.emptyList());
        // Name says newer, code says older: the code decides, exactly like Android does.
        assertTrue(byCode.isNewerThan(4, "1.0.0"));
        assertTrue(!byCode.isNewerThan(6, "1.0.0"));

        UpdateInfo noCode = new UpdateInfo(0, "1.0.5", "https://example.com/a.apk",
                "", 0L, "", java.util.Collections.emptyList());
        assertTrue(noCode.isNewerThan(0, "1.0.4"));
        assertTrue(!noCode.isNewerThan(0, "1.0.5"));
    }
}
