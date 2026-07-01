package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;

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
import com.android.tools.lint.detector.api.LintMap;
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
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String KEY_MIN_SDK = "minSdk";
    private static final String KEY_ATTRIBUTE = "attribute";
    private static final int RTL_API = 17;

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                            + "if you are supporting older versions than API 17, you must **also** "
                            + "specify a gravity or layout_gravity attribute, since older platforms "
                            + "will ignore the `textAlignment` attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    public RtlDetector() {}

    // ---- XML Scanning ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_TEXT_ALIGNMENT, ATTR_GRAVITY, ATTR_LAYOUT_GRAVITY);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        if (ATTR_TEXT_ALIGNMENT.equals(name)) {
            // Check if the element also has a gravity or layout_gravity attribute
            Element element = attribute.getOwnerElement();
            NamedNodeMap attrs = element.getAttributes();
            boolean hasGravity = false;
            for (int i = 0; i < attrs.getLength(); i++) {
                String attrName = attrs.item(i).getLocalName();
                if (attrName == null) {
                    attrName = attrs.item(i).getNodeName();
                }
                if (ATTR_GRAVITY.equals(attrName) || ATTR_LAYOUT_GRAVITY.equals(attrName)) {
                    hasGravity = true;
                    break;
                }
            }

            if (!hasGravity) {
                Location location = context.getLocation(attribute);
                String message =
                        "To support older versions than API 17 (minSdkVersion is %1$s) you "
                                + "should **also** specify `gravity` or `layout_gravity` when "
                                + "specifying `textAlignment`";
                Incident incident =
                        new Incident(ISSUE, attribute, location, message, (LintFix) null);
                LintMap map = new LintMap();
                map.put(KEY_ATTRIBUTE, ATTR_TEXT_ALIGNMENT);
                context.report(incident, map);
            }
        }
    }

    // ---- Source Code Scanning ----

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return List.of(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UastHandler createUastHandler(@NonNull JavaContext context) {
        return new UastHandler(context);
    }

    private static class UastHandler extends UElementHandler {
        private final JavaContext mContext;

        UastHandler(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            String name = node.getIdentifier();
            if ("textAlignment".equals(name)
                    || "TEXT_ALIGNMENT_CENTER".equals(name)
                    || "TEXT_ALIGNMENT_GRAVITY".equals(name)
                    || "TEXT_ALIGNMENT_INHERIT".equals(name)
                    || "TEXT_ALIGNMENT_TEXT_END".equals(name)
                    || "TEXT_ALIGNMENT_TEXT_START".equals(name)
                    || "TEXT_ALIGNMENT_VIEW_END".equals(name)
                    || "TEXT_ALIGNMENT_VIEW_START".equals(name)) {
                PsiElement resolved = node.resolve();
                if (resolved != null) {
                    String message =
                            "API attribute `textAlignment` requires API level 17 (current min is "
                                    + "%1$s): Gravity should be used instead to support older "
                                    + "platforms";
                    Incident incident =
                            new Incident(
                                    ISSUE,
                                    node,
                                    mContext.getLocation(node),
                                    message,
                                    (LintFix) null);
                    LintMap map = new LintMap();
                    map.put(KEY_ATTRIBUTE, "textAlignment");
                    mContext.report(incident, map);
                }
            }
        }
    }

    // ---- Filtering ----

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= RTL_API) {
            // minSdkVersion is high enough; no need to report
            return false;
        }
        // Update the message with the actual minSdkVersion
        String message = incident.getMessage();
        if (message.contains("%1$s")) {
            incident.setMessage(message.replace("%1$s", String.valueOf(minSdk)));
        }
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing additional needed after checking the root project
    }
}

// Helper base class to avoid having to implement all methods of UElementHandler
abstract class UElementHandler extends com.android.tools.lint.detector.api.UElementHandler {
}