package kameker.chattranslator.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModConfigTest {

    @Test
    void blacklistModeRejectsOnlyBlacklistedPlayers() {
        assertFalse(PlayerRules.shouldTranslate(
                "Server", false, List.of(), List.of("Server", "Admin")));
        assertTrue(PlayerRules.shouldTranslate(
                "Player", false, List.of(), List.of("Server", "Admin")));
    }

    @Test
    void whitelistModeAcceptsOnlyWhitelistedPlayers() {
        assertTrue(PlayerRules.shouldTranslate(
                "Friend", true, List.of("Friend"), List.of()));
        assertFalse(PlayerRules.shouldTranslate(
                "Stranger", true, List.of("Friend"), List.of()));
    }

    @Test
    void missingPlayerNameIsNotFiltered() {
        assertTrue(PlayerRules.shouldTranslate(null, true, List.of(), List.of()));
        assertTrue(PlayerRules.shouldTranslate("", true, List.of(), List.of()));
    }

    @Test
    void parsesTrimsDeduplicatesAndSkipsEmptyPlayerNames() {
        assertEquals(List.of("Player1", "Player2"),
                PlayerRules.parseList(" Player1, ,Player2, Player1 "));
        assertEquals(List.of(), PlayerRules.parseList("  "));
        assertEquals(List.of(), PlayerRules.parseList(null));
    }
}
