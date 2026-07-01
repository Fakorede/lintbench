package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Collection;
import java.util.Arrays;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    // Padding attributes
    private static final String ATTR_PADDING_LEFT = "paddingLeft";
    private static final String ATTR_PADDING_RIGHT = "paddingRight";
    private static final String ATTR_PADDING_START = "paddingStart";
    private static final String ATTR_PADDING_END = "paddingEnd";

    // Margin attributes
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT,
                ATTR_PADDING_START, ATTR_PADDING_END,
                ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT,
                ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END,
                "*"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        boolean hasPaddingLeft = false;
        boolean hasPaddingRight = false;
        boolean hasPaddingStart = false;
        boolean hasPaddingEnd = false;
        boolean hasMarginLeft = false;
        boolean hasMarginRight = false;
        boolean hasMarginStart = false;
        boolean hasMarginEnd = false;

        Attr paddingLeftAttr = null;
        Attr paddingRightAttr = null;
        Attr paddingStartAttr = null;
        Attr paddingEndAttr = null;
        Attr marginLeftAttr = null;
        Attr marginRightAttr = null;
        Attr marginStartAttr = null;
        Attr marginEndAttr = null;

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String ns = attr.getNamespaceURI();
            if (!ANDROID_NS.equals(ns)) {
                continue;
            }
            String localName = attr.getLocalName();
            if (localName == null) {
                continue;
            }
            switch (localName) {
                case ATTR_PADDING_LEFT:
                    hasPaddingLeft = true;
                    paddingLeftAttr = attr;
                    break;
                case ATTR_PADDING_RIGHT:
                    hasPaddingRight = true;
                    paddingRightAttr = attr;
                    break;
                case ATTR_PADDING_START:
                    hasPaddingStart = true;
                    paddingStartAttr = attr;
                    break;
                case ATTR_PADDING_END:
                    hasPaddingEnd = true;
                    paddingEndAttr = attr;
                    break;
                case ATTR_LAYOUT_MARGIN_LEFT:
                    hasMarginLeft = true;
                    marginLeftAttr = attr;
                    break;
                case ATTR_LAYOUT_MARGIN_RIGHT:
                    hasMarginRight = true;
                    marginRightAttr = attr;
                    break;
                case ATTR_LAYOUT_MARGIN_START:
                    hasMarginStart = true;
                    marginStartAttr = attr;
                    break;
                case ATTR_LAYOUT_MARGIN_END:
                    hasMarginEnd = true;
                    marginEndAttr = attr;
                    break;
                default:
                    break;
            }
        }

        // Check padding left/right symmetry
        if (hasPaddingLeft && !hasPaddingRight) {
            context.report(ISSUE, element, context.getLocation(paddingLeftAttr),
                    "Should specify `android:paddingRight` as well as `android:paddingLeft` " +
                    "for right-to-left layout symmetry");
        } else if (hasPaddingRight && !hasPaddingLeft) {
            context.report(ISSUE, element, context.getLocation(paddingRightAttr),
                    "Should specify `android:paddingLeft` as well as `android:paddingRight` " +
                    "for right-to-left layout symmetry");
        }

        // Check padding start/end symmetry
        if (hasPaddingStart && !hasPaddingEnd) {
            context.report(ISSUE, element, context.getLocation(paddingStartAttr),
                    "Should specify `android:paddingEnd` as well as `android:paddingStart` " +
                    "for right-to-left layout symmetry");
        } else if (hasPaddingEnd && !hasPaddingStart) {
            context.report(ISSUE, element, context.getLocation(paddingEndAttr),
                    "Should specify `android:paddingStart` as well as `android:paddingEnd` " +
                    "for right-to-left layout symmetry");
        }

        // Check margin left/right symmetry
        if (hasMarginLeft && !hasMarginRight) {
            context.report(ISSUE, element, context.getLocation(marginLeftAttr),
                    "Should specify `android:layout_marginRight` as well as " +
                    "`android:layout_marginLeft` for right-to-left layout symmetry");
        } else if (hasMarginRight && !hasMarginLeft) {
            context.report(ISSUE, element, context.getLocation(marginRightAttr),
                    "Should specify `android:layout_marginLeft` as well as " +
                    "`android:layout_marginRight` for right-to-left layout symmetry");
        }

        // Check margin start/end symmetry
        if (hasMarginStart && !hasMarginEnd) {
            context.report(ISSUE, element, context.getLocation(marginStartAttr),
                    "Should specify `android:layout_marginEnd` as well as " +
                    "`android:layout_marginStart` for right-to-left layout symmetry");
        } else if (hasMarginEnd && !hasMarginStart) {
            context.report(ISSUE, element, context.getLocation(marginEndAttr),
                    "Should specify `android:layout_marginStart` as well as " +
                    "`android:layout_marginEnd` for right-to-left layout symmetry");
        }
    }
}