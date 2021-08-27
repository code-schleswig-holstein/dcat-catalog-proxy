package de.landsh.opendata.catalogproxy;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

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
    public void work() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/catalog.xml");
        catalogFilter.work(inputStream);
        inputStream.close();
    }

    @Test
    public void removeUnusedResources_removeAll() {
        final Model model = parseRdf(getClass().getResourceAsStream("/catalog.xml"));

        catalogFilter.removeUnusedDistributions(model, Collections.emptySet());

        Assertions.assertEquals(0, countInstances(model, DCAT.Distribution));
    }

    @Test
    public void rewriteHydraURLs() {
        final Model model = parseRdf(getClass().getResourceAsStream("/hydra.xml"));
        catalogFilter.rewriteHydraURLs(model);
        StringWriter sw = new StringWriter();
        model.write(sw);

        final String result = sw.toString();

        assertFalse(result.contains("http://opendata.schleswig-holstein.de/catalog.xml"));
        assertTrue(result.contains("https://example.org/catalog.xml?page=84"));
    }

    @Test
    public void addDownloadURLs_will_add_accessURLs() {
        final Model model = parseRdf(getClass().getResourceAsStream("/catalog.xml"));

        catalogFilter.addDownloadURLs(model);

        // Every distribution has a downloadURL with the same value as the accessURL.
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution);
        int count = 0;
        while (it.hasNext()) {
            final Resource distribution = it.next();
            count++;
            final Resource accessURL = distribution.getPropertyResourceValue(DCAT.accessURL);
            final Resource downloadURL = distribution.getPropertyResourceValue(DCAT.downloadURL);
            assertNotNull(downloadURL);
            assertEquals(accessURL, downloadURL);
        }

        assertEquals(101, count);
    }

    @Test
    public void addDownloadURLs_will_not_change_the_downloadURL_if_one_is_already_present() {
        final Model model = parseRdf(getClass().getResourceAsStream("/with_downloadURL.xml"));

        catalogFilter.addDownloadURLs(model);

        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution);
        final Resource distribution = it.next();

        assertEquals(1, distribution.listProperties(DCAT.downloadURL).toList().size());

        final Resource accessURL = distribution.getPropertyResourceValue(DCAT.accessURL);
        final Resource downloadURL = distribution.getPropertyResourceValue(DCAT.downloadURL);
        assertNotNull(accessURL);
        assertNotNull(downloadURL);
        assertEquals("http://example.org/file.csv", downloadURL.getURI());
    }

    @Test
    public void rewriteDownloadAndAccessURLs() throws Exception {
        catalogFilter.replaceURL = Arrays.asList("http://example.org/","https://opendata.sh/","https://www.statistik-nord.de/","https://data.sh/");
        catalogFilter.afterPropertiesSet();

        final Model model = parseRdf(getClass().getResourceAsStream("/with_downloadURL.xml"));
        catalogFilter.rewriteDownloadAndAccessURLs(model);

        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution);
        final Resource distribution = it.next();
        final Resource accessURL = distribution.getPropertyResourceValue(DCAT.accessURL);
        final Resource downloadURL = distribution.getPropertyResourceValue(DCAT.downloadURL);
        assertEquals("https://data.sh/fileadmin/Dokumente/Statistische_Berichte/landwirtschaft/C_II_1_m_S/C_II_1_m0409_S.pdf", accessURL.getURI());
        assertEquals("https://opendata.sh/file.csv", downloadURL.getURI());
    }

    /**
     * Collections (dcat:Dataset with dct:type "http://dcat-ap.de/def/datasetTypes/collection" will be included in the
     * filtered results.
     */
    @Test
    public void work_will_preseve_collections() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/with_collection.xml");
        final Model model = catalogFilter.work(inputStream);

        Assertions.assertEquals(8, countInstances(model, DCAT.Dataset));
        Assertions.assertEquals(7, countInstances(model, DCAT.Distribution));

        inputStream.close();
    }

    /**
     * Check that a <code>dct:accessRights http://publications.europa.eu/resource/authority/access-right/PUBLIC</code>
     * statement has been added to each dataset.
     */
    @Test
    public void work_will_add_accessRights() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/with_collection.xml");
        final Model model = catalogFilter.work(inputStream);

        // Every dataset has a dct:accessRights statement
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Dataset);
        int count = 0;
        while (it.hasNext()) {
            final Resource distribution = it.next();
            count++;
            final Resource accessRights = distribution.getPropertyResourceValue(DCTerms.accessRights);
            assertNotNull(accessRights);
            assertEquals("http://publications.europa.eu/resource/authority/access-right/PUBLIC", accessRights.getURI());
        }

        assertEquals(8, count);

        inputStream.close();
    }

    /**
     * Check that a dct:rights statement has been added to each distribution and is equal to the dct:license statement.
     */
    @Test
    public void work_will_add_rights() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/with_collection.xml");
        final Model model = catalogFilter.work(inputStream);

        // Every dataset has a dct:accessRights statement
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution);
        int count = 0;
        while (it.hasNext()) {
            final Resource distribution = it.next();
            count++;
            final Resource rights = distribution.getPropertyResourceValue(DCTerms.rights);
            final Resource license = distribution.getPropertyResourceValue(DCTerms.license);
            assertNotNull(rights);
            assertEquals( license, rights);
        }

        assertEquals(7, count);

        inputStream.close();
    }
}
