package eu.europeana.corelib.solr.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import eu.europeana.corelib.solr.utils.QueryParser;

public class QueryParserTest {

	@Test
	public void test() {
		String[] rawQueries = new String[]{
			"la joconde OR la gioconda OR mona lisa",
			"paris",
			"paris OR rome",
			"paris OR la joconde",
		};
		String[] normalizedQueries = new String[]{
			"(la joconde) OR (la gioconda) OR (mona lisa)",
			"paris",
			"paris OR rome",
			"paris OR (la joconde)",
		};
		for (int i = 0; i < rawQueries.length; i++) {
			assertEquals(
				normalizedQueries[i],
				QueryParser.normalizeBooleans(rawQueries[i])
			);
		}
	}

}
