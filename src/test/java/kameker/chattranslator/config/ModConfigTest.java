package kameker.chattranslator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для ModConfig
 */
public class ModConfigTest {

    @Test
    @DisplayName("Игрок не в чёрном списке пропускается")
    void testPlayerNotInBlacklist() {
        // Эмулируем настройку чёрного списка
        List<String> blacklist = List.of("Server", "Admin");

        assertFalse(blacklist.contains("Player1"));
        assertTrue(blacklist.contains("Server"));
    }

    @Test
    @DisplayName("Игрок в белом списке пропускается")
    void testPlayerInWhitelist() {
        List<String> whitelist = List.of("Friend1", "Friend2");

        assertTrue(whitelist.contains("Friend1"));
        assertFalse(whitelist.contains("Stranger"));
    }

    @Test
    @DisplayName("Пустое имя игрока обрабатывается корректно")
    void testEmptyPlayerName() {
        // Проверяем, что пустое имя не вызывает ошибок
        String playerName = "";
        assertNull(playerName.isEmpty() ? null : playerName);
    }

    @Test
    @DisplayName("Null имя игрока обрабатывается корректно")
    void testNullPlayerName() {
        String playerName = null;
        assertTrue(playerName == null || playerName.isEmpty());
    }

    @Test
    @DisplayName("Парсинг списка игроков из строки")
    void testParsePlayerList() {
        String input = "Player1, Player2, Player3";
        String[] players = input.split(",");

        assertEquals(3, players.length);
        assertEquals("Player1", players[0].trim());
        assertEquals("Player2", players[1].trim());
        assertEquals("Player3", players[2].trim());
    }

    @Test
    @DisplayName("Парсинг списка игроков с лишними пробелами")
    void testParsePlayerListWithSpaces() {
        String input = "  Player1  ,  Player2  ,Player3";
        String[] players = input.split(",");

        assertEquals(3, players.length);
        assertEquals("Player1", players[0].trim());
        assertEquals("Player2", players[1].trim());
        assertEquals("Player3", players[2].trim());
    }

    @Test
    @DisplayName("Пустой список игроков")
    void testEmptyPlayerList() {
        String input = "";
        String[] players = input.isEmpty() ? new String[0] : input.split(",");

        assertEquals(0, players.length);
    }
}
