package de.landsh.opendata.catalogproxy;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FilterInvalidRDF extends InputStream {

    private static final String RDF_ABOUT_DOUBLE_QUOTE = "rdf:about=\"";
    private static final String RDF_ABOUT_SINGLE_QUOTE = "rdf:about='";
    private final BufferedReader reader;
    boolean initialized = false;
    private byte[] currentLine = null;
    private int index = 0;

    public FilterInvalidRDF(InputStream inputStream) {
        reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    /**
     * Replace invalid information, such as IRIs with spaces and unescaped ampersand characters.
     */
    static String filterLine(String line) {
        if (line == null) return null;

        if (line.contains("&")) {
            String[] fragments = line.split("&");
            for (int i = 1; i < fragments.length; i++) {
                String fragment = fragments[i];
                if (!(fragment.startsWith("#") || fragment.startsWith("amp") || fragment.startsWith("apos")
                        || fragment.startsWith("quot") || fragment.startsWith("lt") || fragment.startsWith("gt"))) {
                    // invalid character entity reference
                    fragments[i] = "amp;" + fragment;
                }
            }
            line = StringUtils.join(fragments, "&");
        }

        String before = null;
        String iri = null;
        String after = null;
        if (line.contains(RDF_ABOUT_DOUBLE_QUOTE)) {
            before = StringUtils.substringBefore(line, RDF_ABOUT_DOUBLE_QUOTE) + RDF_ABOUT_DOUBLE_QUOTE;
            iri = StringUtils.substringBetween(line, RDF_ABOUT_DOUBLE_QUOTE, "\"");
            after = "\"" + StringUtils.substringAfter(StringUtils.substringAfter(line, RDF_ABOUT_DOUBLE_QUOTE), "\"");
        } else if (line.contains(RDF_ABOUT_SINGLE_QUOTE)) {
            before = StringUtils.substringBefore(line, RDF_ABOUT_DOUBLE_QUOTE) + RDF_ABOUT_SINGLE_QUOTE;
            iri = StringUtils.substringBetween(line, RDF_ABOUT_DOUBLE_QUOTE, "'");
            after = "'" + StringUtils.substringAfter(StringUtils.substringAfter(line, RDF_ABOUT_DOUBLE_QUOTE), "'");
        }

        if (iri == null) {
            return line;
        } else {
            final String fixedIRI = iri.replaceAll(" ", "%20");
            return before + fixedIRI + after;
        }
    }

    private void readNextLine() throws IOException {
        final String line = reader.readLine();
        if (line == null) {
            currentLine = null;
        } else {
            currentLine = filterLine(line).getBytes(StandardCharsets.UTF_8);
        }

        index = 0;
    }

    @Override
    public int read() throws IOException {
        if (!initialized) {
            initialized = true;
            readNextLine();

        }

        if (currentLine == null) {
            return -1;
        }

        if (index > currentLine.length) {
            readNextLine();
        }

        if (currentLine == null) {
            return -1;
        }
        // insert a newline character at the end of each line
        if (index == currentLine.length) {
            index++;
            return '\n';
        }
        
        byte result = currentLine[index];
        index++;
        return result;
    }
}
