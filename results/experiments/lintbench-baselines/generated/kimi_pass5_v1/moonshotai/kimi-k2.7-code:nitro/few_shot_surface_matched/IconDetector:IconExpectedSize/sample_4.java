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
import com.android.tools.lint.model.LintModelCondition;
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

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_EXPECTED_SIZE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. You should "
                            + "follow these conventions to make sure your icons fit in with the "
                            + "overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.MANIFEST_SCOPE, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ICON = "icon";

    public IconDetector() {}

    @Override
    public boolean appliesTo(Context context, File file) {
        String name = file.getName();
        return name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // Reset any per-run state.
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // Perform any cross-file finalization.
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, LintModelCondition condition) {
        return true;
    }

    // XmlScanner
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "activity-alias");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr iconAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_ICON);
        if (iconAttr == null) {
            return;
        }
        String icon = iconAttr.getValue();
        if (icon.isEmpty()) {
            return;
        }
        if (icon.startsWith("@drawable/")) {
            context.report(
                    ICON_EXPECTED_SIZE,
                    iconAttr,
                    context.getLocation(iconAttr),
                    "Launcher icons should be placed in mipmap folders and follow the expected "
                            + "size conventions for each density");
        }
    }

    // SourceCodeScanner
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class,
                UMethod.class,
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitMethod(UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        // Inspect classes that set launcher icons.
    }

    @Override
    public void visitMethod(JavaContext context, UMethod node) {
        // Inspect methods that reference launcher icon resources.
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
        // Inspect icon-related API calls such as setImageResource/setIcon.
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            JavaContext context, USimpleNameReferenceExpression node) {
        // Inspect references to launcher icon resources.
    }
}