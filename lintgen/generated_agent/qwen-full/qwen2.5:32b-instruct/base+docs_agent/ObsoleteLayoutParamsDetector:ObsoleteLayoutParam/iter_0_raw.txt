package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParams",
            "The given layout_param is not defined for the given layout, meaning it has no effect.",
            "This usually happens when you change the parent layout or move view code around without updating the layout params. This will cause useless attribute processing at runtime and is misleading for others reading the layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCES_SCOPE
            )
    );

    private static final Set<String> LINEAR_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_weight",
            "layout_gravity"
    ));

    private static final Set<String> RELATIVE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_alignParentLeft",
            "layout_alignParentTop",
            "layout_toRightOf",
            "layout_below"
    ));

    @NonNull
    @Override
    public List<UElementHandler> getApplicableElements() {
        return Arrays.<UElementHandler>asList(new UElementHandler(Element.class) {
            @Override
            public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
                checkLayoutParams(context, element);
            }
        });
    }

    private void checkLayoutParams(XmlContext context, Element element) {
        String tagName = element.getTagName();
        Set<String> validParams = getValidLayoutParams(tagName);

        for (Attr attr : LintUtils.attrs(element)) {
            String attrName = attr.getName();
            if (!attrName.startsWith("layout_")) continue;

            if (!validParams.contains(attrName)) {
                Location location = context.getLocation(attr);
                context.report(ISSUE, location, "The layout parameter '" + attrName + "' is not valid for this view.");
            }
        }
    }

    private Set<String> getValidLayoutParams(String tagName) {
        switch (tagName) {
            case SdkConstants.TAG_LINEAR_LAYOUT:
                return LINEAR_LAYOUT_PARAMS;
            case SdkConstants.TAG_RELATIVE_LAYOUT:
                return RELATIVE_LAYOUT_PARAMS;
            default:
                return new HashSet<>();
        }
    }
}