package de.landsh.opendata.catalogproxy;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;

import java.io.InputStream;
import java.util.*;

/**
 * Filtert eine DCAT-AP.de konforme catalog.xml Datei nach festgelegten Kriterien.
 */
public class CatalogFilter {


    private static final Collection<Resource> UNWANTED_FORMATS = Arrays.asList(
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/PDF"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/DOC"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/DOCX"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/HTML")
    );
    private static final Property LOCN_GEOMETRY = ResourceFactory.createProperty("http://www.w3.org/ns/locn#geometry");
    private final String baseURL;

    public CatalogFilter(String baseURL) {
        this.baseURL = baseURL;
    }

    Model work(InputStream in) {
        Model model = ModelFactory.createDefaultModel();

        RDFParser.create()
                .source(in)
                .lang(RDFLanguages.RDFXML)
                .errorHandler(ErrorHandlerFactory.errorHandlerStrict)
                .base(baseURL)
                .parse(model);

        Set<String> usedDistributionIds = new HashSet<>();

        final ResIterator it = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://www.w3.org/ns/dcat#Dataset"));
        while (it.hasNext()) {
            Resource dataset = it.next();
            if (hasAtLeastOneValidDistribution(dataset)) {
                usedDistributionIds.addAll(getDistributionsForDataset(dataset));
            } else {
                model.remove(dataset.listProperties());
                model.remove(model.listStatements(null, ResourceFactory.createProperty("http://www.w3.org/ns/dcat#dataset"), dataset));
            }
        }

        removeUnusedDistributions(model, usedDistributionIds);
        removeAnonymousResources(model);
        removeUnusedLocations(model);
        minimizeLocations(model);
        rewriteHydraURLs(model);

        return model;
    }

    void rewriteHydraURLs(Model model) {
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://www.w3.org/ns/hydra/core#PagedCollection"));
        if (it.hasNext()) {
            final Resource pagedCollection = it.nextResource();
            final String originalURL = StringUtils.substringBefore(pagedCollection.getURI(), "catalog.xml");

            List<Statement> changeStatements = new ArrayList<>();

            StmtIterator iterator = pagedCollection.listProperties();
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

        Collection<Resource> allObjects = allObjects(model);

        while (it.hasNext()) {
            Resource resource = it.next();
            if (resource.getURI() == null && !allObjects.contains(resource)) {
                model.remove(resource.listProperties());
            }
        }
    }

    Collection<Resource> allObjects(Model model) {
        Set<Resource> result = new HashSet<>();
        NodeIterator it = model.listObjects();
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
        final ResIterator it = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://www.w3.org/ns/dcat#Distribution"));
        while (it.hasNext()) {
            Resource distribution = it.next();
            if (!usedDistributionIds.contains(distribution.getURI())) {
                model.remove(distribution.listProperties());
            }
        }
    }

    Collection<String> getDistributionsForDataset(Resource dataset) {
        Set<String> result = new HashSet<>();
        StmtIterator it = dataset.listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/dcat#distribution"));
        while (it.hasNext()) {
            Statement next = it.next();

            Resource distribution = next.getObject().asResource();

            result.add(distribution.getURI());
        }

        return result;
    }

    boolean hasAtLeastOneValidDistribution(Resource dataset) {
        StmtIterator it = dataset.listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/dcat#distribution"));
        boolean atLeastOneValidFormat = false;

        while (it.hasNext()) {
            Statement next = it.next();

            Resource distribution = next.getObject().asResource();
            RDFNode format = distribution.getProperty(DCTerms.format).getObject();
            if (!UNWANTED_FORMATS.contains(format)) {
                atLeastOneValidFormat = true;
            }

        }

        return atLeastOneValidFormat;
    }

}
