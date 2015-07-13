package squidpony.squidai;

import squidpony.squidgrid.FOV;
import squidpony.squidgrid.Radius;
import squidpony.squidgrid.mapping.DungeonUtility;

import java.awt.*;
import java.util.HashMap;

/**
 * An AOE type that has a center and a radius, and will blast outward and somewhat around corners/obstacles, out to
 * the distance specified by radius. You can specify the RadiusType to Radius.DIAMOND for Manhattan distance,
 * RADIUS.SQUARE for Chebyshev, or RADIUS.CIRCLE for Euclidean.
 * Created by Tommy Ettinger on 7/13/2015.
 */
public class BlastAOE implements AOE {
    private FOV fov;
    private Point center;
    private int radius;
    private double[][] map;
    private Radius radiusType;
    public BlastAOE(Point center, int radius, Radius radiusType)
    {
        fov = new FOV(FOV.RIPPLE_LOOSE);
        this.center = center;
        this.radius = radius;
        this.radiusType = radiusType;
    }
    private BlastAOE()
    {
        fov = new FOV(FOV.RIPPLE_LOOSE);
        this.center = new Point(1, 1);
        this.radius = 1;
        this.radiusType = Radius.DIAMOND;
    }

    public Point getCenter() {
        return center;
    }

    public void setCenter(Point center) {
        this.center = center;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    @Override
    public void setMap(char[][] map) {
        this.map = DungeonUtility.generateResistances(map);
    }

    @Override
    public HashMap<Point, Double> findArea() {
        return AreaUtils.arrayToHashMap(fov.calculateFOV(map, center.x, center.y, radius, radiusType));
    }
}