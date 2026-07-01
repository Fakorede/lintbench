package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_PADDING_START;
import static com.android.SdkConstants.ATTR_PADDING_END;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) for "
                            + "right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    // Map from left attribute to its right counterpart
    private static final Map<String, String> LEFT_TO_RIGHT = new HashMap<>();
    // Map from right attribute to its left counterpart
    private static final Map<String, String> RIGHT_TO_LEFT = new HashMap<>();

    static {
        LEFT_TO_RIGHT.put(ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT);
        LEFT_TO_RIGHT.put(ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT);

        RIGHT_TO_LEFT.put(ATTR_PADDING_RIGHT, ATTR_PADDING_LEFT);
        RIGHT_TO_LEFT.put(ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_LEFT);
    }

    // Track incidents that need to be deferred until we know if RTL attributes are used
    private final Map<String, Incident> mPendingIncidents = new HashMap<>();

    // Track whether RTL/start/end attributes are used in the project
    private boolean mUsesRtlAttributes = false;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        Element element = attribute.getOwnerElement();
        NamedNodeMap attrs = element.getAttributes();

        String opposite = LEFT_TO_RIGHT.get(name);
        boolean isLeft = opposite != null;
        if (!isLeft) {
            opposite = RIGHT_TO_LEFT.get(name);
        }

        if (opposite == null) {
            return;
        }

        // Check if the opposite attribute is already set
        if (attrs.getNamedItem(opposite) != null
                || attrs.getNamedItemNS(attribute.getNamespaceURI(), opposite) != null) {
            return;
        }

        // Also check with android: namespace prefix
        String androidOpposite = "android:" + opposite;
        boolean hasOpposite = false;
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            String attrLocalName = a.getLocalName();
            if (attrLocalName == null) {
                attrLocalName = a.getName();
            }
            if (opposite.equals(attrLocalName)) {
                hasOpposite = true;
                break;
            }
        }

        if (hasOpposite) {
            return;
        }

        // Check if start/end equivalents are set (which would handle RTL)
        if (isLeft) {
            // left is set; check if paddingStart or marginStart is also set
            String startAttr = name.equals(ATTR_PADDING_LEFT) ? ATTR_PADDING_START : ATTR_LAYOUT_MARGIN_START;
            String endAttr = name.equals(ATTR_PADDING_LEFT) ? ATTR_PADDING_END : ATTR_LAYOUT_MARGIN_END;
            if (hasAttribute(attrs, startAttr) && hasAttribute(attrs, endAttr)) {
                return;
            }
        } else {
            // right is set; check if paddingEnd or marginEnd is also set
            String startAttr = name.equals(ATTR_PADDING_RIGHT) ? ATTR_PADDING_START : ATTR_LAYOUT_MARGIN_START;
            String endAttr = name.equals(ATTR_PADDING_RIGHT) ? ATTR_PADDING_END : ATTR_LAYOUT_MARGIN_END;
            if (hasAttribute(attrs, startAttr) && hasAttribute(attrs, endAttr)) {
                return;
            }
        }

        String side = isLeft ? "left" : "right";
        String otherSide = isLeft ? "right" : "left";
        String message =
                "Should use \""
                        + opposite
                        + "\" to ensure correct behavior in right-to-left locales (was \""
                        + name
                        + "\"). If not intentional, also specify \""
                        + opposite
                        + "\" to ensure symmetry.";

        Location location = context.getLocation(attribute);
        String key = context.file.getPath() + ":" + element.hashCode() + ":" + side;

        Incident incident = new Incident(ISSUE, attribute, location, message);
        mPendingIncidents.put(key, incident);
    }

    private static boolean hasAttribute(@NonNull NamedNodeMap attrs, @NonNull String localName) {
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            String attrLocalName = a.getLocalName();
            if (attrLocalName == null) {
                attrLocalName = a.getName();
            }
            if (localName.equals(attrLocalName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull com.android.tools.lint.detector.api.LintMap map) {
        // If the project uses RTL attributes (start/end), we should be more lenient
        // For now, report all pending incidents
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Incident incident : mPendingIncidents.values()) {
            context.report(incident);
        }
        mPendingIncidents.clear();
    }

    // SourceCodeScanner implementation to detect RTL attribute usage in Java/Kotlin code

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (name.contains("Start")
                        || name.contains("End")
                        || name.contains("Rtl")
                        || name.contains("rtl")) {
                    mUsesRtlAttributes = true;
                }
            }
        };
    }
}

// Needed import for Collections
import java.util.Collections;

// Needed import for UElementHandler
import org.jetbrains.uast.visitor.AbstractUastVisitor;