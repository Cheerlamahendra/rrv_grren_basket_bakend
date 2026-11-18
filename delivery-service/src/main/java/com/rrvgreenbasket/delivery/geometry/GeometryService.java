package com.rrvgreenbasket.delivery.geometry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.exception.InvalidGeometryException;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GeometryService {

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Geometry> geometryCache = new ConcurrentHashMap<>(128);

    public GeometryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses and validates a GeoJSON Polygon or MultiPolygon representation.
     *
     * @param geoJson the GeoJSON string
     * @return a validated JTS Geometry (Polygon or MultiPolygon)
     */
    public Geometry parseAndValidateGeometry(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) {
            throw new InvalidGeometryException("GeoJSON string cannot be null or empty");
        }

        try {
            JsonNode root = objectMapper.readTree(geoJson);
            return parseAndValidateJsonNodeToGeometry(root);
        } catch (JsonProcessingException e) {
            throw new InvalidGeometryException("Malformed GeoJSON format: " + e.getMessage());
        }
    }

    /**
     * Parses and validates a GeoJSON representation into a JTS Polygon.
     * Backwards-compatible signature.
     *
     * @param geoJson GeoJSON string
     * @return a validated JTS Polygon
     */
    public Polygon parseAndValidatePolygon(String geoJson) {
        Geometry geom = parseAndValidateGeometry(geoJson);
        if (geom instanceof Polygon polygon) {
            return polygon;
        } else if (geom instanceof MultiPolygon mp && mp.getNumGeometries() == 1) {
            return (Polygon) mp.getGeometryN(0);
        }
        throw new InvalidGeometryException("Expected single Polygon geometry but found " + geom.getGeometryType());
    }

    /**
     * Parses from a Jackson JsonNode directly into a Polygon.
     */
    public Polygon parseAndValidateJsonNode(JsonNode root) {
        Geometry geom = parseAndValidateJsonNodeToGeometry(root);
        if (geom instanceof Polygon polygon) {
            return polygon;
        } else if (geom instanceof MultiPolygon mp && mp.getNumGeometries() == 1) {
            return (Polygon) mp.getGeometryN(0);
        }
        throw new InvalidGeometryException("Expected single Polygon geometry but found " + geom.getGeometryType());
    }

    /**
     * Parses from a Jackson JsonNode directly into either a Polygon or MultiPolygon.
     */
    public Geometry parseAndValidateJsonNodeToGeometry(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new InvalidGeometryException("GeoJSON must be a valid JSON object");
        }

        JsonNode typeNode = root.get("type");
        if (typeNode == null || typeNode.isNull()) {
            throw new InvalidGeometryException("GeoJSON 'type' field is required");
        }

        String type = typeNode.asText();
        JsonNode coordinatesNode = root.get("coordinates");
        if (coordinatesNode == null || !coordinatesNode.isArray() || coordinatesNode.isEmpty()) {
            throw new InvalidGeometryException("GeoJSON coordinates array is required and must not be empty");
        }

        if ("Polygon".equalsIgnoreCase(type)) {
            return parsePolygonCoordinates(coordinatesNode);
        } else if ("MultiPolygon".equalsIgnoreCase(type)) {
            return parseMultiPolygonCoordinates(coordinatesNode);
        } else {
            throw new InvalidGeometryException("GeoJSON type must be 'Polygon' or 'MultiPolygon', received: " + type);
        }
    }

    private Polygon parsePolygonCoordinates(JsonNode coordinatesNode) {
        // Parse outer boundary ring
        JsonNode outerRingNode = coordinatesNode.get(0);
        if (outerRingNode == null || !outerRingNode.isArray() || outerRingNode.size() < 4) {
            throw new InvalidGeometryException("Polygon outer ring must contain at least 4 coordinates (3 vertices + closed endpoint)");
        }

        Coordinate[] outerRingCoordinates = parseRingCoordinates(outerRingNode, "outer ring");
        LinearRing outerRing;
        try {
            outerRing = geometryFactory.createLinearRing(outerRingCoordinates);
        } catch (IllegalArgumentException e) {
            throw new InvalidGeometryException("Invalid outer ring: " + e.getMessage());
        }

        // Parse interior hole rings if any
        LinearRing[] holes = null;
        if (coordinatesNode.size() > 1) {
            List<LinearRing> holeList = new ArrayList<>();
            for (int i = 1; i < coordinatesNode.size(); i++) {
                JsonNode holeNode = coordinatesNode.get(i);
                if (holeNode == null || !holeNode.isArray() || holeNode.size() < 4) {
                    throw new InvalidGeometryException("Interior ring (hole " + i + ") must contain at least 4 coordinates (3 vertices + closed endpoint)");
                }
                Coordinate[] holeCoordinates = parseRingCoordinates(holeNode, "interior ring " + i);
                try {
                    holeList.add(geometryFactory.createLinearRing(holeCoordinates));
                } catch (IllegalArgumentException e) {
                    throw new InvalidGeometryException("Invalid interior ring (hole " + i + "): " + e.getMessage());
                }
            }
            holes = holeList.toArray(new LinearRing[0]);
        }

        Polygon polygon = geometryFactory.createPolygon(outerRing, holes);
        validateGeometryTopology(polygon);
        return polygon;
    }

    private MultiPolygon parseMultiPolygonCoordinates(JsonNode coordinatesNode) {
        List<Polygon> polygons = new ArrayList<>();
        for (int i = 0; i < coordinatesNode.size(); i++) {
            JsonNode polyNode = coordinatesNode.get(i);
            if (polyNode == null || !polyNode.isArray() || polyNode.isEmpty()) {
                throw new InvalidGeometryException("MultiPolygon member at index " + i + " must be a valid coordinates ring array");
            }
            polygons.add(parsePolygonCoordinates(polyNode));
        }

        MultiPolygon multiPolygon = geometryFactory.createMultiPolygon(polygons.toArray(new Polygon[0]));
        validateGeometryTopology(multiPolygon);
        return multiPolygon;
    }

    private void validateGeometryTopology(Geometry geometry) {
        IsValidOp validOp = new IsValidOp(geometry);
        if (!validOp.isValid()) {
            TopologyValidationError err = validOp.getValidationError();
            String details = err != null ? err.getMessage() + " at coordinate " + err.getCoordinate() : "self-intersecting or degenerate";
            throw new InvalidGeometryException("Polygon boundary is geometrically invalid (" + details + ")");
        }

        if (geometry.getArea() <= 0) {
            throw new InvalidGeometryException("Polygon must enclose an area greater than zero");
        }
    }

    private Coordinate[] parseRingCoordinates(JsonNode ringNode, String ringDescription) {
        int size = ringNode.size();
        Coordinate[] coordinates = new Coordinate[size];

        for (int i = 0; i < size; i++) {
            JsonNode pointNode = ringNode.get(i);
            if (pointNode == null || !pointNode.isArray() || pointNode.size() < 2) {
                throw new InvalidGeometryException("Each coordinate in " + ringDescription + " must be an array of [longitude, latitude]");
            }

            JsonNode lonNode = pointNode.get(0);
            JsonNode latNode = pointNode.get(1);

            if (lonNode == null || !lonNode.isNumber() || latNode == null || !latNode.isNumber()) {
                throw new InvalidGeometryException("Coordinates in " + ringDescription + " must be valid numeric values");
            }

            double longitude = lonNode.asDouble();
            double latitude = latNode.asDouble();

            validateCoordinateRange(latitude, longitude);
            // JTS uses (x, y) = (longitude, latitude)
            coordinates[i] = new Coordinate(longitude, latitude);
        }

        // Verify ring closure
        Coordinate first = coordinates[0];
        Coordinate last = coordinates[size - 1];
        if (!first.equals2D(last)) {
            throw new InvalidGeometryException("Polygon " + ringDescription + " is not closed: first coordinate " + first + " does not match last coordinate " + last);
        }

        return coordinates;
    }

    public void validateCoordinateRange(double latitude, double longitude) {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new InvalidGeometryException("Invalid latitude: " + latitude + ". Must be between -90.0 and 90.0");
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new InvalidGeometryException("Invalid longitude: " + longitude + ". Must be between -180.0 and 180.0");
        }
    }

    /**
     * Checks if given latitude and longitude falls within the polygon or multipolygon boundary.
     * Uses covers() to include points that lie directly on the polygon perimeter.
     */
    public boolean isPointInsideGeometry(double latitude, double longitude, Geometry geometry) {
        validateCoordinateRange(latitude, longitude);
        Coordinate coord = new Coordinate(longitude, latitude);
        Point point = geometryFactory.createPoint(coord);
        return geometry.covers(point);
    }

    /**
     * Backwards compatible method for Polygon point checking.
     */
    public boolean isPointInsidePolygon(double latitude, double longitude, Polygon polygon) {
        return isPointInsideGeometry(latitude, longitude, polygon);
    }

    /**
     * Checks if two geometries share an overlapping area in 2D space.
     * Adjacent zones that only share a boundary edge or single vertex are NOT considered overlapping.
     */
    public boolean doGeometriesOverlap(Geometry geomA, Geometry geomB) {
        if (geomA == null || geomB == null) {
            return false;
        }

        if (!geomA.intersects(geomB)) {
            return false;
        }

        Geometry intersection = geomA.intersection(geomB);
        // Overlap exists if intersection is a 2-dimensional area
        return intersection.getDimension() == 2 && intersection.getArea() > 1e-10;
    }

    public boolean doPolygonsOverlap(Polygon polyA, Polygon polyB) {
        return doGeometriesOverlap(polyA, polyB);
    }

    /**
     * Serializes a validated polygon back to a normalized canonical GeoJSON string,
     * fully preserving exterior ring and all interior rings (holes).
     */
    public String toCanonicalGeoJson(Polygon polygon) {
        return toCanonicalGeoJson((Geometry) polygon);
    }

    /**
     * Serializes a validated geometry (Polygon or MultiPolygon) back to canonical GeoJSON.
     */
    public String toCanonicalGeoJson(Geometry geometry) {
        if (geometry == null) {
            throw new InvalidGeometryException("Geometry cannot be null for GeoJSON serialization");
        }

        try {
            if (geometry instanceof Polygon polygon) {
                List<List<List<Double>>> ringsList = new ArrayList<>();
                // 1. Exterior ring
                ringsList.add(ringCoordinatesToList(polygon.getExteriorRing().getCoordinates()));

                // 2. Interior rings (holes) - PRESERVES INTERIOR RINGS
                for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                    ringsList.add(ringCoordinatesToList(polygon.getInteriorRingN(i).getCoordinates()));
                }

                var map = java.util.Map.of(
                        "type", "Polygon",
                        "coordinates", ringsList
                );
                return objectMapper.writeValueAsString(map);

            } else if (geometry instanceof MultiPolygon multiPolygon) {
                List<List<List<List<Double>>>> polyList = new ArrayList<>();
                for (int p = 0; p < multiPolygon.getNumGeometries(); p++) {
                    Polygon poly = (Polygon) multiPolygon.getGeometryN(p);
                    List<List<List<Double>>> ringsList = new ArrayList<>();
                    ringsList.add(ringCoordinatesToList(poly.getExteriorRing().getCoordinates()));
                    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
                        ringsList.add(ringCoordinatesToList(poly.getInteriorRingN(i).getCoordinates()));
                    }
                    polyList.add(ringsList);
                }

                var map = java.util.Map.of(
                        "type", "MultiPolygon",
                        "coordinates", polyList
                );
                return objectMapper.writeValueAsString(map);

            } else {
                throw new InvalidGeometryException("Unsupported geometry type for GeoJSON export: " + geometry.getGeometryType());
            }
        } catch (Exception e) {
            throw new InvalidGeometryException("Failed to serialize geometry to GeoJSON: " + e.getMessage());
        }
    }

    private List<List<Double>> ringCoordinatesToList(Coordinate[] coordinates) {
        List<List<Double>> list = new ArrayList<>(coordinates.length);
        for (Coordinate c : coordinates) {
            list.add(List.of(c.getX(), c.getY()));
        }
        return list;
    }

    /**
     * Retrieves parsed geometry from memory cache or parses and caches it.
     */
    public Geometry getOrParseGeometry(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) {
            throw new InvalidGeometryException("GeoJSON string cannot be null or empty");
        }
        return geometryCache.computeIfAbsent(geoJson, this::parseAndValidateGeometry);
    }

    /**
     * Invalidates the geometry cache.
     */
    public void invalidateCache() {
        geometryCache.clear();
        log.debug("Geometry cache invalidated");
    }
}
