package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
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

    private static final String MESSAGE =
            "Consider converting this image to WebP, which is typically smaller than PNG/JPEG.";

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                            + "it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion. Previously, launcher "
                            + "icons were required to be in the PNG format but that restriction is no longer "
                            + "there, so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    @Override
    public void beforeCheckRootProject(Context context) {}

    @Override
    public void afterCheckEachProject(Context context) {}

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        String name = file.getName();
        return name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap attrs = element.getAttributes();
        if (attrs == null) {
            return;
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String value = attr.getValue();
            if (value != null && isRasterImageReference(value)) {
                context.report(ISSUE, attr, context.getLocation(attr), MESSAGE);
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                UClass.class,
                UMethod.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                String source = node.asSourceString();
                if (source != null && isRasterImageReference(source)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }

            @Override
            public void visitMethod(UMethod node) {
                String name = node.getName();
                if (name != null && isRasterImageReference(name)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }

            @Override
            public void visitClass(UClass node) {
                String name = node.getName();
                if (name != null && isRasterImageReference(name)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (name != null && isRasterImageReference(name)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }
        };
    }

    private static boolean isRasterImageReference(String value) {
        String lower = value.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".bmp");
    }
}