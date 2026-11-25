package com.poligorn.ikariaborders.map;

import com.poligorn.ikariaborders.zone.Shape;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire format of {@code ikaria_borders:sync} (pure JVM, so it is unit-tested without the game).
 * Layout: int protocol | long version | int zoneCount | zone*. No compression, a few KB for typical configs.
 * Decoding is defensive: bad or oversized input throws {@link IOException}, never allocates unbounded memory.
 */
public final class ZoneMapCodec {
    public static final int PROTOCOL = 1;
    /** Hard cap for the encoded blob (also used as the byte-array bound of the payload codec). */
    public static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_ZONES = 1024, MAX_POINTS = 65_536, MAX_CHILDREN = 1024, MAX_DEPTH = 16, MAX_STR = 1024;

    private ZoneMapCodec() {}

    public static byte[] encode(ZoneMapData data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeInt(PROTOCOL);
            out.writeLong(data.version());
            writeZones(out, data.zones());
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e); // cannot happen for in-memory streams
        }
    }

    public static ZoneMapData decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("слишком большой пакет: " + bytes.length);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int protocol = in.readInt();
        if (protocol != PROTOCOL) throw new IOException("неизвестная версия протокола: " + protocol);
        long version = in.readLong();
        int n = in.readInt();
        if (n < 0 || n > MAX_ZONES) throw new IOException("некорректное число зон: " + n);
        List<MapZone> zones = new ArrayList<>(n);
        for (int i = 0; i < n; i++) zones.add(readZone(in));
        if (in.available() > 0) throw new IOException("лишние данные в конце пакета");
        return new ZoneMapData(version, zones);
    }

    /** Version = FNV-1a 64 over the encoded zone list (stable across JVMs). */
    static ZoneMapData withComputedVersion(List<MapZone> zones) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            writeZones(out, zones);
            out.flush();
            long h = 0xcbf29ce484222325L;
            for (byte b : bos.toByteArray()) {
                h ^= (b & 0xFF);
                h *= 0x100000001b3L;
            }
            return new ZoneMapData(h, zones);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeZones(DataOutputStream out, List<MapZone> zones) throws IOException {
        out.writeInt(zones.size());
        for (MapZone z : zones) {
            writeStr(out, z.id());
            writeStr(out, z.name());
            writeStr(out, z.dimension());
            out.writeBoolean(z.shape() != null);
            if (z.shape() != null) writeShape(out, z.shape());
            out.writeInt(z.priority());
            out.writeInt(z.order());
            out.writeInt(z.rgb());
            out.writeBoolean(z.fill());
            out.writeBoolean(z.outline());
            out.writeFloat(z.opacity());
            out.writeBoolean(z.outer());
            out.writeBoolean(z.label() != null);
            if (z.label() != null) writeStr(out, z.label());
        }
    }

    private static MapZone readZone(DataInputStream in) throws IOException {
        String id = readStr(in), name = readStr(in), dim = readStr(in);
        Shape shape = in.readBoolean() ? readShape(in, 0) : null;
        int priority = in.readInt(), order = in.readInt(), rgb = in.readInt();
        boolean fill = in.readBoolean(), outline = in.readBoolean();
        float opacity = in.readFloat();
        boolean outer = in.readBoolean();
        String label = in.readBoolean() ? readStr(in) : null;
        return new MapZone(id, name, dim, shape, priority, order, rgb, fill, outline, opacity, outer, label);
    }

    private static void writeShape(DataOutputStream out, Shape s) throws IOException {
        switch (s) {
            case Shape.Circle c -> { out.writeByte(0); out.writeDouble(c.cx()); out.writeDouble(c.cz()); out.writeDouble(c.radius()); }
            case Shape.Square q -> { out.writeByte(1); out.writeDouble(q.cx()); out.writeDouble(q.cz()); out.writeDouble(q.half()); }
            case Shape.Rect r -> { out.writeByte(2); out.writeInt(r.minX()); out.writeInt(r.minZ()); out.writeInt(r.maxX()); out.writeInt(r.maxZ()); }
            case Shape.Polygon p -> {
                out.writeByte(3);
                out.writeInt(p.size());
                for (int i = 0; i < p.size(); i++) { out.writeDouble(p.xs()[i]); out.writeDouble(p.zs()[i]); }
            }
            case Shape.Union u -> {
                out.writeByte(4);
                out.writeInt(u.shapes().size());
                for (Shape c : u.shapes()) writeShape(out, c);
            }
            case Shape.Difference d -> {
                out.writeByte(5);
                writeShape(out, d.base());
                out.writeInt(d.minus().size());
                for (Shape c : d.minus()) writeShape(out, c);
            }
        }
    }

    private static Shape readShape(DataInputStream in, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("слишком глубокая вложенность форм");
        int type = in.readUnsignedByte();
        switch (type) {
            case 0: return new Shape.Circle(in.readDouble(), in.readDouble(), in.readDouble());
            case 1: return new Shape.Square(in.readDouble(), in.readDouble(), in.readDouble());
            case 2: return new Shape.Rect(in.readInt(), in.readInt(), in.readInt(), in.readInt());
            case 3: {
                int n = in.readInt();
                if (n < 3 || n > MAX_POINTS) throw new IOException("некорректное число точек: " + n);
                double[] xs = new double[n], zs = new double[n];
                for (int i = 0; i < n; i++) { xs[i] = in.readDouble(); zs[i] = in.readDouble(); }
                return new Shape.Polygon(xs, zs);
            }
            case 4: {
                int n = in.readInt();
                if (n < 1 || n > MAX_CHILDREN) throw new IOException("некорректное число форм: " + n);
                List<Shape> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(readShape(in, depth + 1));
                return new Shape.Union(l);
            }
            case 5: {
                Shape base = readShape(in, depth + 1);
                int n = in.readInt();
                if (n < 0 || n > MAX_CHILDREN) throw new IOException("некорректное число форм: " + n);
                List<Shape> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(readShape(in, depth + 1));
                return new Shape.Difference(base, l);
            }
            default: throw new IOException("неизвестный тип формы: " + type);
        }
    }

    private static void writeStr(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        while (b.length > MAX_STR) { // never fail the whole sync because of one absurdly long name
            s = s.substring(0, s.length() - Math.max(1, (b.length - MAX_STR) / 4));
            b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        out.writeShort(b.length);
        out.write(b);
    }

    private static String readStr(DataInputStream in) throws IOException {
        int n = in.readUnsignedShort();
        if (n > MAX_STR) throw new IOException("строка длиннее " + MAX_STR + " байт");
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }
}
