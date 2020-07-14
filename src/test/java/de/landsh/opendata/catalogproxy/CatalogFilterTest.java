package de.landsh.opendata.catalogproxy;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

public class CatalogFilterTest {

    @Test
    public void test() throws Exception{

        InputStream is = getClass().getResourceAsStream("/catalog.xml");

        new CatalogFilter().work(is);
    }
}
