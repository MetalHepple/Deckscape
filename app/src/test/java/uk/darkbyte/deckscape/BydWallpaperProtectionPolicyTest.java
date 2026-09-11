package uk.darkbyte.deckscape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BydWallpaperProtectionPolicyTest {
    @Test public void restoreChooserIsLimitedToBydThemes() {
        assertEquals("com.byd.automultipletheme", BydWallpaperProtectionPolicy.THEMES_PACKAGE_NAME);
    }

    @Test
    public void exactBydHardwareIsSupported() {
        assertTrue(BydWallpaperProtectionPolicy.supportsHardware(
                "BYD AUTO", "DiLink3.0", "DiLink3.0"));
    }

    @Test
    public void productMatchAllowsDeviceAlias() {
        assertTrue(BydWallpaperProtectionPolicy.supportsHardware(
                " byd auto ", "unknown", "dilink3.0"));
    }

    @Test
    public void otherAndroidDevicesAreNotSupported() {
        assertFalse(BydWallpaperProtectionPolicy.supportsHardware(
                "Android SDK built for x86", "generic_x86", "sdk"));
        assertFalse(BydWallpaperProtectionPolicy.supportsHardware(
                "BYD AUTO", "unknown", "unknown"));
        assertFalse(BydWallpaperProtectionPolicy.supportsHardware(
                null, "DiLink3.0", "DiLink3.0"));
    }

    @Test
    public void commandsAreFixedToTheBydWallpaperPackage() {
        assertEquals("cmd appops set --user 0 com.byd.wallpaperhome WRITE_WALLPAPER ignore",
                BydWallpaperProtectionPolicy.operationCommand(1));
        assertEquals("cmd appops set --user 0 com.byd.wallpaperhome WRITE_WALLPAPER allow",
                BydWallpaperProtectionPolicy.operationCommand(0));
        assertEquals("pm default-state --user 0 com.byd.wallpaperhome",
                BydWallpaperProtectionPolicy.legacyRestoreCommand(0));
        assertEquals("pm enable --user 0 com.byd.wallpaperhome",
                BydWallpaperProtectionPolicy.legacyRestoreCommand(1));
        assertEquals("cmd appops get --user 0 com.byd.wallpaperhome WRITE_WALLPAPER",
                BydWallpaperProtectionPolicy.VERIFY_COMMAND);
        assertEquals("cmd appops write-settings", BydWallpaperProtectionPolicy.PERSIST_COMMAND);
    }

    @Test
    public void stateRequiresInstalledOwnerAndSuccessfulRead() {
        String line = "  User 0: installed=true hidden=false stopped=true enabled=3 instant=false\n";
        assertEquals(3, BydWallpaperProtectionPolicy.packageState(0, line));
        assertEquals(0, BydWallpaperProtectionPolicy.packageState(0, line.replace("enabled=3", "enabled=0")));
        assertEquals(-1, BydWallpaperProtectionPolicy.packageState(1, line));
        assertEquals(-1, BydWallpaperProtectionPolicy.packageState(0, line.replace("installed=true", "installed=false")));
        assertEquals(-1, BydWallpaperProtectionPolicy.packageState(0, line.replace("User 0:", "User 10:")));
        assertEquals(-1, BydWallpaperProtectionPolicy.packageState(0, line.replace("enabled=3", "enabled=30")));
        assertEquals(-1, BydWallpaperProtectionPolicy.packageState(0, line + line));
        assertEquals(-1, BydWallpaperProtectionPolicy.packageState(0, null));
        assertEquals(-1, BydWallpaperProtectionPolicy.packageState(0, "stopped=true"));
    }

    @Test public void adbIdentityRequiresVerifiedAndroidVersionAndUser() {
        assertTrue(BydWallpaperProtectionPolicy.identityMatches("BYD AUTO\r\nDiLink3.0\r\nDiLink3.0\r\n29\r\n0\r\n"));
        assertFalse(BydWallpaperProtectionPolicy.identityMatches("BYD AUTO\nDiLink3.0\nDiLink3.0\n30\n0"));
        assertFalse(BydWallpaperProtectionPolicy.identityMatches("BYD AUTO\nDiLink3.0\nDiLink3.0\n29\n10"));
        assertFalse(BydWallpaperProtectionPolicy.identityMatches("BYD AUTO\nDiLink3.0\nDiLink3.0\n29"));
        assertFalse(BydWallpaperProtectionPolicy.identityMatches(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void restoreRejectsInvalidState() { BydWallpaperProtectionPolicy.legacyRestoreCommand(3); }

    @Test public void parsesExactPackageOperationAndDefault() {
        assertEquals(0, BydWallpaperProtectionPolicy.operationState(0, "No operations.\r\nDefault mode: allow\r\n"));
        String[] modes = {"allow", "ignore", "deny", "default", "foreground"};
        for (int i = 0; i < modes.length; i++) {
            assertEquals(i, BydWallpaperProtectionPolicy.operationState(0, "WRITE_WALLPAPER: " + modes[i]));
            assertEquals(i, BydWallpaperProtectionPolicy.operationState(0,
                    "WRITE_WALLPAPER: " + modes[i] + "; time=+5s ago"));
            assertEquals("cmd appops set --user 0 com.byd.wallpaperhome WRITE_WALLPAPER " + modes[i],
                    BydWallpaperProtectionPolicy.operationCommand(i));
        }
    }

    @Test public void operationParsingRejectsUidOverridesErrorsAndAmbiguity() {
        String[] invalid = {null, "", "No operations.", "No operations.\nDefault mode: ignore",
                "WRITE_WALLPAPER: ignore\nWRITE_WALLPAPER: allow", "WRITE_WALLPAPER: ignored",
                "Uid mode: WRITE_WALLPAPER: allow\nWRITE_WALLPAPER: ignore",
                "Error: unknown package", "READ_SMS: ignore", "x".repeat(8193)};
        for (String value : invalid) assertEquals(-1, BydWallpaperProtectionPolicy.operationState(0, value));
        assertEquals(-1, BydWallpaperProtectionPolicy.operationState(1, "WRITE_WALLPAPER: ignore"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void operationCommandRejectsInvalidMode() { BydWallpaperProtectionPolicy.operationCommand(-1); }
}
