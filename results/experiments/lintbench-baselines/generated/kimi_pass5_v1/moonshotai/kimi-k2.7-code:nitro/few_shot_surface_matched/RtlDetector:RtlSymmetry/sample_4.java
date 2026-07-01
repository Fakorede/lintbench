package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue SYMMETRY =
            Issue.create(
                    "RtlSymmetry",
                    "Padding/Margin RTL Symmetry",
                    "If you specify padding or margin on the left side of a layout, you should"
                            + " probably also specify padding on the right side (and vice versa)"
                            + " for right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Map<String, String> PAIRS;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("paddingLeft", "paddingRight");
        map.put("paddingRight", "paddingLeft");
        map.put("paddingStart", "paddingEnd");
        map.put("paddingEnd", "paddingStart");
        map.put("layout_marginLeft", "layout_marginRight");
        map.put("layout_marginRight", "layout_marginLeft");
        map.put("layout_marginStart", "layout_marginEnd");
        map.put("layout_marginEnd", "layout_marginStart");
        PAIRS = Collections.unmodifiableMap(map);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return new ArrayList<>(PAIRS.keySet());
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String name = attribute.getLocalName();
        String pair = PAIRS.get(name);
        if (pair == null) {
            return;
        }
        Element element = attribute.getOwnerElement();
        if (hasAttribute(element, pair)) {
            return;
        }
        String base = name.startsWith("layout_margin") ? "layout_margin" : "padding";
        if (hasAttribute(element, base)) {
            return;
        }
        String message =
                "To maintain right-to-left symmetry, you should also define `"
                        + pair
                        + "` when defining `"
                        + name
                        + "`";
        context.report(SYMMETRY, attribute, context.getLocation(attribute), message);
    }

    private static boolean hasAttribute(Element element, String name) {
        return element.getAttributeNodeNS(ANDROID_URI, name) != null;
    }

    @Override
    public boolean filterIncident(Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(Context context) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return null;
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            JavaContext context, USimpleNameReferenceExpression reference) {
    }
}