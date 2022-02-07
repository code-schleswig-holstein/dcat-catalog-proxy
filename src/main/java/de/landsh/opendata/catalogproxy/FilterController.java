package de.landsh.opendata.catalogproxy;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.net.URLEncoder;

@Controller
public class FilterController {
    private static final Logger log = LoggerFactory.getLogger(FilterController.class);
    private final CatalogFilter catalogFilter;
    @Value("${remoteURL:https://opendata.schleswig-holstein.de/}")
    String remoteURL;

    public FilterController(CatalogFilter filter) {
        this.catalogFilter = filter;
    }

    @RequestMapping(value = "/catalog.xml", produces = "application/rdf+xml")
    public void catalog(@RequestParam(required = false) Integer page,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) String fq,
                        @RequestParam(required = false, name = "modified_since") String modifiedSince,
                        HttpServletResponse response) throws IOException {
        if (page == null)
            page = 1;

        log.debug("catalog.xml?page={}", page);

        final StringBuilder url = new StringBuilder(remoteURL);
        url.append("catalog.xml?page=");
        url.append(page);
        if (StringUtils.isNotBlank(modifiedSince)) {
            url.append("&modified_since=");
            url.append(URLEncoder.encode(modifiedSince, "utf-8"));
        }
        if (StringUtils.isNotBlank(q)) {
            url.append("&q=");
            url.append(URLEncoder.encode(q, "utf-8"));
        }
        if (StringUtils.isNotBlank(fq)) {
            url.append("&fq=");
            url.append(URLEncoder.encode(fq, "utf-8"));
        }

        final InputStream is = new URL(url.toString()).openStream();
        final Model model = catalogFilter.work(is);
        is.close();

        response.setCharacterEncoding("utf-8");
        response.setContentType("application/rdf+xml");

        final Writer writer = response.getWriter();
        model.write(writer);
        writer.close();
    }
}
