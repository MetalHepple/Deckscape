package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the Android-version boundary for reliable foreground GPS access. */
public final class LocationPermissionPolicyTest {
    @Test
    public void androidTenRequiresFineLocationForGpsReliability() {
        assertFalse(LocationPermissionPolicy.isSufficient(29, false, true));
        assertTrue(LocationPermissionPolicy.isSufficient(29, true, true));
    }

    @Test
    public void androidTwelveAcceptsUsersApproximateChoice() {
        assertTrue(LocationPermissionPolicy.isSufficient(31, false, true));
    }

    @Test
    public void missingForegroundGrantIsNeverSufficient() {
        assertFalse(LocationPermissionPolicy.isSufficient(36, false, false));
    }
}
