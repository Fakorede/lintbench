package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final int LAUNCHER_SIZE_DP = 48;
    private static final int DEFAULT_DENSITY = 160;

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. "
                            + "You should follow these conventions to make sure your icons "
                            + "fit in with the overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(Context context) {
        // No per-project state needs to be initialized.
    }

    @Override
    public void afterCheckEachProject(Context context) {
        if (context.getProject() != context.getMainProject()) {
            return;
        }
        checkLauncherIconSizes(context);
    }

    @Override
    public boolean filterIncident(
            Context context, Incident incident, LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Launcher icon sizes are checked by scanning resource files directly.
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Not needed for this check.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                // Not needed for this check.
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                // Not needed for this check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression node) {
                // Not needed for this check.
            }
        };
    }

    private void checkLauncherIconSizes(Context context) {
        com.android.tools.lint.detector.api.Project project = context.getMainProject();
        if (project == null) {
            return;
        }

        for (File resDir : project.getResourceFolders()) {
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
                if (!typeName.startsWith("drawable") && !typeName.startsWith("mipmap")) {
                    continue;
                }
                int density = getDensityQualifier(typeName);
                if (density < 0) {
                    continue;
                }
                int expectedSize = (LAUNCHER_SIZE_DP * density) / DEFAULT_DENSITY;

                File[] files = typeDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!isLauncherIcon(file)) {
                        continue;
                    }
                    int[] size = getImageSize(file);
                    if (size == null) {
                        continue;
                    }
                    if (size[0] != expectedSize || size[1] != expectedSize) {
                        String message =
                                String.format(
                                        "Expected launcher icon size of %1$dx%1$d px for "
                                                + "this density, but found %2$dx%3$d px",
                                        expectedSize,
                                        size[0],
                                        size[1]);
                        context.report(
                                new Incident(ISSUE, new Location(file), message));
                    }
                }
            }
        }
    }

    private static boolean isLauncherIcon(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.startsWith("ic_launcher") && file.isFile();
    }

    private static int[] getImageSize(File file) {
        if (!file.getName().toLowerCase().endsWith(".png")) {
            return null;
        }

        byte[] header = new byte[24];
        try (FileInputStream fis = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(fis)) {
            if (dis.read(header) != header.length) {
                return null;
            }
            // PNG signature: 0x89 'P' 'N' 'G' 0x0D 0x0A 0x1A 0x0A
            if (header[0] != (byte) 0x89
                    || header[1] != 0x50
                    || header[2] != 0x4E
                    || header[3] != 0x47
                    || header[4] != 0x0D
                    || header[5] != 0x0A
                    || header[6] != 0x1A
                    || header[7] != 0x0A) {
                return null;
            }
            // IHDR chunk type at bytes 12-15
            if (header[12] != 'I'
                    || header[13] != 'H'
                    || header[14] != 'D'
                    || header[15] != 'R') {
                return null;
            }
            int width = readInt(header, 16);
            int height = readInt(header, 20);
            return new int[] {width, height};
        } catch (IOException e) {
            return null;
        }
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static int getDensityQualifier(String folderName) {
        if (folderName.contains("anydpi") || folderName.contains("nodpi")) {
            return -1;
        }
        if (folderName.contains("xxxhdpi")) {
            return 640;
        }
        if (folderName.contains("xxhdpi")) {
            return 480;
        }
        if (folderName.contains("xhdpi")) {
            return 320;
        }
        if (folderName.contains("hdpi")) {
            return 240;
        }
        if (folderName.contains("mdpi")) {
            return 160;
        }
        if (folderName.contains("ldpi")) {
            return 120;
        }
        // Default density for unqualified drawable/mipmap folders.
        return 160;
    }
}