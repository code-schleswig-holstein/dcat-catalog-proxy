package de.landsh.opendata.catalogproxy;

import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collections;

public class CatalogFilterTest {

    private final CatalogFilter catalogFilter = new CatalogFilter();

    @BeforeEach
    public void setUp() {
        catalogFilter.setBaseURL("https://example.org/");
    }


    private Model parseRdf(InputStream inputStream) {
        Model model = ModelFactory.createDefaultModel();

        RDFParser.create()
                .source(inputStream)
                .lang(RDFLanguages.RDFXML)
                .errorHandler(ErrorHandlerFactory.errorHandlerStrict)
                .base("http://example/base")
                .parse(model);

        return model;
    }

    private int countInstances(Model model, Resource type) {
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, type);
        int count = 0;
        while (it.hasNext()) {
            it.nextResource();
            count++;
        }
        return count;
    }

    @Test
    public void test() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/catalog.xml");
        catalogFilter.work(inputStream);
        inputStream.close();
    }

    @Test
    public void removeUnusedResources_removeAll() {
        Model model = parseRdf(getClass().getResourceAsStream("/catalog.xml"));

        catalogFilter.removeUnusedDistributions(model, Collections.emptySet());

        Assertions.assertEquals(0, countInstances(model, ResourceFactory.createResource("http://www.w3.org/ns/dcat#Distribution")));
    }

    @Test
    public void rewriteHydraURLs() {
        Model model = parseRdf(getClass().getResourceAsStream("/hydra.xml"));
        catalogFilter.rewriteHydraURLs(model);
        StringWriter sw = new StringWriter();
        model.write(sw);

        final String result = sw.toString();

        Assertions.assertFalse(result.contains("http://opendata.schleswig-holstein.de/catalog.xml"));
        Assertions.assertTrue(result.contains("https://example.org/catalog.xml?page=84"));
    }
}
