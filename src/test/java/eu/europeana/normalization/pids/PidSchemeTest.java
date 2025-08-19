package eu.europeana.normalization.pids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;
import org.junit.jupiter.api.Test;

public class PidSchemeTest {

  @Test
  void testHappyFlow() {

    final String schemeId = "#aboutvalue";
    final String schemeTitle = "Title";
    final String schemeSeeAlso = "SeeAlso";
    final String schemeOrganisation = "Organisation";
    final PidScheme pidScheme = new PidScheme(schemeId, Set.of(
        "https?://ark\\.bnf\\.fr/ark:(/?[0-9]+)(/[a-z0-9=~\\*\\+@_$%\\-]+)(/[a-z0-9=~\\*\\+@_$%\\-/\\.]+)?",
        "https?://gallica\\.bnf\\.fr/ark:(/?[0-9]+)(/[a-z0-9=~\\*\\+@_$%\\-]+)(/[a-z0-9=~\\*\\+@_$%\\-/\\.]+)?",
        "https?://catalogue\\.bnf\\.fr/ark:(/?[0-9]+)(/[a-z0-9=~\\*\\+@_$%\\-]+)(/[a-z0-9=~\\*\\+@_$%\\-/\\.]+)?",
        "https?://n2t\\.net/ark:(/?[0-9]+)(/[a-z0-9=~\\*\\+@_$%\\-]+)(/[a-z0-9=~\\*\\+@_$%\\-/\\.]+)?",
        "ark:(/?[0-9]+)(/[a-z0-9=~\\*\\+@_$%\\-]+)(/[a-z0-9=~\\*\\+@_$%\\-/\\.]+)?"),
        "ark:$1$2$3", "https://n2t.net/$0", schemeTitle, schemeSeeAlso, schemeOrganisation);

    final String unNormalizedPid = "https://ark.bnf.fr/ark:/12148/bpt6k279983";

    final PidMatchResult normalisation = pidScheme.match(unNormalizedPid);
    assertNotNull(normalisation);

    assertEquals(unNormalizedPid, normalisation.originalPid());
    assertEquals("ark:/12148/bpt6k279983", normalisation.canonicalPid());
    assertEquals("https://n2t.net/ark:/12148/bpt6k279983", normalisation.resolvablePid());
    assertEquals(schemeId, normalisation.scheme().getSchemeId());
    assertEquals(schemeTitle, normalisation.scheme().getTitle());
    assertEquals(schemeSeeAlso, normalisation.scheme().getSeeAlso());
    assertEquals(schemeOrganisation, normalisation.scheme().getOrganization());
  }
}
