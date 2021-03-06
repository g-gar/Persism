package net.sf.persism.model;

import net.sf.persism.ColumnInfo;
import net.sf.persism.Types;

public class ColumnInfoBuilder {

	private String columnName;
	private Types columnType;
	private int sqlColumnType;
	private String sqlColumnTypeName;
	private boolean autoIncrement;
	private boolean primary;
	private boolean hasDefault;
	private int length;

	public ColumnInfoBuilder() {

	}

	public ColumnInfoBuilder(ColumnInfo columnInfo) {
		columnName = columnInfo.getColumnName();
		columnType = columnInfo.getColumnType();
		sqlColumnType = columnInfo.getSqlColumnType();
		sqlColumnTypeName = columnInfo.getSqlColumnTypeName();
		autoIncrement = columnInfo.isAutoIncrement();
		primary = columnInfo.isPrimary();
		hasDefault = columnInfo.isHasDefault();
		length = columnInfo.getLength();
	}

	public ColumnInfo build() {
		return new ColumnInfo(columnName, columnType, sqlColumnType, sqlColumnTypeName, autoIncrement, primary, hasDefault, length);
	}

	public ColumnInfoBuilder columnName(String columnName) {
		this.columnName = columnName;
		return this;
	}

	public ColumnInfoBuilder columnType(Types columnType) {
		this.columnType = columnType;
		return this;
	}

	public ColumnInfoBuilder sqlColumnType(int sqlColumnType) {
		this.sqlColumnType = sqlColumnType;
		return this;
	}

	public ColumnInfoBuilder sqlColumnTypeName(String sqlColumnTypeName) {
		this.sqlColumnTypeName = sqlColumnTypeName;
		return this;
	}

	public ColumnInfoBuilder autoIncrement(boolean autoIncrement) {
		this.autoIncrement = autoIncrement;
		return this;
	}

	public ColumnInfoBuilder primary(boolean primary) {
		this.primary = primary;
		return this;
	}

	public ColumnInfoBuilder hasDefault(boolean hasDefault) {
		this.hasDefault = hasDefault;
		return this;
	}

	public ColumnInfoBuilder length(int length) {
		this.length = length;
		return this;
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
}
