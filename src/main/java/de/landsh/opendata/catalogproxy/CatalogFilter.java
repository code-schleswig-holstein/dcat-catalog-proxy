package de.landsh.opendata.catalogproxy;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;
import java.util.*;

/**
 * Filtert eine DCAT-AP.de konforme catalog.xml Datei nach festgelegten Kriterien.
 */
public class CatalogFilter implements InitializingBean {


    private static final Collection<Resource> UNWANTED_FORMATS = Arrays.asList(
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/PDF"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/DOC"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/DOCX"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/HTML")
    );
    private static final Property LOCN_GEOMETRY = ResourceFactory.createProperty("http://www.w3.org/ns/locn#geometry");
    final private Map<String, String> urlReplacements = new HashMap<>();
    @Value("#{${replaceURL:''}}")
    List<String> replaceURL;
    @Value("${baseURL:http://localhost:8080/}")
    private String baseURL;

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    Model work(InputStream in) {
        final Model model = ModelFactory.createDefaultModel();

        RDFParser.create()
                .source(in)
                .lang(RDFLanguages.RDFXML)
                .errorHandler(ErrorHandlerFactory.errorHandlerStrict)
                .base(baseURL)
                .parse(model);

        final Set<String> usedDistributionIds = new HashSet<>();

        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Dataset);
        while (it.hasNext()) {
            Resource dataset = it.next();
            if (hasAtLeastOneValidDistribution(dataset) || isCollection(dataset)) {
                usedDistributionIds.addAll(getDistributionsForDataset(dataset));
            } else {
                model.remove(dataset.listProperties());
                model.remove(model.listStatements(null, DCAT.dataset, dataset));
            }
        }

        removeUnusedDistributions(model, usedDistributionIds);
        removeAnonymousResources(model);
        removeUnusedLocations(model);
        minimizeLocations(model);
        rewriteHydraURLs(model);
        rewriteDownloadAndAccessURLs(model);
        addDownloadURLs(model);

        return model;
    }

    /**
     * Add downloadURL properties to Distributions. The German DCAT-AP.de treats downloadURL as a not so
     * important optional properties and relies on the accessURL. However, the European data portal values the
     * downloadURL property highly.
     */
    void addDownloadURLs(Model model) {
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution);
        while (it.hasNext()) {
            final Resource distribution = it.next();

            final Resource accessURL = distribution.getPropertyResourceValue(DCAT.accessURL);
            final Resource downloadURL = distribution.getPropertyResourceValue(DCAT.downloadURL);

            if (downloadURL == null) {
                distribution.addProperty(DCAT.downloadURL, accessURL);
            }
        }
    }

    private Resource replaceURIifNecessary(Resource res) {
        if (res == null) return null;
        final String uri = res.getURI();

        for (String s : urlReplacements.keySet()) {
            if (uri.startsWith(s)) {
                return ResourceFactory.createResource(uri.replaceFirst(s, urlReplacements.get(s)));
            }
        }
        return res;
    }

    void rewriteDownloadAndAccessURLs(Model model) {
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution);
        while (it.hasNext()) {
            final Resource distribution = it.next();

            final Resource accessURL = replaceURIifNecessary(distribution.getPropertyResourceValue(DCAT.accessURL));
            final Resource downloadURL = replaceURIifNecessary(distribution.getPropertyResourceValue(DCAT.downloadURL));

            if (accessURL != null) {
                distribution.removeAll(DCAT.accessURL);
                distribution.addProperty(DCAT.accessURL, accessURL);
            }
            if (downloadURL != null) {
                distribution.removeAll(DCAT.downloadURL);
                distribution.addProperty(DCAT.downloadURL, downloadURL);
            }

        }
    }

    void rewriteHydraURLs(Model model) {
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://www.w3.org/ns/hydra/core#PagedCollection"));
        if (it.hasNext()) {
            final Resource pagedCollection = it.nextResource();
            final String originalURL = StringUtils.substringBefore(pagedCollection.getURI(), "catalog.xml");

            final List<Statement> changeStatements = new ArrayList<>();

            final StmtIterator iterator = pagedCollection.listProperties();
            while (iterator.hasNext()) {
                Statement stmt = iterator.next();
                if (stmt.getObject().isLiteral()) {
                    final String value = stmt.getObject().asLiteral().getString();
                    if (value.startsWith(originalURL)) {
                        changeStatements.add(stmt);
                    }
                }
            }

            for (Statement stmt : changeStatements) {
                final String value = stmt.getObject().asLiteral().getString();
                final String newValue = value.replaceFirst(originalURL, baseURL);
                stmt.changeObject(newValue);
            }

            ResourceUtils.renameResource(pagedCollection, pagedCollection.getURI().replaceFirst(originalURL, baseURL));
        }
    }

    /**
     * Entfernt alle dct:Location Instanzen aus dem Model, die nicht als Objekt verwendet werden.
     */
    void removeUnusedLocations(Model model) {
        final Collection<Resource> allObjects = allObjects(model);

        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCTerms.Location);
        while (it.hasNext()) {
            final Resource location = it.next();
            if (!allObjects.contains(location))
                model.remove(location.listProperties());
        }
    }

    /**
     * Enternt aus benannten Locations die Geometrien.
     */
    void minimizeLocations(Model model) {
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCTerms.Location);
        while (it.hasNext()) {
            Resource location = it.next();
            if (StringUtils.startsWith(location.getURI(), "http://dcat-ap.de/def/politicalGeocoding/")) {
                model.remove(location.listProperties(LOCN_GEOMETRY));
            }
        }
    }

    /**
     * Entfernt Resourcen ohne URI aus dem Model. Das sind typischerweise Instanzen von foaf:Organization und
     * dct:PeriodOfTime, die von gelöschten dcat:Datasets übriggeblieben sind.
     */
    void removeAnonymousResources(Model model) {
        final ResIterator it = model.listSubjects();

        final Collection<Resource> allObjects = allObjects(model);

        while (it.hasNext()) {
            Resource resource = it.next();
            if (resource.getURI() == null && !allObjects.contains(resource)) {
                model.remove(resource.listProperties());
            }
        }
    }

    Collection<Resource> allObjects(Model model) {
        final Set<Resource> result = new HashSet<>();
        final NodeIterator it = model.listObjects();
        while (it.hasNext()) {
            RDFNode next = it.next();
            if (next.isResource()) {
                result.add(next.asResource());
            }
        }
        return result;
    }

    /**
     * Entfernt aus dem Model alle dcat:Distribution Instanzen, deren URI nicht in der angegebenen Collection enthalten sind.
     */
    void removeUnusedDistributions(Model model, Collection<String> usedDistributionIds) {
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution);
        while (it.hasNext()) {
            final Resource distribution = it.next();
            if (!usedDistributionIds.contains(distribution.getURI())) {
                model.remove(distribution.listProperties());
            }
        }
    }

    Collection<String> getDistributionsForDataset(Resource dataset) {
        final Set<String> result = new HashSet<>();
        final StmtIterator it = dataset.listProperties(DCAT.distribution);
        while (it.hasNext()) {
            final Statement next = it.next();
            final Resource distribution = next.getObject().asResource();
            result.add(distribution.getURI());
        }

        return result;
    }

    boolean isCollection(Resource dataset) {
        final Resource type = dataset.getPropertyResourceValue(DCTerms.type);
        return type != null && "http://dcat-ap.de/def/datasetTypes/collection".equals(type.getURI());
    }

    boolean hasAtLeastOneValidDistribution(Resource dataset) {
        final StmtIterator it = dataset.listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/dcat#distribution"));
        boolean atLeastOneValidFormat = false;

        while (it.hasNext()) {
            final Statement next = it.next();

            final Resource distribution = next.getObject().asResource();
            final RDFNode format = distribution.getProperty(DCTerms.format).getObject();
            if (!UNWANTED_FORMATS.contains(format)) {
                atLeastOneValidFormat = true;
            }

        }

        return atLeastOneValidFormat;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        // Interpret the (optionally) specified replacement of download URLs.
        if (replaceURL.size() % 2 != 0) {
            throw new BeanInitializationException("replaceURL must be an array of even size, e.g. replaceURL= {'http://10.61.35.179/','https://opendata.schleswig-holstein.de/'}");
        }
        for (int i = 0; i < replaceURL.size(); i += 2) {
            final String source = replaceURL.get(i);
            final String target = replaceURL.get(i + 1);
            urlReplacements.put(source, target);
        }
    }
}
