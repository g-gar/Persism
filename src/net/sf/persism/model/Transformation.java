package net.sf.persism.model;

public interface Transformation<T, R> {

	R transform(T object);

}
