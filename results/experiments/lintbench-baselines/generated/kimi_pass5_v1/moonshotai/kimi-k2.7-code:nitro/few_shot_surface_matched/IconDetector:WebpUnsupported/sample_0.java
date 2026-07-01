package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue WEBP_UNSUPPORTED =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as"
                            + " lossless encoding and transparency, requires Android 4.2.1 (API 18;"
                            + " API 17 is 4.2.0.)",
                    Category.ICONS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final int WEBP_BASE_API = 15;
    private static final int WEBP_LOSSLESS_TRANSPARENT_API = 18;

    private Set<String> mWebpResources;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mWebpResources = new HashSet<>();
        for (File resDir : context.getProject().getResourceDirectories()) {
            File[] typeDirs = resDir.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                if (typeDir.isDirectory()
                        && typeDir.getName().startsWith(ResourceFolderType.DRAWABLE.getName())) {
                    File[] files = typeDir.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        String name = file.getName();
                        if (name.toLowerCase().endsWith(".webp")) {
                            mWebpResources.add(name.substring(0, name.length() - 5));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {}

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("bitmap", "item", "ImageView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("bitmap".equals(tag)) {
            String src = element.getAttributeNS(ANDROID_URI, "src");
            checkWebPReference(context, element, src);
        } else if ("item".equals(tag)) {
            String drawable = element.getAttributeNS(ANDROID_URI, "drawable");
            checkWebPReference(context, element, drawable);
        } else if ("ImageView".equals(tag)) {
            String src = element.getAttributeNS(ANDROID_URI, "src");
            checkWebPReference(context, element, src);
        }
    }

    private void checkWebPReference(
            @NonNull XmlContext context, @NonNull Element element, @Nullable String reference) {
        if (reference == null || reference.isEmpty()) {
            return;
        }
        String name = getDrawableName(reference);
        if (name != null && mWebpResources.contains(name)) {
            reportWebP(context, element, name);
        }
    }

    @Nullable
    private static String getDrawableName(@NonNull String reference) {
        if (reference.startsWith("@drawable/")) {
            return reference.substring("@drawable/".length());
        } else if (reference.startsWith("@android:drawable/")) {
            return reference.substring("@android:drawable/".length());
        }
        return null;
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new WebPHandler(context);
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.<Class<? extends UElement>>asList(
                UClass.class, UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    private class WebPHandler extends UElementHandler {
        @NonNull private final JavaContext mContext;

        WebPHandler(@NonNull JavaContext context) {
            mContext = context;
        }

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
            if (containingClass == null || !"drawable".equals(containingClass.getName())) {
                return;
            }
            PsiClass outerClass = containingClass.getContainingClass();
            if (outerClass == null || !"R".equals(outerClass.getName())) {
                return;
            }
            String name = field.getName();
            if (name != null && mWebpResources.contains(name)) {
                reportWebP(mContext, node, name);
            }
        }
    }

    private void reportWebP(@NonNull XmlContext context, @NonNull Element element, @NonNull String name) {
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= WEBP_LOSSLESS_TRANSPARENT_API) {
            return;
        }
        String message =
                minSdk < WEBP_BASE_API
                        ? String.format(
                                "WebP image `%1$s` requires API %2$d (current minSdk is %3$d)",
                                name, WEBP_BASE_API, minSdk)
                        : String.format(
                                "WebP image `%1$s` with lossless encoding or transparency"
                                        + " requires API %2$d (current minSdk is %3$d)",
                                name, WEBP_LOSSLESS_TRANSPARENT_API, minSdk);
        context.report(WEBP_UNSUPPORTED, element, context.getLocation(element), message);
    }

    private void reportWebP(@NonNull JavaContext context, @NonNull UElement node, @NonNull String name) {
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= WEBP_LOSSLESS_TRANSPARENT_API) {
            return;
        }
        String message =
                minSdk < WEBP_BASE_API
                        ? String.format(
                                "WebP image `%1$s` requires API %2$d (current minSdk is %3$d)",
                                name, WEBP_BASE_API, minSdk)
                        : String.format(
                                "WebP image `%1$s` with lossless encoding or transparency"
                                        + " requires API %2$d (current minSdk is %3$d)",
                                name, WEBP_LOSSLESS_TRANSPARENT_API, minSdk);
        context.report(WEBP_UNSUPPORTED, node, context.getLocation(node), message);
    }
}