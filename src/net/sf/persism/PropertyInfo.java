package net.sf.persism;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: DHoward
 * Date: 9/8/11
 * Time: 8:09 AM
 */
public final class PropertyInfo {

    private final String propertyName;
    private final Method getter;
    private final Method setter;

    private final Map<Class<? extends Annotation>, Annotation> annotations;

    public PropertyInfo(String propertyName, Method getter, Method setter, Map<Class<? extends Annotation>, Annotation> annotations) {
        this.propertyName = propertyName;
        this.getter = getter;
        this.setter = setter;
        this.annotations = Collections.unmodifiableMap(annotations);
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Method getGetter() {
        return getter;
    }

    public Method getSetter() {
        return setter;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return (T) annotations.get(annotationClass);
    }

    public Map<Class<? extends Annotation>, Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public String toString() {
        return "PropertyInfo{" +
            "propertyName='" + propertyName + '\'' +
            ", getter=" + getter +
            ", setter=" + setter +
            ", annotations=" + annotations +
            '}';
    }
}
