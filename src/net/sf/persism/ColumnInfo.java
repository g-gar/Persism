package net.sf.persism;

/**
 * Information about columns used for insert, update and delete.
 * Queries do not use this object.
 *
 * @author Dan Howard
 * @since 5/4/12 6:22 AM
 */
public final class ColumnInfo {

	private final String columnName;

	// SQLite - Date - comes back as StringType
	// H2 - BIT - comes back NULL
	private final Types columnType;

	// Currently just kept for possible future use
	private final int sqlColumnType;
	private final String sqlColumnTypeName;

	// indicates this column is generated. Only for Auto-Inc for now
	private final boolean autoIncrement;

	// Indicates this is primary key column
	private final boolean primary;

	private final boolean hasDefault;

	private final int length; // for string to varchar length checking

	public ColumnInfo(String columnName, Types columnType, int sqlColumnType, String sqlColumnTypeName, boolean autoIncrement, boolean primary, boolean hasDefault, int length) {
		this.columnName = columnName;
		this.columnType = columnType;
		this.sqlColumnType = sqlColumnType;
		this.sqlColumnTypeName = sqlColumnTypeName;
		this.autoIncrement = autoIncrement;
		this.primary = primary;
		this.hasDefault = hasDefault;
		this.length = length;
	}

	public String getColumnName() {
		return columnName;
	}

	public Types getColumnType() {
		return columnType;
	}

	public int getSqlColumnType() {
		return sqlColumnType;
	}

	public String getSqlColumnTypeName() {
		return sqlColumnTypeName;
	}

	public boolean isAutoIncrement() {
		return autoIncrement;
	}

	public boolean isPrimary() {
		return primary;
	}

	public boolean isHasDefault() {
		return hasDefault;
	}

	public int getLength() {
		return length;
	}

	@Override
	public String toString() {
		return "ColumnInfo{" +
			"columnName='" + columnName + '\'' +
			", columnType=" + columnType +
			", sqlColumnType=" + sqlColumnType +
			", sqlColumnTypeName=" + sqlColumnTypeName +
			", autoIncrement=" + autoIncrement +
			", primary=" + primary +
                ", hasDefault=" + hasDefault +
                ", length=" + length +
                '}';
    }
}
