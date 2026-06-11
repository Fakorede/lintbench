package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.XmlUtils2;
import com.android.utils.XmlUtils2.ElementAction;
import com.google.common.collect.ImmutableList;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.List;

public class RtlDetector extends XmlScanner {
    private static final String TEXT_ALIGNMENT = "textAlignment";
    private static final String GRAVITY = "gravity";
    private static final String LAYOUT_GRAVITY = "layout_gravity";

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of("TextView", "EditText");
    }

    @Override
    public ElementAction visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr textAlignmentAttr = XmlUtils.findAttribute(element, TEXT_ALIGNMENT);
        if (textAlignmentAttr != null) {
            Attr gravityAttr = XmlUtils.findAttribute(element, GRAVITY);
            Attr layoutGravityAttr = XmlUtils.findAttribute(element, LAYOUT_GRAVITY);

            if (gravityAttr == null && layoutGravityAttr == null) {
                int apiLevel = context.getProject().getApiLevel();
                if (apiLevel < 17) {
                    String message = "textAlignment is used but gravity or layout_gravity is missing for API level below 17";
                    context.report(this, element, textAlignmentAttr,
                            Severity.ERROR, message);
                }
            }
        }

        return ElementAction.CONTINUE;
    }
}