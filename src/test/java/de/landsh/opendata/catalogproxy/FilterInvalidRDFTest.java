package de.landsh.opendata.catalogproxy;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FilterInvalidRDFTest {
    @Test
    public void read_unmodified() throws IOException {
        String expectedResult = IOUtils.toString(getClass().getResourceAsStream("/catalog.xml"), StandardCharsets.UTF_8);

        InputStream inputStream = new FilterInvalidRDF(getClass().getResourceAsStream("/catalog.xml"));
        String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

        assertEquals(expectedResult, result);
    }

    @Test
    public void read_iri_with_space() throws IOException {
        final String invalidIRI = "\"https://opendata.schleswig-holstein.de/dataset/automatische-zahlstelle-neustadt i. h.-süd-2012\"";
        final String correctedIRI = "\"https://opendata.schleswig-holstein.de/dataset/automatische-zahlstelle-neustadt%20i.%20h.-süd-2012\"";

        final String invalidURL = "https://www.bast.de/DE/Verkehrstechnik/Fachthemen/v2-verkehrszaehlung/Aktuell/zaehl_aktuell_node.html?nn=1819516&cms_detail=1105&cms_map=0";
        final String correctedURL = "https://www.bast.de/DE/Verkehrstechnik/Fachthemen/v2-verkehrszaehlung/Aktuell/zaehl_aktuell_node.html?nn=1819516&amp;cms_detail=1105&amp;cms_map=0";

        String expectedResult = IOUtils.toString(getClass().getResourceAsStream("/invalid_iri.xml"), StandardCharsets.UTF_8)
                .replace(invalidIRI,correctedIRI)
                .replace(invalidURL, correctedURL);

        InputStream inputStream = new FilterInvalidRDF(getClass().getResourceAsStream("/invalid_iri.xml"));
        String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

        assertEquals(expectedResult, result);
    }

    @Test
    public void filterLine_unmodified() {
        // edge cases
        assertEquals("", FilterInvalidRDF.filterLine(""));
        assertNull(FilterInvalidRDF.filterLine(null));

        // unmodified
        assertEquals("abc&quot;def", FilterInvalidRDF.filterLine("abc&quot;def"));
        assertEquals("&quot;", FilterInvalidRDF.filterLine("&quot;"));
        assertEquals(" &quot; ", FilterInvalidRDF.filterLine(" &quot; "));
        assertEquals(" &amp; ", FilterInvalidRDF.filterLine(" &amp; "));
        assertEquals(" &#20; ", FilterInvalidRDF.filterLine(" &#20; "));
        assertEquals(" &apos; ", FilterInvalidRDF.filterLine(" &apos; "));
        assertEquals(" &lt; ", FilterInvalidRDF.filterLine(" &lt; "));
        assertEquals(" &gt; ", FilterInvalidRDF.filterLine(" &gt; "));
    }

    @Test
    public void filterLine_invalid_xml_entity() {
        assertEquals("?nn=1819516&amp;cms_detail=1105&amp;cms_map=0", FilterInvalidRDF.filterLine("?nn=1819516&cms_detail=1105&cms_map=0"));

    }
}
