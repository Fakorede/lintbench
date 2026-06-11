package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.resources.ScreenSize;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRound;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.ILogger;
import com.android.utils.PositionXmlParser;
import com.android.utils.XmlPullAttributes;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

public class AndroidTvDetector extends ManifestDetector {

    private static final String USES_FEATURE = "uses-feature";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String LEANBACK_NAME = "android.software.leanback";
    private static final String REQUIRED_ATTRIBUTE = "required";

    @Override
    public int getApplicableElements() {
        return TYPE_MANIFEST;
    }

    @Nullable
    @Override
    public List<String> getExpectedErrors() {
        return ImmutableList.of("MissingLeanbackSupport");
    }

    @NonNull
    @Override
    protected IssueRegistry.Issue getIssue() {
        return ISSUE_MISSING_LEANBACK_SUPPORT;
    }

    private static final Issue ISSUE_MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "The manifest should declare the use of the Leanback user interface required by Android TV.",
            "To fix this, add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to your manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Nullable
    @Override
    public List<Pair<Integer, String>> checkManifest(@NonNull Element manifestRoot) {
        NodeList usesFeatures = XmlUtils.getElementsByTagName(manifestRoot, USES_FEATURE);
        boolean leanbackFeatureFound = false;

        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Node node = usesFeatures.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element featureElement = (Element) node;
                Attr nameAttr = featureElement.getAttributeNode(NAME_ATTRIBUTE);
                if (nameAttr != null && LEANBACK_NAME.equals(nameAttr.getValue())) {
                    leanbackFeatureFound = true;
                    break;
                }
            }
        }

        if (!leanbackFeatureFound) {
            return ImmutableList.of(new Pair<>(manifestRoot.getLineNumber(),
                    "The manifest should declare the use of the Leanback user interface required by Android TV. "
                            + "To fix this, add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to your manifest."));
        }

        return null;
    }
}