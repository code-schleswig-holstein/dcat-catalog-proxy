package de.landsh.opendata.catalogproxy;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;

@Controller
public class FilterController {
    private static final Logger log = LoggerFactory.getLogger(FilterController.class);

    @Value("${remoteURL:https://opendata.schleswig-holstein.de/}")
    private String remoteURL;

    @Autowired
    private CatalogFilter catalogFilter;

    @RequestMapping(value = "/catalog.xml", produces = "application/rdf+xml")
    public void catalog(@RequestParam(required = false) Integer page, HttpServletResponse response) throws IOException {
        if (page == null)
            page = 1;

        log.debug("catalog.xml?page={}", page);

        final InputStream is = new URL(remoteURL + "catalog.xml?page=" + page).openStream();
        final Model model = catalogFilter.work(is);
        is.close();

        response.setCharacterEncoding("utf-8");
        response.setContentType("application/rdf+xml");

        final Writer writer = response.getWriter();
        model.write(writer);
        writer.close();
    }
}
