package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue WEBP_ELIGIBLE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android "
                            + "4.2.1 it supports transparency and lossless conversion as well. "
                            + "Note that there is a quickfix in the IDE which lets you perform "
                            + "conversion.\n\n"
                            + "Previously, launcher icons were required to be in the PNG format "
                            + "but that restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_IMAGE_VIEW = "ImageView";
    private static final String TAG_APP_COMPAT_IMAGE_VIEW = "androidx.appcompat.widget.AppCompatImageView";
    private static final String ATTR_SRC = "src";
    private static final String ATTR_APP_SRC = "srcCompat";

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Hook for initialization before checking the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Hook for cleanup after checking each project
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        // Allow all incidents through by default
        return false;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_IMAGE_VIEW, TAG_APP_COMPAT_IMAGE_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (TAG_IMAGE_VIEW.equals(tagName) || TAG_APP_COMPAT_IMAGE_VIEW.equals(tagName)) {
            // Check if the element references a PNG or JPEG drawable that could be WebP
            org.w3c.dom.NamedNodeMap attrs = element.getAttributes();
            if (attrs != null) {
                for (int i = 0; i < attrs.getLength(); i++) {
                    org.w3c.dom.Node attr = attrs.item(i);
                    String attrName = attr.getLocalName();
                    if (ATTR_SRC.equals(attrName) || ATTR_APP_SRC.equals(attrName)) {
                        String value = attr.getNodeValue();
                        if (value != null && (value.endsWith(".png") || value.endsWith(".jpg")
                                || value.endsWith(".jpeg"))) {
                            context.report(
                                    WEBP_ELIGIBLE,
                                    element,
                                    context.getLocation((org.w3c.dom.Attr) attr),
                                    "One or more images in this project can be converted to "
                                            + "the WebP format which typically results in "
                                            + "smaller file sizes, even for lossless conversion");
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public com.android.tools.lint.detector.api.UastCallVisitor createUastHandler(
            @NonNull JavaContext context) {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Check method calls that reference image resources
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // Inspect call expressions for image resource references
        String methodName = node.getMethodName();
        if (methodName != null && (methodName.equals("setImageResource")
                || methodName.equals("setImageDrawable")
                || methodName.equals("setBackgroundResource"))) {
            // Could check if the referenced resource is a PNG/JPEG
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level checks needed for WebP conversion
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No simple name reference checks needed
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "setImageResource",
                "setImageDrawable",
                "setBackgroundResource",
                "setBackground");
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }
}