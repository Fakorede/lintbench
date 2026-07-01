package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintDriver;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlFileType;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final int WEBP_REQUIRED_API = 15;

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP image format requires Android 4.0 (API 15). Note that "
                            + "certain features, such as lossless encoding and transparency, "
                            + "require Android 4.2.1 (API 18; API 17 is Android 4.2.0). "
                            + "If your `minSdkVersion` is lower than the required API level for a "
                            + "WebP image, provide a fallback PNG or place the WebP in a "
                            + "version-qualified resource folder.",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Map<String, Integer> mWebpMinVersions;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mWebpMinVersions = new HashMap<>();
        LintDriver driver = context.getDriver();
        for (Project project : driver.getProjects()) {
            for (File resDir : project.getResourceFolders()) {
                collectWebpFiles(resDir);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No per-project cleanup needed; state is reset in beforeCheckRootProject.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int requiredApi = map.getInt("requiredApi", -1);
        if (requiredApi < 0) {
            return true;
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= requiredApi) {
            return false;
        }

        int minVersion = map.getInt("minVersion", 1);
        return minVersion < requiredApi;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.MENU
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<XmlFileType> getApplicableFiles() {
        return Collections.singletonList(XmlFileType.ANDROID_MANIFEST);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            String name = getDrawableName(value);
            if (name == null) {
                continue;
            }
            Integer minVersion = mWebpMinVersions.get(name);
            if (minVersion != null) {
                reportWebp(context, element, attr, name, minVersion);
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not needed for this check.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not needed for this check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not needed for this check; R.drawable references are caught below.
            }

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
                String simpleName = containingClass.getName();
                String qualifiedName = containingClass.getQualifiedName();
                boolean isDrawable =
                        "drawable".equals(simpleName)
                                || "mipmap".equals(simpleName)
                                || (qualifiedName != null
                                        && (qualifiedName.endsWith(".R$drawable")
                                                || qualifiedName.endsWith(".R$mipmap")));
                if (!isDrawable) {
                    return;
                }

                String name = field.getName();
                Integer minVersion = mWebpMinVersions.get(name);
                if (minVersion != null) {
                    reportWebp(context, node, name, minVersion);
                }
            }
        };
    }

    private void collectWebpFiles(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectWebpFiles(file);
            } else if (file.isFile() && file.getName().endsWith(".webp")) {
                String base = file.getName();
                base = base.substring(0, base.length() - ".webp".length());
                int version = getMinVersion(file);
                Integer existing = mWebpMinVersions.get(base);
                if (existing == null || version < existing) {
                    mWebpMinVersions.put(base, version);
                }
            }
        }
    }

    private static int getMinVersion(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return 1;
        }
        String folder = parent.getName();
        int index = folder.indexOf("-v");
        if (index < 0) {
            return 1;
        }
        int start = index + 2;
        int end = start;
        while (end < folder.length() && Character.isDigit(folder.charAt(end))) {
            end++;
        }
        if (end == start) {
            return 1;
        }
        try {
            return Integer.parseInt(folder.substring(start, end));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String getDrawableName(@NonNull String value) {
        if (value.startsWith("@drawable/")) {
            return value.substring("@drawable/".length());
        }
        if (value.startsWith("@mipmap/")) {
            return value.substring("@mipmap/".length());
        }
        return null;
    }

    private void reportWebp(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Attr attr,
            @NonNull String name,
            int minVersion) {
        String message =
                String.format(
                        "WebP image `%1$s` requires API %2$d and may not render correctly on "
                                + "devices running API %3$d or lower",
                        name,
                        WEBP_REQUIRED_API,
                        WEBP_REQUIRED_API - 1);

        LintMap map =
                LintMap.Companion.builder()
                        .put("requiredApi", WEBP_REQUIRED_API)
                        .put("minVersion", minVersion)
                        .build();

        Location location = context.getValueLocation(element, attr);
        context.report(new Incident(ISSUE, location, message).map(map));
    }

    private void reportWebp(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node,
            @NonNull String name,
            int minVersion) {
        String message =
                String.format(
                        "WebP image `%1$s` requires API %2$d and may not render correctly on "
                                + "devices running API %3$d or lower",
                        name,
                        WEBP_REQUIRED_API,
                        WEBP_REQUIRED_API - 1);

        LintMap map =
                LintMap.Companion.builder()
                        .put("requiredApi", WEBP_REQUIRED_API)
                        .put("minVersion", minVersion)
                        .build();

        context.report(new Incident(ISSUE, context.getLocation(node), message).map(map));
    }
}