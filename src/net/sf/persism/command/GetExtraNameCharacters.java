package net.sf.persism.command;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class GetExtraNameCharacters {

	public static String execute(Connection connection) throws SQLException {
		DatabaseMetaData dmd = connection.getMetaData();
		return dmd.getExtraNameCharacters();
	}

}
