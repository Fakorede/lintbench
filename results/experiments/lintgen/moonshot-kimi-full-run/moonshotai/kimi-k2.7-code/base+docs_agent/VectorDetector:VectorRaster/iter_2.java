package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_FILL_TYPE;
import static com.android.SdkConstants.ATTR_STROKE_DASH_ARRAY;
import static com.android.SdkConstants.ATTR_STROKE_DASH_OFFSET;
import static com.android.SdkConstants.TAG_CLIP_PATH;
import static com.android.SdkConstants.TAG_GRADIENT;
import static com.android.SdkConstants.TAG_VECTOR;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {
    private static final int MAX_DIMENSION_DP = 200;
    private static final int VECTOR_API = 21;
    private static final int GRADIENTS_API = 24;

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector image generation",
            "When a vector drawable is used on devices running API 20 or lower, the Android Gradle "
                    + "plugin generates PNG bitmaps for backwards compatibility. Some vector "
                    + "features are not supported by this raster