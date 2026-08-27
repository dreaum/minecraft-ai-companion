package adris.altoclef.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialIndexTest {
    @TempDir
    Path temp;

    @Test
    void findsWoodTutorialByEnglishItemId() throws Exception {
        try (var index = new TutorialIndex(new AgentStore(temp))) {
            index.rebuild();
            assertFalse(index.search("oak_log", 5).isEmpty());
        }
    }

    @Test
    void findsWoodTutorialFromChineseNaturalLanguage() throws Exception {
        try (var index = new TutorialIndex(new AgentStore(temp))) {
            index.rebuild();
            var hits = index.search("获取橡木原木", 5);
            assertFalse(hits.isEmpty());
            assertTrue(hits.stream().anyMatch(hit -> hit.id().contains("resources/wood.md")));
        }
    }
}
