package net.sf.persism.model;

import net.sf.persism.PropertyInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class PropertyInfoBuilder {

	private String propertyName;
	private Method getter;
	private Method setter;
	private Map<Class<? extends Annotation>, Annotation> annotations;

	public PropertyInfoBuilder() {
		annotations = new HashMap<Class<? extends Annotation>, Annotation>(4);
	}

	public PropertyInfoBuilder propertyName(String propertyName) {
		this.propertyName = propertyName;
		return this;
	}

	public PropertyInfoBuilder getter(Method getter) {
		this.getter = getter;
		return this;
	}

	public PropertyInfoBuilder setter(Method getter) {
		this.getter = getter;
		return this;
	}

	public PropertyInfoBuilder annotations(Map<Class<? extends Annotation>, Annotation> annotations) {
		this.annotations = annotations;
		return this;
	}

	public PropertyInfoBuilder annotation(Class<? extends Annotation> annotationClass, Annotation annotation) {
		annotations.put(annotationClass, annotation);
		return this;
	}

	public PropertyInfo build() {
		return new PropertyInfo(propertyName, getter, setter, annotations);
	}
}
