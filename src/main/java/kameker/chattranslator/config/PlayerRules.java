package kameker.chattranslator.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PlayerRules {
    private PlayerRules() {
    }

    public static boolean shouldTranslate(String playerName, boolean whitelistMode,
                                          Collection<? extends String> whitelist,
                                          Collection<? extends String> blacklist) {
        if (playerName == null || playerName.isEmpty()) return true;
        return whitelistMode ? whitelist.contains(playerName) : !blacklist.contains(playerName);
    }

    public static List<String> parseList(String input) {
        if (input == null || input.trim().isEmpty()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String name : input.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) result.add(trimmed);
        }
        return result;
    }
}
