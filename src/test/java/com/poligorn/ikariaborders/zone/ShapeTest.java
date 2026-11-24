package com.poligorn.ikariaborders.zone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeTest {
    @Test void circleStrict() {
        Shape c = new Shape.Circle(0, 0, 100);
        assertTrue(c.contains(0, 0));
        assertTrue(c.contains(99, 0));
        assertFalse(c.contains(100, 0), "boundary is outside");
        assertFalse(c.contains(80, 80)); // dist 113
        assertTrue(c.contains(70, 70));  // dist 99
    }

    @Test void squareChebyshevBoundaryOutside() {
        Shape s = new Shape.Square(0, 0, 100);
        assertTrue(s.contains(99.99, -99.99));
        assertFalse(s.contains(100, 0));
        assertFalse(s.contains(0, -100));
        assertTrue(s.contains(99, 99), "corner of square is inside (unlike a circle)");
        assertFalse(s.contains(100, 100));
        assertTrue(new Shape.Square(0, 0, 1500).contains(1400, 1400));
        assertFalse(new Shape.Circle(0, 0, 1500).contains(1400, 1400));
    }

    @Test void squareWithOffsetCenter() {
        Shape s = new Shape.Square(500, -500, 10);
        assertTrue(s.contains(505, -505));
        assertFalse(s.contains(510, -500));
    }

    @Test void rectInclusiveByBlock() {
        Shape r = Shape.Rect.of(0, 0, 10, 5);
        assertTrue(r.contains(0, 0));
        assertTrue(r.contains(10, 5));
        assertTrue(r.contains(10.99, 5.99), "block 10,5 spans up to <11,<6");
        assertFalse(r.contains(11, 5));
        assertFalse(r.contains(-0.1, 0));
        // swapped corners
        assertTrue(Shape.Rect.of(10, 5, 0, 0).contains(5, 3));
    }

    // L-shaped concave polygon: (0,0) (10,0) (10,4) (4,4) (4,10) (0,10)
    private static Shape.Polygon concave() {
        return new Shape.Polygon(new double[]{0, 10, 10, 4, 4, 0}, new double[]{0, 0, 4, 4, 10, 10});
    }

    @Test void concavePolygon() {
        Shape.Polygon p = concave();
        assertTrue(p.contains(2, 2));
        assertTrue(p.contains(8, 2));
        assertTrue(p.contains(2, 8));
        assertFalse(p.contains(8, 8), "inside the notch");
        assertFalse(p.contains(-1, 5));
        assertFalse(p.contains(11, 2));
        assertFalse(p.selfIntersects());
    }

    @Test void pointOnPolygonEdgeAndVertexIsInside() {
        Shape.Polygon p = concave();
        assertTrue(p.contains(5, 0), "on bottom edge");
        assertTrue(p.contains(0, 5), "on left edge");
        assertTrue(p.contains(7, 4), "on inner (reflex) edge");
        assertTrue(p.contains(4, 4), "reflex vertex");
        assertTrue(p.contains(0, 0), "corner vertex");
        assertFalse(p.contains(4.0001, 4.0001 + 1), "just inside notch");
    }

    @Test void triangle() {
        Shape.Polygon t = new Shape.Polygon(new double[]{0, 10, 0}, new double[]{0, 0, 10});
        assertTrue(t.contains(1, 1));
        assertTrue(t.contains(5, 5), "on hypotenuse");
        assertFalse(t.contains(6, 6));
    }

    @Test void selfIntersectingBowtieDetected() {
        Shape.Polygon bow = new Shape.Polygon(new double[]{0, 10, 10, 0}, new double[]{0, 10, 0, 10});
        assertTrue(bow.selfIntersects());
    }

    @Test void polygonNeedsThreePoints() {
        assertThrows(IllegalArgumentException.class, () -> new Shape.Polygon(new double[]{0, 1}, new double[]{0, 1}));
    }

    @Test void unionOfTwoSquares() {
        Shape u = new Shape.Union(List.of(new Shape.Square(0, 0, 10), new Shape.Square(100, 0, 10)));
        assertTrue(u.contains(0, 0));
        assertTrue(u.contains(105, 5));
        assertFalse(u.contains(50, 0));
        assertTrue(u.bounds().contains(50, 0), "bounds are a conservative box");
    }

    @Test void differenceMakesRing() {
        Shape ring = new Shape.Difference(new Shape.Square(0, 0, 1500), List.of(new Shape.Square(0, 0, 100)));
        assertFalse(ring.contains(0, 0));
        assertFalse(ring.contains(99, 99));
        assertTrue(ring.contains(100, 0), "square boundary outside the hole => inside the ring");
        assertTrue(ring.contains(1499, 1499));
        assertFalse(ring.contains(1500, 0));
    }

    @Test void differenceMultipleHoles() {
        Shape d = new Shape.Difference(new Shape.Square(0, 0, 50),
                List.of(new Shape.Circle(-30, 0, 5), new Shape.Circle(30, 0, 5)));
        assertFalse(d.contains(-30, 0));
        assertFalse(d.contains(30, 0));
        assertTrue(d.contains(0, 0));
    }
}
