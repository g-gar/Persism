package net.sf.persism.command;

import net.sf.persism.Log;
import org.apache.derby.impl.sql.execute.ColumnInfo;

import java.sql.Connection;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public class DetermineColumnInfo {

	/**
	 * Function to call in order to check if the wanted PropertyInfo has already been retrieved
	 */
	private final Function<Class, Collection<ColumnInfo>> fallback;
	private final Log log;

	public DetermineColumnInfo(Function<Class, Collection<ColumnInfo>> fallback, Log log) {
		this.fallback = fallback;
		this.log = log;
	}

	public synchronized <T> Map<String, net.sf.persism.ColumnInfo> execute(Class<T> objectClass, String tableName, Connection connection) {
		return null;
	}
}
