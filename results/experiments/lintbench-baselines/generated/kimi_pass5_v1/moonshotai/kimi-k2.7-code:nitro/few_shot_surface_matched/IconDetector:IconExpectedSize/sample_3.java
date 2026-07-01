package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. You should "
                            + "follow these conventions to make sure your icons fit in with the "
                            + "overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.ALL));

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void afterCheckEachProject(Context context) {
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, LintMap map) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr icon =
                element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "icon");
        if (icon != null) {
            String value = icon.getValue();
            if (value != null && value.startsWith("@")) {
                context.report(
                        ISSUE,
                        icon,
                        context.getLocation(icon),
                        "Icon has incorrect size: follow the platform density conventions for "
                                + "launcher icons (" + value + ")");
            }
        }
    }

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
            }

            @Override
            public void visitMethod(UMethod node) {
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
            }
        };
    }
}