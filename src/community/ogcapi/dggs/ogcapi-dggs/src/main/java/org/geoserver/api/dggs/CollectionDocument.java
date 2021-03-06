/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.api.dggs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.api.APIRequestInfo;
import org.geoserver.api.AbstractCollectionDocument;
import org.geoserver.api.CollectionExtents;
import org.geoserver.api.Link;
import org.geoserver.api.features.FeaturesResponse;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.config.ServiceInfo;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geotools.dggs.gstore.DGGSFeatureSource;
import org.geotools.dggs.gstore.DGGSStore;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.FeatureType;
import org.springframework.http.MediaType;

/** Description of a single collection, that will be serialized to JSON/XML/HTML */
@JsonPropertyOrder({"id", "title", "description", "extent", "dggs-id", "resolutions", "links"})
public class CollectionDocument extends AbstractCollectionDocument {
    static final Logger LOGGER = Logging.getLogger(CollectionDocument.class);
    private final DGGSFeatureSource fs;

    FeatureTypeInfo featureType;
    String mapPreviewURL;

    public CollectionDocument(GeoServer geoServer, FeatureTypeInfo featureType) throws IOException {
        super(featureType);
        // basic info
        String collectionId = featureType.prefixedName();
        this.id = collectionId;
        this.title = featureType.getTitle();
        this.description = featureType.getAbstract();
        ReferencedEnvelope bbox = featureType.getLatLonBoundingBox();
        setExtent(new CollectionExtents(bbox));
        this.featureType = featureType;

        String baseUrl = APIRequestInfo.get().getBaseURL();

        // links
        Collection<MediaType> formats =
                APIRequestInfo.get().getProducibleMediaTypes(FeaturesResponse.class, true);
        for (MediaType format : formats) {
            String apiUrl =
                    ResponseUtils.buildURL(
                            baseUrl,
                            "ogc/dggs/collections/" + collectionId + "/zones",
                            Collections.singletonMap("f", format.toString()),
                            URLMangler.URLType.SERVICE);
            addLink(
                    new Link(
                            apiUrl,
                            "zones",
                            format.toString(),
                            collectionId + " items as " + format.toString(),
                            "zones"));
        }

        addSelfLinks("ogc/dggs/collections/" + id);

        // map preview if available
        if (isWMSAvailable(geoServer)) {
            Map<String, String> kvp = new HashMap<>();
            kvp.put("LAYERS", featureType.prefixedName());
            kvp.put("FORMAT", "application/openlayers");
            this.mapPreviewURL =
                    ResponseUtils.buildURL(baseUrl, "wms/reflect", kvp, URLMangler.URLType.SERVICE);
        }

        // setup resolutions
        DGGSStore dggsStore = (DGGSStore) featureType.getStore().getDataStore(null);
        this.fs = dggsStore.getDGGSFeatureSource(featureType.getNativeName());
    }

    private boolean isWMSAvailable(GeoServer geoServer) {
        ServiceInfo si =
                geoServer
                        .getServices()
                        .stream()
                        .filter(s -> "WMS".equals(s.getId()))
                        .findFirst()
                        .orElse(null);
        return si != null;
    }

    @JsonIgnore
    public FeatureType getSchema() {
        try {
            return featureType.getFeatureType();
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Failed to compute feature type", e);
            return null;
        }
    }

    @JsonIgnore
    public String getMapPreviewURL() {
        return mapPreviewURL;
    }

    public int[] getResolutions() {
        return fs.getDGGS().getResolutions();
    }

    @JsonProperty("dggs-id")
    public String getDggsId() {
        return fs.getDGGS().getIdentifier();
    }
}
