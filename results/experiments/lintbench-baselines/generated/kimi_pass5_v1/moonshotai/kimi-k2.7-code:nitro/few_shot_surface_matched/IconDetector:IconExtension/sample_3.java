package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LOGO;
import static com.android.SdkConstants.ATTR_ROUND_ICON;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_PROVIDER;
import static com.android.xml.AndroidManifest.NODE_RECEIVER;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (for example, a `.png`"
                            + " file is really in the PNG format and not a GIF renamed to `.png`).",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(
                                    Scope.JAVA_FILE_SCOPE,
                                    Scope.MANIFEST_SCOPE,
                                    Scope.RESOURCE_FILE_SCOPE)));

    private final Map<Project, Set<String>> mIconNames = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconNames.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        Set<String> names = mIconNames.remove(project);
        if (names == null || names.isEmpty()) {
            return;
        }
        checkIconFiles(context, project, names);
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident, boolean isPartialAnalysis) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList(
                NODE_APPLICATION,
                NODE_ACTIVITY,
                NODE_ACTIVITY_ALIAS,
                NODE_SERVICE,
                NODE_RECEIVER,
                NODE_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Project project = context.getProject();
        collectIconReference(project, element.getAttributeNS(ANDROID_URI, ATTR_ICON));
        collectIconReference(project, element.getAttributeNS(ANDROID_URI, ATTR_ROUND_ICON));
        collectIconReference(project, element.getAttributeNS(ANDROID_URI, ATTR_LOGO));
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {}

            @Override
            public void visitMethod(@NonNull UMethod node) {}

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {}

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }
                PsiField field = (PsiField) resolved;
                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                String typeName = containingClass.getName();
                if ("drawable".equals(typeName) || "mipmap".equals(typeName)) {
                    String name = field.getName();
                    if (name != null) {
                        addIconName(context.getProject(), name);
                    }
                }
            }
        };
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {}

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {}

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {}

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression reference) {}

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class,
                UMethod.class,
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    private void collectIconReference(@Nullable Project project, @Nullable String value) {
        if (value == null || value.isEmpty() || value.charAt(0) != '@') {
            return;
        }
        int slash = value.lastIndexOf('/');
        if (slash <= 0 || slash == value.length() - 1) {
            return;
        }
        String prefix = value.substring(0, slash);
        String name = value.substring(slash + 1);
        if (prefix.endsWith("drawable") || prefix.endsWith("mipmap")) {
            addIconName(project, name);
        }
    }

    private void addIconName(@Nullable Project project, @Nullable String name) {
        if (project == null || name == null || name.isEmpty()) {
            return;
        }
        mIconNames.computeIfAbsent(project, k -> new HashSet<>()).add(name);
    }

    private void checkIconFiles(
            @NonNull Context context, @NonNull Project project, @NonNull Set<String> iconNames) {
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }
        for (File res : resourceFolders) {
            File[] dirs = res.listFiles();
            if (dirs == null) {
                continue;
            }
            for (File dir : dirs) {
                String dirName = dir.getName();
                if (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) {
                    continue;
                }
                File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.isDirectory() || !file.isFile()) {
                        continue;
                    }
                    String fileName = file.getName();
                    if (fileName.contains(".9.")) {
                        continue;
                    }
                    String baseName = getBaseName(fileName);
                    if (!iconNames.contains(baseName)) {
                        continue;
                    }
                    String actualExtension = getExtension(fileName);
                    String expectedExtension = getExpectedExtension(file);
                    if (expectedExtension != null
                            && !extensionsMatch(actualExtension, expectedExtension)) {
                        context.report(
                                ISSUE,
                                Location.create(file),
                                "The icon `"
                                        + baseName
                                        + "` appears to be a "
                                        + formatName(expectedExtension)
                                        + " file but uses the `."
                                        + actualExtension
                                        + "` extension");
                    }
                }
            }
        }
    }

    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String getExtension(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase()
                : "";
    }

    @Nullable
    private static String getExpectedExtension(@NonNull File file) {
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = stream.read(header);
            if (read < 4) {
                return null;
            }
            if (header[0] == (byte) 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G') {
                return "png";
            }
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
                return "gif";
            }
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
                return "jpg";
            }
            if (read >= 12
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == 'F'
                    && header[8] == 'W'
                    && header[9] == 'E'
                    && header[10] == 'B'
                    && header[11] == 'P') {
                return "webp";
            }
            if (startsWith(header, read, "<?xml") || startsWith(header, read, "<vector")) {
                return "xml";
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static boolean startsWith(@NonNull byte[] data, int length, @NonNull String prefix) {
        byte[] prefixBytes = prefix.getBytes();
        if (length < prefixBytes.length) {
            return false;
        }
        for (int i = 0; i < prefixBytes.length; i++) {
            if (data[i] != prefixBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean extensionsMatch(@NonNull String actual, @NonNull String expected) {
        if (actual.equals(expected)) {
            return true;
        }
        return "jpg".equals(expected)
                && ("jpg".equals(actual) || "jpeg".equals(actual));
    }

    private static String formatName(@NonNull String extension) {
        switch (extension) {
            case "png":
                return "PNG";
            case "gif":
                return "GIF";
            case "jpg":
                return "JPEG";
            case "webp":
                return "WebP";
            case "xml":
                return "XML vector";
            default:
                return extension.toUpperCase();
        }
    }
}