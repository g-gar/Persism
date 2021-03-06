package net.sf.persism.command.resultset;

import net.sf.persism.model.Transformation;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;

public class GetColumnNames {

	public static synchronized Collection<String> getColumnNames(ResultSet rs) throws SQLException {
		return getColumnNames(rs, e -> e);
	}

	//	Is it necessary to synchronize?
	public static synchronized Collection<String> getColumnNames(ResultSet rs, Transformation<String, String> transformation) throws SQLException {
		return getColumnNames(rs.getMetaData(), transformation);
	}

	public static synchronized Collection<String> getColumnNames(ResultSetMetaData rsmd) throws SQLException {
		return getColumnNames(rsmd, e -> e);
	}

	public static synchronized Collection<String> getColumnNames(ResultSetMetaData rsmd, Transformation<String, String> transformation) throws SQLException {
		Collection<String> result = new LinkedList<>();
		for (int i = 1; i <= rsmd.getColumnCount(); i++) {
			result.add(transformation.transform(rsmd.getColumnLabel(i)));
		}
		return result;
	}

}
