package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. "
                            + "You should follow these conventions to make sure your icons "
                            + "fit in with the overall look of the platform. "
                            + "The expected sizes are 48x48 (mdpi), 72x72 (hdpi), "
                            + "96x96 (xhdpi), 144x144 (xxhdpi), and 192x192 (xxxhdpi) pixels.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String TAG_BITMAP = "bitmap";
    private static final String TAG_VECTOR = "vector";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_WIDTH = "width";
    private static final String ATTR_HEIGHT = "height";

    private static final int BASELINE_MDPI_SIZE = 48;
    private static final double TOLERANCE = 0.5;
    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*(px|dp|dip|sp|pt|in|mm)?\\s*$");

    private final Map<String, List<IconResource>> mIcons = new HashMap<>();

    private static class IconResource {
        final String name;
        final ResourceFolderType folderType;
        final Density density;
        final double width;
        final double height;
        final Location location;

        IconResource(String name, ResourceFolderType folderType, Density density,
                     double width, double height, Location location) {
            this.name = name;
            this.folderType = folderType;
            this.density = density;
            this.width = width;
            this.height = height;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIcons.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        scanBinaryResources(context);
        checkApplicationIcons(context);
        mIcons.clear();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_BITMAP, TAG_VECTOR);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getParentNode() == null
                || element.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        String widthAttr = element.getAttributeNS(ANDROID_URI, ATTR_WIDTH);
        String heightAttr = element.getAttributeNS(ANDROID_URI, ATTR_HEIGHT);
        if (widthAttr.isEmpty() || heightAttr.isEmpty()) {
            return;
        }

        Density density = getDensityFromFolderName(context.file.getParentFile().getName());
        double width = parseDimension(widthAttr, density);
        double height = parseDimension(heightAttr, density);
        if (width <= 0 || height <= 0) {
            return;
        }

        String name = getResourceName(context.file);
        IconResource icon = new IconResource(
                name,
                ResourceFolderType.MIPMAP,
                density,
                width,
                height,
                context.getLocation(element));
        storeIcon(icon);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Launcher icon size checks do not require class-level inspection.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for launcher icon size checks.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for launcher icon size checks.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for launcher icon size checks.
            }
        };
    }

    private void storeIcon(IconResource icon) {
        List<IconResource> list = mIcons.get(icon.name);
        if (list == null) {
            list = new ArrayList<>();
            mIcons.put(icon.name, list);
        }
        list.add(icon);
    }

    private void scanBinaryResources(Context context) {
        for (File resDir : context.getProject().getResourceFolders()) {
            if (resDir == null || !resDir.isDirectory()) {
                continue;
            }
            File[] typeDirs = resDir.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                if (!typeDir.isDirectory()) {
                    continue;
                }
                String typeName = typeDir.getName();
                if (!typeName.startsWith(ResourceFolderType.MIPMAP.name().toLowerCase(Locale.US))) {
                    continue;
                }
                Density density = getDensityFromFolderName(typeName);
                File[] files = typeDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file == null || !file.isFile() || !isImageFile(file)) {
                        continue;
                    }
                    int[] dims = readImageDimensions(file);
                    if (dims != null && dims[0] > 0 && dims[1] > 0) {
                        String name = getResourceName(file);
                        IconResource icon = new IconResource(
                                name,
                                ResourceFolderType.MIPMAP,
                                density,
                                dims[0],
                                dims[1],
                                Location.create(file));
                        storeIcon(icon);
                    }
                }
            }
        }
    }

    private void checkApplicationIcons(Context context) {
        for (List<IconResource> icons : mIcons.values()) {
            for (IconResource icon : icons) {
                if (icon.density == Density.NODPI || icon.density == Density.ANYDPI) {
                    continue;
                }
                double expected = BASELINE_MDPI_SIZE * getScale(icon.density);
                if (Math.abs(icon.width - expected) > TOLERANCE
                        || Math.abs(icon.height - expected) > TOLERANCE) {
                    int expectedInt = (int) Math.round(expected);
                    String message = String.format(Locale.US,
                            "The icon `%1$s` in `%2$s` is %3$dx%4$d px, "
                                    + "but for %5$s it should be %6$dx%6$d px",
                            icon.name,
                            getFolderName(icon),
                            (int) icon.width,
                            (int) icon.height,
                            getDensityName(icon.density),
                            expectedInt);
                    LintMap map = createMap(icon);
                    Incident incident = new Incident(ISSUE, icon.location, message, map);
                    context.report(incident, map);
                }
            }
        }
    }

    private Density getDensityFromFolderName(String folderName) {
        String[] parts = folderName.split("-");
        for (String part : parts) {
            if (part.equals("ldpi")) {
                return Density.LDPI;
            }
            if (part.equals("mdpi")) {
                return Density.MDPI;
            }
            if (part.equals("hdpi")) {
                return Density.HDPI;
            }
            if (part.equals("xhdpi")) {
                return Density.XHDPI;
            }
            if (part.equals("xxhdpi")) {
                return Density.XXHDPI;
            }
            if (part.equals("xxxhdpi")) {
                return Density.XXXHDPI;
            }
            if (part.equals("tvdpi")) {
                return Density.TVDPI;
            }
            if (part.equals("nodpi")) {
                return Density.NODPI;
            }
            if (part.equals("anydpi")) {
                return Density.ANYDPI;
            }
        }
        return Density.MDPI;
    }

    private double getScale(Density density) {
        if (density == Density.NODPI || density == Density.ANYDPI) {
            return 1.0;
        }
        return density.getDpi() / 160.0;
    }

    private double parseDimension(String value, Density density) {
        Matcher matcher = DIMENSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return -1;
        }
        double number = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null) {
            return number;
        }
        switch (unit) {
            case "px":
                return number;
            case "dp":
            case "dip":
            case "sp":
                return number * getScale(density);
            case "pt":
                return number * density.getDpi() / 72.0;
            case "in":
                return number * density.getDpi();
            case "mm":
                return number * density.getDpi() / 25.4;
            default:
                return -1;
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp");
    }

    private int[] readImageDimensions(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            if (name.endsWith(".png")) {
                return readPngDimensions(stream);
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                return readJpegDimensions(stream);
            } else if (name.endsWith(".gif")) {
                return readGifDimensions(stream);
            }
        } catch (IOException e) {
            // Ignore files we cannot read.
        }
        return null;
    }

    private int[] readPngDimensions(BufferedInputStream stream) throws IOException {
        byte[] header = new byte[24];
        if (stream.read(header) != 24) {
            return null;
        }
        if (header[0] != (byte) 0x89 || header[1] != 0x50 || header[2] != 0x4E
                || header[3] != 0x47) {
            return null;
        }
        int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
        int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
        return new int[] { width, height };
    }

    private int[] readJpegDimensions(BufferedInputStream stream) throws IOException {
        if (stream.read() != 0xFF || stream.read() != 0xD8) {
            return null;
        }
        while (true) {
            int marker = stream.read();
            if (marker < 0) {
                return null;
            }
            if (marker == 0xFF) {
                continue;
            }
            int type = marker;
            if (type == 0xD9 || type == 0xDA) {
                return null;
            }
            int lengthHi = stream.read();
            int lengthLo = stream.read();
            if (lengthHi < 0 || lengthLo < 0) {
                return null;
            }
            int length = (lengthHi << 8) | lengthLo;
            if (length < 2) {
                return null;
            }
            if (type == 0xC0 || type == 0xC1 || type == 0xC2) {
                stream.read();
                int hHi = stream.read();
                int hLo = stream.read();
                int wHi = stream.read();
                int wLo = stream.read();
                int height = (hHi << 8) | hLo;
                int width = (wHi << 8) | wLo;
                return new int[] { width, height };
            } else {
                long skipped = stream.skip(length - 2);
                int remaining = length - 2 - (int) skipped;
                for (int i = 0; i < remaining; i++) {
                    if (stream.read() < 0) {
                        return null;
                    }
                }
            }
        }
    }

    private int[] readGifDimensions(BufferedInputStream stream) throws IOException {
        byte[] header = new byte[10];
        if (stream.read(header) != 10) {
            return null;
        }
        if (header[0] != 'G' || header[1] != 'I' || header[2] != 'F') {
            return null;
        }
        int width = (header[6] & 0xFF) | ((header[7] & 0xFF) << 8);
        int height = (header[8] & 0xFF) | ((header[9] & 0xFF) << 8);
        return new int[] { width, height };
    }

    private String getResourceName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private String getFolderName(IconResource icon) {
        return icon.folderType.name().toLowerCase(Locale.US) + "-" + getDensityName(icon.density);
    }

    private String getDensityName(Density density) {
        return density.name().toLowerCase(Locale.US);
    }

    private LintMap createMap(IconResource icon) {
        LintMap.Builder builder = new LintMap.Builder();
        builder.putString("name", icon.name);
        builder.putString("density", icon.density.name());
        builder.putString("expected",
                String.valueOf((int) Math.round(BASELINE_MDPI_SIZE * getScale(icon.density))));
        return builder.build();
    }
}