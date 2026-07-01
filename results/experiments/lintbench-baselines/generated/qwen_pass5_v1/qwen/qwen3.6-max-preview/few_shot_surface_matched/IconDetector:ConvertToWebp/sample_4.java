package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                            + "it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion.\n\n"
                            + "Previously, launcher icons were required to be in the PNG format but that "
                            + "restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public boolean filterIncident(Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String src = element.getAttribute("android:src");
        if (src != null && (src.endsWith(".png") || src.endsWith(".jpg") || src.endsWith(".jpeg"))) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Consider converting this image to WebP format for better compression.");
        }
    }

    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            // UAST traversal is delegated to the detector overrides below
        };
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression call, PsiMethod method) {
        checkForImageExtensions(context, call);
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
        checkForImageExtensions(context, node);
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        // Class-level scanning hook for icon references
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        if (name != null && (name.endsWith("_png") || name.endsWith("_jpg") || name.endsWith("_jpeg"))) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Consider converting this icon resource to WebP format.");
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, UClass.class, USimpleNameReferenceExpression.class);
    }

    private void checkForImageExtensions(JavaContext context, UCallExpression node) {
        List<UExpression> args = node.getValueArguments();
        for (UExpression arg : args) {
            Object value = arg.evaluate();
            if (value instanceof String) {
                String str = (String) value;
                if (str.endsWith(".png") || str.endsWith(".jpg") || str.endsWith(".jpeg")) {
                    context.report(ISSUE, arg, context.getLocation(arg),
                            "Consider converting this image to WebP format for better compression.");
                }
            }
        }
    }
}