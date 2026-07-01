package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public abstract class Detector {
    ...
    public void beforeCheckProject(@NonNull Context context) { }
    public void beforeCheckLibraryProject(@NonNull Context context) { }
    public void afterCheckProject(@NonNull Context context) { }
    public void afterCheckLibraryProject(@NonNull Context context) { }
    ...
}