package uk.darkbyte.deckscape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BydWallpaperProtectionPolicyTest {
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
        assertEquals("am force-stop --user 0 com.byd.wallpaperhome",
                BydWallpaperProtectionPolicy.FORCE_STOP_COMMAND);
        assertEquals("if pidof com.byd.wallpaperhome >/dev/null; then "
                        + "cmd activity force-stop --user 0 com.byd.wallpaperhome; fi",
                BydWallpaperProtectionPolicy.STOP_IF_RUNNING_COMMAND);
        assertEquals("dumpsys package com.byd.wallpaperhome | grep 'User 0:'",
                BydWallpaperProtectionPolicy.VERIFY_STOPPED_COMMAND);
    }

    @Test
    public void successRequiresBothCommandsAndStoppedState() {
        assertTrue(BydWallpaperProtectionPolicy.repairSucceeded(
                0, 0, "User 0: installed=true stopped=true enabled=0"));
        assertFalse(BydWallpaperProtectionPolicy.repairSucceeded(
                1, 0, "User 0: installed=true stopped=true enabled=0"));
        assertFalse(BydWallpaperProtectionPolicy.repairSucceeded(
                0, 1, "User 0: installed=true stopped=true enabled=0"));
        assertFalse(BydWallpaperProtectionPolicy.repairSucceeded(
                0, 0, "User 0: installed=true stopped=false enabled=0"));
        assertFalse(BydWallpaperProtectionPolicy.repairSucceeded(0, 0, null));
    }
}
