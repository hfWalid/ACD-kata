package fr.walid.acd.contribution.adapters.importing;

import fr.walid.acd.contribution.domain.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Package level fixture helpers shared by the adapter tests. */
final class ImportFixtures {
    private ImportFixtures() {
    }

    static byte[] specBytes() {
        return """
                [
                  {
                    "operation": "shift",
                    "spec": {
                      "reference": "sourceRef",
                      "titre": "title",
                      "gabarit": "templateRef",
                      "sections": { "*": { "titre": "sections[&1].title", "contenu": "sections[&1].content" } }
                    }
                  }
                ]
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    static List<Map<String, Object>> asMaps(List<Section> sections) {
        var maps = new ArrayList<Map<String, Object>>();
        sections.forEach(section -> maps.add(Map.of("title", section.title(), "content", section.content())));
        return maps;
    }
}
