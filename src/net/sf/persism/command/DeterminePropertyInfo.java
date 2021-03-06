package net.sf.persism.command;

import net.sf.persism.*;
import net.sf.persism.annotations.Column;
import net.sf.persism.annotations.NotColumn;
import net.sf.persism.command.resultset.GetColumnNames;
import net.sf.persism.model.PropertyInfoBuilder;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DeterminePropertyInfo {

	private static final Log log = Log.getLogger(DeterminePropertyInfo.class);

	public static synchronized <T> Collection<PropertyInfo> execute(Class<T> objectClass) {
		Map<String, PropertyInfoBuilder> allPropertyInfo = new HashMap<>(32);
		for (Method method : objectClass.getMethods()) {
			String methodName = method.getName().toLowerCase();
			Matcher m = Pattern.compile("^(set|is|get)").matcher(methodName);
			if (!"getClass".equalsIgnoreCase(methodName) && m.find()) {
				String propertyName = methodName.substring(m.group().length());
				PropertyInfoBuilder propertyInfoBuilder = allPropertyInfo.containsKey(propertyName) ? allPropertyInfo.get(propertyName) : new PropertyInfoBuilder();
				propertyInfoBuilder.propertyName(propertyName);
				Arrays.asList(method.getAnnotations()).forEach(annotation -> propertyInfoBuilder.annotation(annotation.annotationType(), annotation));
				if (m.group().matches("set")) {
					propertyInfoBuilder.setter(method);
				} else {
					propertyInfoBuilder.getter(method);
				}
				allPropertyInfo.put(propertyName, propertyInfoBuilder);
			}
		}

		Arrays.asList(objectClass.getDeclaredFields()).stream()
			.filter(field -> allPropertyInfo.containsKey(field.getName().toLowerCase()))
			.forEach(field -> {
				PropertyInfoBuilder propertyInfoBuilder = allPropertyInfo.get(field.getName().toLowerCase());
				Arrays.asList(field.getAnnotations()).forEach(annotation -> propertyInfoBuilder.annotation(annotation.annotationType(), annotation));
			});

		// Remove any properties found with the NoColumn annotation OR ones missing a setter (meaning they are calculated properties)
		// http://stackoverflow.com/questions/2026104/hashmap-keyset-foreach-and-remove
		return Collections.unmodifiableCollection(allPropertyInfo.values().stream()
			.map(builder -> builder.build())
			.filter(propertyInfo -> propertyInfo.getAnnotations().containsKey(NotColumn.class) || propertyInfo.getSetter() == null)
			.collect(Collectors.toList())
		);
	}

	//	TODO: done in Metadata.class, bring to here
	public static synchronized <T> Map<String, PropertyInfo> execute(Class<T> objectClass, String extraNameCharacters, ResultSet rs) throws SQLException {
		Map<String, PropertyInfo> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Collection<PropertyInfo> properties = execute(objectClass);

		List<String> realColumnNames = (List<String>) GetColumnNames.getColumnNames(rs);
		List<String> columnNames = realColumnNames.stream()
			.map(e -> e.toLowerCase().replaceAll(String.format("[%s_\\s]+", extraNameCharacters), ""))
			.collect(Collectors.toList());

		boolean propertyFound;
		for (int i = 0; i < columnNames.size(); i++) {
			propertyFound = false;
			String columnName = columnNames.get(i);
			String realColumnName = realColumnNames.get(i);
			for (PropertyInfo propertyInfo : properties) {
				if (propertyInfo.getPropertyName().toLowerCase().replace("_", "").equals(columnName) || propertyInfo.getAnnotations().containsKey(Column.class) && propertyInfo.getAnnotation(Column.class).name().equalsIgnoreCase(realColumnName)) {
					result.put(realColumnName, propertyInfo);
					propertyFound = true;
					break;
				}
			}
			if (!propertyFound) {
				log.warn(String.format("Property not found for column: %s class: %s", realColumnName, objectClass));
			}
		}
		return result;
	}

	public static synchronized <T> Map<String, PropertyInfo> execute(Class<T> objectClass, String tableName, Connection connection) {
		Map<String, PropertyInfo> result = null;
		ResultSet rs = null;
		Statement st = null;
		try {
			ConnectionTypes connectionType = GetConnectionType.execute(connection);
			String sd = connectionType.getKeywordStartDelimiter();
			String ed = connectionType.getKeywordEndDelimiter();

			st = connection.createStatement();
			// gives us real column names with case.
			String sql = String.format("SELECT * FROM %s%s%s WHERE 1=0", sd, tableName, ed);
			if (log.isDebugEnabled()) {
				log.debug("determineColumns: " + sql);
			}
			rs = st.executeQuery(sql);
			result = execute(objectClass, GetExtraNameCharacters.execute(connection), rs);
		} catch (SQLException e) {
			throw new PersismException(e.getMessage(), e);
		} finally {
			Util.cleanup(st);
			Util.cleanup(rs);
		}
		return result;
	}
}
