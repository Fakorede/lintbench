package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.flatten;
import com.android.utils.join;
import com.android.utils.parseXmlAttribute;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.List;

public class RtlDetector extends Detector implements Detector.XmlScanner {
    private static final String SUPPORTS_RTL = "android:supportsRtl";
    private static final String APPLICATION_ELEMENT = "application";

    @Nullable
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of(APPLICATION_ELEMENT);
    }

    @NonNull
    @Override
    public Scope getScope() {
        return Scope.MANIFEST_SCOPE;
    }

    @Override
    public int visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getTagName().equals(APPLICATION_ELEMENT)) {
            Attr supportsRtlAttr = XmlUtils.findAttribute(element, SUPPORTS_RTL);
            if (supportsRtlAttr == null || !supportsRtlAttr.getValue().equalsIgnoreCase("true")) {
                String message = "Using RTL attributes without enabling RTL support. "
                        + "Set android:supportsRtl=\"true\" in the <application> element.";
                context.report(this, supportsRtlAttr != null ? supportsRtlAttr : element,
                        getIssue(), message);
            }
        }
        return super.visitElement(context, element);
    }

    public static final Issue ISSUE = Issue.create(
            "MissingSupportsRtl",
            "Using RTL attributes without enabling RTL support",
            "To enable right-to-left support, when running on API 17 and higher, you must set the `android:supportsRtl` attribute in the manifest `<application>` element. If you have started adding RTL attributes, but have not yet finished the migration, you can set the attribute to false to satisfy this lint check.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new RtlDetector());
}