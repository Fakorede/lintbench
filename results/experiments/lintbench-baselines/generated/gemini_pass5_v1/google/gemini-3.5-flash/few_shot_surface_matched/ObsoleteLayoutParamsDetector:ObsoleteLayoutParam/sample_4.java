package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move "
                            + "view code around without updating the layout params. This will cause "
                            + "useless attribute processing at runtime, and is misleading for others "
                            + "reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class,
                            Scope.LAYOUT_RESOURCE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int index = name.indexOf(':');
            if (index != -1) {
                name = name.substring(index + 1);
            }
        }

        if (name.startsWith("layout_")) {
            Element element = attribute.getOwnerElement();
            if (element != null && element.getParentNode() instanceof Element) {
                Element parent = (Element) element.getParentNode();
                String parentTag = parent.getTagName();
                
                boolean isObsolete = false;
                if ("LinearLayout".equals(parentTag)) {
                    if (name.startsWith("layout_align") || name.startsWith("layout_to") || 
                        name.startsWith("layout_below") || name.startsWith("layout_above") || 
                        name.equals("layout_centerHorizontal") || name.equals("layout_centerVertical") || 
                        name.equals("layout_centerInParent")) {
                        isObsolete = true;
                    }
                } else if ("RelativeLayout".equals(parentTag)) {
                    if ("layout_weight".equals(name)) {
                        isObsolete = true;
                    }
                } else if ("FrameLayout".equals(parentTag)) {
                    if ("layout_weight".equals(name) || name.startsWith("layout_align") || 
                        name.startsWith("layout_to") || name.startsWith("layout_below") || 
                        name.startsWith("layout_above")) {
                        isObsolete = true;
                    }
                }
                
                if (isObsolete) {
                    String message = String.format("The layout parameter `%s` is not defined for parent `%s` and is ignored", name, parentTag);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Keep as no-op override to satisfy the scanner specification
    }

    @Override
    public void afterCheckRootProject(Context context) {
        // Keep as no-op override to satisfy the scanner specification
    }
}