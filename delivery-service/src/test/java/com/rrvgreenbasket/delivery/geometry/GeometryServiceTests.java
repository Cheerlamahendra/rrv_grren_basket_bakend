package com.rrvgreenbasket.delivery.geometry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.exception.InvalidGeometryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GeometryService Polygon & Spatial Tests")
class GeometryServiceTests {

    private GeometryService geometryService;
    private ObjectMapper objectMapper;

    // Kurnool Zone 1: [78.0300, 15.8200] to [78.0500, 15.8400]
    private static final String VALID_ZONE_1 = """
            {
              "type": "Polygon",
              "coordinates": [
                [
                  [78.0300, 15.8200],
                  [78.0500, 15.8200],
                  [78.0500, 15.8400],
                  [78.0300, 15.8400],
                  [78.0300, 15.8200]
                ]
              ]
            }
            """;

    // Overlapping Zone: [78.0400, 15.8300] to [78.0600, 15.8500] (Overlaps Zone 1)
    private static final String OVERLAPPING_ZONE = """
            {
              "type": "Polygon",
              "coordinates": [
                [
                  [78.0400, 15.8300],
                  [78.0600, 15.8300],
                  [78.0600, 15.8500],
                  [78.0400, 15.8500],
                  [78.0400, 15.8300]
                ]
              ]
            }
            """;

    // Contiguous Non-Overlapping Zone: [78.0500, 15.8200] to [78.0700, 15.8400] (Touches border at lng=78.0500, area=0)
    private static final String CONTIGUOUS_ZONE = """
            {
              "type": "Polygon",
              "coordinates": [
                [
                  [78.0500, 15.8200],
                  [78.0700, 15.8200],
                  [78.0700, 15.8400],
                  [78.0500, 15.8400],
                  [78.0500, 15.8200]
                ]
              ]
            }
            """;

    // Self-intersecting "hourglass" polygon
    private static final String SELF_INTERSECTING_POLYGON = """
            {
              "type": "Polygon",
              "coordinates": [
                [
                  [78.00, 15.00],
                  [78.10, 15.10],
                  [78.10, 15.00],
                  [78.00, 15.10],
                  [78.00, 15.00]
                ]
              ]
            }
            """;

    // Polygon with an interior hole (outer [78.00, 15.00] -> [78.10, 15.10], hole [78.03, 15.03] -> [78.07, 15.07])
    private static final String POLYGON_WITH_HOLE = """
            {
              "type": "Polygon",
              "coordinates": [
                [
                  [78.00, 15.00],
                  [78.10, 15.00],
                  [78.10, 15.10],
                  [78.00, 15.10],
                  [78.00, 15.00]
                ],
                [
                  [78.03, 15.03],
                  [78.07, 15.03],
                  [78.07, 15.07],
                  [78.03, 15.07],
                  [78.03, 15.03]
                ]
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        geometryService = new GeometryService(objectMapper);
    }

    @Test
    @DisplayName("Successfully parses valid GeoJSON polygon")
    void testParseValidPolygon() {
        Polygon polygon = geometryService.parseAndValidatePolygon(VALID_ZONE_1);
        assertNotNull(polygon);
        assertTrue(polygon.isValid());
        assertTrue(polygon.getArea() > 0);
    }

    @Test
    @DisplayName("Correctly identifies point inside polygon")
    void testPointInsidePolygon() {
        Polygon polygon = geometryService.parseAndValidatePolygon(VALID_ZONE_1);
        // Center: lat=15.8300, lng=78.0400
        assertTrue(geometryService.isPointInsidePolygon(15.8300, 78.0400, polygon));
    }

    @Test
    @DisplayName("Correctly identifies point on polygon perimeter")
    void testPointOnBoundary() {
        Polygon polygon = geometryService.parseAndValidatePolygon(VALID_ZONE_1);
        // Border: lat=15.8200, lng=78.0400
        assertTrue(geometryService.isPointInsidePolygon(15.8200, 78.0400, polygon));
    }

    @Test
    @DisplayName("Correctly identifies point outside polygon")
    void testPointOutsidePolygon() {
        Polygon polygon = geometryService.parseAndValidatePolygon(VALID_ZONE_1);
        // Outside: lat=15.8100, lng=78.0400
        assertFalse(geometryService.isPointInsidePolygon(15.8100, 78.0400, polygon));
    }

    @Test
    @DisplayName("Rejects polygon with unclosed linear ring")
    void testUnclosedRingRejected() {
        String unclosed = """
                {
                  "type": "Polygon",
                  "coordinates": [
                    [
                      [78.0300, 15.8200],
                      [78.0500, 15.8200],
                      [78.0500, 15.8400],
                      [78.0300, 15.8400]
                    ]
                  ]
                }
                """;
        assertThrows(InvalidGeometryException.class, () -> geometryService.parseAndValidatePolygon(unclosed));
    }

    @Test
    @DisplayName("Rejects polygon with fewer than 4 coordinates")
    void testFewerThan4PointsRejected() {
        String triangleUnclosed = """
                {
                  "type": "Polygon",
                  "coordinates": [
                    [
                      [78.0300, 15.8200],
                      [78.0500, 15.8200],
                      [78.0300, 15.8200]
                    ]
                  ]
                }
                """;
        assertThrows(InvalidGeometryException.class, () -> geometryService.parseAndValidatePolygon(triangleUnclosed));
    }

    @Test
    @DisplayName("Rejects invalid coordinate ranges (latitude > 90)")
    void testInvalidLatitudeRange() {
        String invalidLat = """
                {
                  "type": "Polygon",
                  "coordinates": [
                    [
                      [78.0300, 95.8200],
                      [78.0500, 95.8200],
                      [78.0500, 95.8400],
                      [78.0300, 95.8400],
                      [78.0300, 95.8200]
                    ]
                  ]
                }
                """;
        assertThrows(InvalidGeometryException.class, () -> geometryService.parseAndValidatePolygon(invalidLat));
    }

    @Test
    @DisplayName("Rejects invalid coordinate ranges (longitude > 180)")
    void testInvalidLongitudeRange() {
        String invalidLng = """
                {
                  "type": "Polygon",
                  "coordinates": [
                    [
                      [195.0300, 15.8200],
                      [195.0500, 15.8200],
                      [195.0500, 15.8400],
                      [195.0300, 15.8400],
                      [195.0300, 15.8200]
                    ]
                  ]
                }
                """;
        assertThrows(InvalidGeometryException.class, () -> geometryService.parseAndValidatePolygon(invalidLng));
    }

    @Test
    @DisplayName("Rejects self-intersecting polygon")
    void testSelfIntersectingPolygon() {
        assertThrows(InvalidGeometryException.class,
                () -> geometryService.parseAndValidatePolygon(SELF_INTERSECTING_POLYGON));
    }

    @Test
    @DisplayName("Rejects malformed JSON syntax")
    void testInvalidGeoJsonSyntax() {
        assertThrows(InvalidGeometryException.class,
                () -> geometryService.parseAndValidatePolygon("{ not a valid json"));
    }

    @Test
    @DisplayName("Polygon containing interior hole excludes point inside hole")
    void testPolygonWithHole_PointExclusion() {
        Polygon polygonWithHole = geometryService.parseAndValidatePolygon(POLYGON_WITH_HOLE);
        assertNotNull(polygonWithHole);
        assertEquals(1, polygonWithHole.getNumInteriorRing());

        // Point in solid polygon area (lat=15.01, lng=78.01) -> INSIDE
        assertTrue(geometryService.isPointInsidePolygon(15.01, 78.01, polygonWithHole));

        // Point inside the hole (lat=15.05, lng=78.05) -> EXCLUDED (NOT inside polygon)
        assertFalse(geometryService.isPointInsidePolygon(15.05, 78.05, polygonWithHole));

        // Point on boundary of outer shell (lat=15.00, lng=78.05) -> INSIDE (covers)
        assertTrue(geometryService.isPointInsidePolygon(15.00, 78.05, polygonWithHole));
    }

    @Test
    @DisplayName("toCanonicalGeoJson preserves all interior rings (holes)")
    void testCanonicalGeoJsonPreservesHoles() throws Exception {
        Polygon polygonWithHole = geometryService.parseAndValidatePolygon(POLYGON_WITH_HOLE);
        String canonicalJson = geometryService.toCanonicalGeoJson(polygonWithHole);

        var jsonNode = objectMapper.readTree(canonicalJson);
        assertEquals("Polygon", jsonNode.get("type").asText());
        // Must contain 2 rings: exterior ring + 1 interior ring
        assertEquals(2, jsonNode.get("coordinates").size());

        // Re-parse the canonical JSON and verify hole is preserved
        Polygon reparsed = geometryService.parseAndValidatePolygon(canonicalJson);
        assertEquals(1, reparsed.getNumInteriorRing());
        assertFalse(geometryService.isPointInsidePolygon(15.05, 78.05, reparsed));
        assertTrue(geometryService.isPointInsidePolygon(15.01, 78.01, reparsed));
    }

    @Test
    @DisplayName("Correctly detects true 2D spatial overlap")
    void testDetectSpatialOverlap() {
        Polygon polyA = geometryService.parseAndValidatePolygon(VALID_ZONE_1);
        Polygon polyB = geometryService.parseAndValidatePolygon(OVERLAPPING_ZONE);

        assertTrue(geometryService.doPolygonsOverlap(polyA, polyB));
    }

    @Test
    @DisplayName("Allows contiguous neighboring zones that merely touch boundary edges")
    void testContiguousZonesDoNotOverlap() {
        Polygon polyA = geometryService.parseAndValidatePolygon(VALID_ZONE_1);
        Polygon polyB = geometryService.parseAndValidatePolygon(CONTIGUOUS_ZONE);

        assertFalse(geometryService.doPolygonsOverlap(polyA, polyB));
    }
}
