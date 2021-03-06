package net.sf.persism.command;

import net.sf.persism.ConnectionTypes;

import java.sql.Connection;
import java.sql.SQLException;

public class GetConnectionType {

	public static ConnectionTypes execute(Connection connection) throws SQLException {
		return ConnectionTypes.get(connection.getMetaData().getURL());
	}

}
