package de.landsh.opendata.catalogproxy;

import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.unlimited;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class FilterControllerTest {
    private static ClientAndServer mockServer;
    CatalogFilter catalogFilter = Mockito.mock(CatalogFilter.class);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterController controller = new FilterController(catalogFilter);

    @BeforeAll
    public static void startServer() throws IOException {
        mockServer = startClientAndServer(1080);


    }

    @AfterAll
    public static void stopServer() {
        mockServer.stop();
    }

    @BeforeEach
    public void setUp() {
        Mockito.when(catalogFilter.work(any())).thenReturn(ModelFactory.createDefaultModel());

        controller.remoteURL = "http://localhost:" + mockServer.getPort() + "/";
    }

    @Test
    public void catalog_all_parameters() throws IOException {
        byte[] rawdata = "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:dcat=\"http://www.w3.org/ns/dcat#\"><dcat:Catalog rdf:about=\"https://opendata.schleswig-holstein.de\"></dcat:Catalog></rdf:RDF>".getBytes();

        new MockServerClient("127.0.0.1", mockServer.getPort())
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/catalog.xml")
                                .withQueryStringParameter("page", "5")
                                .withQueryStringParameter("q", "myquery")
                                .withQueryStringParameter("modified_since","2022-02-07")
                                .withQueryStringParameter("fq", "org:zit"),
                        unlimited())
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.OK_200.code())
                                .withHeaders(
                                        new Header("Content-Type", "application/xml"))
                                .withBody(rawdata)
                );

        controller.catalog(5, "myquery", "org:zit", "2022-02-07", response);

        assertEquals("<rdf:RDF\n" +
                "    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                "</rdf:RDF>\n", response.getContentAsString());
    }

    @Test
    public void catalog_all_null() throws IOException {
        byte[] rawdata = "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:dcat=\"http://www.w3.org/ns/dcat#\"><dcat:Catalog rdf:about=\"https://opendata.schleswig-holstein.de\"></dcat:Catalog></rdf:RDF>".getBytes();

        new MockServerClient("127.0.0.1", mockServer.getPort())
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/catalog.xml")
                                .withQueryStringParameter("page", "1"),
                        unlimited())
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.OK_200.code())
                                .withHeaders(
                                        new Header("Content-Type", "application/xml"))
                                .withBody(rawdata)
                );


        controller.catalog(null, null, null, null, response);

        assertEquals("application/rdf+xml;charset=utf-8", response.getContentType());
        assertEquals("<rdf:RDF\n" +
                "    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                "</rdf:RDF>\n", response.getContentAsString());
    }
}
