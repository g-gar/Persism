package net.sf.persism;

import net.sf.persism.annotations.Column;
import net.sf.persism.annotations.Table;
import net.sf.persism.command.DeterminePropertyInfo;
import net.sf.persism.command.GetConnectionType;
import net.sf.persism.model.ColumnInfoBuilder;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.sf.persism.Util.*;

/**
 * Meta data collected in a map singleton based on connection url
 *
 * @author Dan Howard
 * @since 3/31/12 4:19 PM
 */
final class MetaData {

    private static final Log log = Log.getLogger(MetaData.class);

    // properties for each class - static because this won't change between MetaData instances
    private static final Map<Class, Collection<PropertyInfo>> propertyMap = new ConcurrentHashMap<>(32);

    // column to property map for each class
    private final Map<Class, Map<String, PropertyInfo>> propertyInfoMap = new ConcurrentHashMap<Class, Map<String, PropertyInfo>>(32);
    private final Map<Class, Map<String, ColumnInfo>> columnInfoMap = new ConcurrentHashMap<Class, Map<String, ColumnInfo>>(32);

    // table name for each class
    private final Map<Class, String> tableMap = new ConcurrentHashMap<Class, String>(32);

    // SQL for updates/inserts/deletes/selects for each class
    private final Map<Class, String> updateStatementsMap = new ConcurrentHashMap<Class, String>(32);
    private final Map<Class, String> insertStatementsMap = new ConcurrentHashMap<Class, String>(32);
    private final Map<Class, String> deleteStatementsMap = new ConcurrentHashMap<Class, String>(32);
    private final Map<Class, String> selectStatementsMap = new ConcurrentHashMap<Class, String>(32);

    // Key is SQL with named params, Value is SQL with ?
    // private Map<String, String> sqlWitNamedParams = new ConcurrentHashMap<String, String>(32);

    // Key is SQL with named params, Value list of named params
    // private Map<String, List<String>> namedParams = new ConcurrentHashMap<String, List<String>>(32);

    // private Map<Class, List<String>> primaryKeysMap = new ConcurrentHashMap<Class, List<String>>(32); // remove later maybe?

    // list of tables in the DB
    private final Set<String> tableNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    // Map of table names + meta data
    // private Map<String, TableInfo> tableInfoMap = new ConcurrentHashMap<String, TableInfo>(32);

    private static final Map<String, MetaData> metaData = new ConcurrentHashMap<String, MetaData>(4);

    ConnectionTypes connectionType;

    //the "extra" characters that can be used in unquoted identifier names (those beyond a-z, A-Z, 0-9 and _)
    private final String extraNameCharacters;

    private MetaData(Connection con) throws SQLException {

        log.debug("MetaData CREATING instance [" + this + "] ");

        connectionType = GetConnectionType.execute(con);
        if (connectionType == ConnectionTypes.Other) {
            log.warn("Unknown connection type. Please contact Persism to add support for " + con.getMetaData().getDatabaseProductName());
        }

        DatabaseMetaData dmd = con.getMetaData();
        extraNameCharacters = dmd.getExtraNameCharacters();
        populateTableList(con);
    }

    static synchronized MetaData getInstance(Connection con) throws SQLException {

        String url = con.getMetaData().getURL();
        if (metaData.get(url) == null) {
            metaData.put(url, new MetaData(con));
        }
        log.debug("MetaData getting instance " + url);
        return metaData.get(url);
    }

    private static synchronized <T> Collection<PropertyInfo> determinePropertyInfo(Class<T> objectClass) {
        if (propertyMap.containsKey(objectClass)) {
            return propertyMap.get(objectClass);
        }
        Collection<PropertyInfo> properties = DeterminePropertyInfo.execute(objectClass);
        propertyMap.put(objectClass, properties);
        return properties;
    }

    // Should only be called IF the map does not contain the column meta information yet.
    // Version for Tables
    private synchronized <T> Map<String, PropertyInfo> determinePropertyInfo(Class<T> objectClass, String tableName, Connection connection) {
        // double check map
        if (propertyInfoMap.containsKey(objectClass)) {
            return propertyInfoMap.get(objectClass);
        }

        return DeterminePropertyInfo.execute(objectClass, tableName, connection);
    }

    // Should only be called IF the map does not contain the column meta information yet.
    // Version for Queries
//    TODO: TEST
    private synchronized <T> Map<String, PropertyInfo> determinePropertyInfo(Class<T> objectClass, ResultSet rs) {
        // double check map - note this could be called with a Query were we never have that in here
        if (propertyInfoMap.containsKey(objectClass)) {
            return propertyInfoMap.get(objectClass);
        }
        Map<String, PropertyInfo> result = null;
        try {
            result = DeterminePropertyInfo.execute(objectClass, extraNameCharacters, rs);
            propertyInfoMap.put(objectClass, result);
        } catch (SQLException e) {
            throw new PersismException(e.getMessage(), e);
        }
        return result;
    }

    static <T> Collection<PropertyInfo> getPropertyInfo(Class<T> objectClass) {
        if (propertyMap.containsKey(objectClass)) {
            return propertyMap.get(objectClass);
        }
        return determinePropertyInfo(objectClass);
    }

    private synchronized <T> Map<String, ColumnInfo> determineColumnInfo(Class<T> objectClass, String tableName, Connection connection) {
        if (columnInfoMap.containsKey(objectClass)) {
            return columnInfoMap.get(objectClass);
        }

        Statement st = null;
        ResultSet rs = null;
        Map<String, PropertyInfo> properties = getTableColumnsPropertyInfo(objectClass, connection);
        String sd = connectionType.getKeywordStartDelimiter();
        String ed = connectionType.getKeywordEndDelimiter();

        try {

            st = connection.createStatement();
            rs = st.executeQuery(String.format("SELECT  * FROM %s%s%s WHERE 1=0", sd, tableName, ed));
            // Make sure primary keys sorted by column order in case we have more than 1
            // then we'll know the order to apply the parameters.
            Map<String, ColumnInfo> map = new LinkedHashMap<>(32);

            st = connection.createStatement();
            rs = st.executeQuery("SELECT * FROM " + sd + tableName + ed + " WHERE 1=0");

            // Make sure primary keys sorted by column order in case we have more than 1
            // then we'll know the order to apply the parameters.
            map = new LinkedHashMap<>(32);

            ResultSetMetaData rsmd = rs.getMetaData();
            ColumnInfoBuilder builder;
            PropertyInfo propertyInfo;
            Column columnAnnotation;
            Types columnType;
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                if (properties.containsKey(rsmd.getColumnLabel(i))) {
                    columnType = Types.convert(rsmd.getColumnType(i));
                    builder = new ColumnInfoBuilder()
                        .columnName(rsmd.getColumnLabel(i))
                        .autoIncrement(rsmd.isAutoIncrement(i))
                        .primary(rsmd.isAutoIncrement(i))
                        .sqlColumnType(rsmd.getColumnType(i))
                        .sqlColumnTypeName(rsmd.getColumnTypeName(i))
                        .columnType(columnType)
                        .length(rsmd.getColumnDisplaySize(i));

                    propertyInfo = properties.get(rsmd.getColumnLabel(i));
                    if (propertyInfo.getAnnotations().containsKey(Column.class)) {
                        columnAnnotation = propertyInfo.getAnnotation(Column.class);
                        builder.hasDefault(columnAnnotation.hasDefault())
                            .primary(columnAnnotation.primary())
                            .autoIncrement(columnAnnotation.autoIncrement() && columnType.isCountable());

                        if (columnAnnotation.autoIncrement() && !columnType.isCountable()) {
                            log.warn(String.format("Column %s is annotated as auto-increment but is a non-numeric type (%s) - Ignoring.", rsmd.getColumnLabel(i), columnType));
                        }
                    }

                    map.put(rsmd.getColumnLabel(i), builder.build());
                }
            }
            rs.close();

            DatabaseMetaData dmd = connection.getMetaData();

            /*
             Get columns from database metadata since we don't get Type from resultSetMetaData
             with SQLite. + We also need to know if there's a default on a column.
             */
            rs = dmd.getColumns(null, connectionType.getSchemaPattern(), tableName, null);
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                ColumnInfo columnInfo = map.get(columnName);

                if (columnInfo != null) {
                    builder = new ColumnInfoBuilder(map.get(columnName));
                    if (!builder.isHasDefault()) {
                        builder.hasDefault(containsColumn(rs, "COLUMN_DEF") && rs.getString("COLUMN_DEF") != null);
                    }
                    // Do we not have autoinc info here? Yes.
                    // IS_AUTOINCREMENT = NO or YES
                    if (!builder.isAutoIncrement()) {
                        builder.autoIncrement(containsColumn(rs, "IS_AUTOINCREMENT") && "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")));
                    }
                    // Re-assert the type since older version of SQLite could not detect types with empty resultsets
                    // It seems OK now in the newer JDBC driver.
                    // See testTypes unit test in TestSQLite
                    if (containsColumn(rs, "DATA_TYPE")) {
                        builder.sqlColumnType(rs.getInt("DATA_TYPE"));
                        if (containsColumn(rs, "TYPE_NAME")) {
                            builder.sqlColumnTypeName(rs.getString("TYPE_NAME"));
                        }
                        builder.columnType(Types.convert(builder.getSqlColumnType()));
                    }
                    map.put(columnName, builder.build());
                }
            }
            rs.close();

            // Iterate primary keys and update column infos
            rs = dmd.getPrimaryKeys(null, connectionType.getSchemaPattern(), tableName);
            int pkCount = 0;
            while (rs.next()) {
                ColumnInfo columnInfo = map.get(rs.getString("COLUMN_NAME"));
                if (columnInfo != null) {
                    builder = new ColumnInfoBuilder(columnInfo)
                        .primary(true);
                    map.put(columnInfo.getColumnName(), builder.build());
                    pkCount++;
                }
            }

            if (pkCount == 0) {
                // Should we fail-fast? Actually no, we should not fail here.
                // It's very possible the user has a table that they will never
                // update, delete or select (by primary).
                // They may only want to do read operations with specified queries and in that
                // context we don't need any primary keys. (same with insert)
                log.warn(String.format("No primary key found for table %s. Do not use with update/delete/fetch or add a primary key.", tableName));
            }

            columnInfoMap.put(objectClass, map);

            return map;

        } catch (SQLException e) {
            throw new PersismException(e.getMessage(), e);
        } finally {
            cleanup(st, rs);
        }
    }


    private static final String[] tableTypes = {"TABLE"};

    // Populates the tables list with table names from the DB.
    // This list is used for discovery of the table name from a class.
    // ONLY to be called from Init in a synchronized way.
    private void populateTableList(Connection con) throws PersismException {

        ResultSet rs = null;

        try {
            // NULL POINTER WITH
            // http://social.msdn.microsoft.com/Forums/en-US/sqldataaccess/thread/5c74094a-8506-4278-ac1c-f07d1bfdb266
            // solution:
            // http://stackoverflow.com/questions/8988945/java7-sqljdbc4-sql-error-08s01-on-getconnection

            rs = con.getMetaData().getTables(null, connectionType.getSchemaPattern(), null, tableTypes);
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }

        } catch (SQLException e) {
            throw new PersismException(e.getMessage(), e);

        } finally {
            cleanup(null, rs);
        }
    }

    /**
     * @param object
     * @param connection
     * @return sql update string
     * @throws NoChangesDetectedForUpdateException if the data object implements Persistable and there are no changes detected
     */
    String getUpdateStatement(Object object, Connection connection) throws PersismException, NoChangesDetectedForUpdateException {

        if (object instanceof Persistable) {
            Map<String, PropertyInfo> changes = getChangedProperties((Persistable) object, connection);
            if (changes.size() == 0) {
                throw new NoChangesDetectedForUpdateException();
            }
            // Note we don't not add Persistable updates to updateStatementsMap since they will be different each time.
            String sql = buildUpdateString(object, changes.keySet().iterator(), connection);
            if (log.isDebugEnabled()) {
                log.debug("getUpdateStatement for " + object.getClass() + " for changed fields is " + sql);
            }
            return sql;
        }

        if (updateStatementsMap.containsKey(object.getClass())) {
            return updateStatementsMap.get(object.getClass());
        }

        return determineUpdateStatement(object, connection);
    }

    // Used by Objects not implementing Persistable since they will always use the same update statement
    private synchronized String determineUpdateStatement(Object object, Connection connection) {
        if (updateStatementsMap.containsKey(object.getClass())) {
            return updateStatementsMap.get(object.getClass());
        }

        Map<String, PropertyInfo> columns = getTableColumnsPropertyInfo(object.getClass(), connection);

        String updateStatement = buildUpdateString(object, columns.keySet().iterator(), connection);

        // Store static update statement for future use.
        updateStatementsMap.put(object.getClass(), updateStatement);

        if (log.isDebugEnabled()) {
            log.debug("determineUpdateStatement for " + object.getClass() + " is " + updateStatement);
        }

        return updateStatement;
    }


    // Note this will not include columns unless they have the associated property.
    String getInsertStatement(Object object, Connection connection) throws PersismException {
        if (insertStatementsMap.containsKey(object.getClass())) {
            return insertStatementsMap.get(object.getClass());
        }
        return determineInsertStatement(object, connection);
    }

    //    TODO
    private synchronized String determineInsertStatement(Object object, Connection connection) {
        if (insertStatementsMap.containsKey(object.getClass())) {
            return insertStatementsMap.get(object.getClass());
        }

        try {
            String tableName = getTableName(object.getClass(), connection);
            String sd = connectionType.getKeywordStartDelimiter();
            String ed = connectionType.getKeywordEndDelimiter();

            Map<String, ColumnInfo> columns = getColumns(object.getClass(), connection);
            Map<String, PropertyInfo> properties = getTableColumnsPropertyInfo(object.getClass(), connection);
            StringBuilder sb = new StringBuilder();
            sb.append("INSERT INTO ").append(sd).append(tableName).append(ed).append(" (");
            String sep = "";
            boolean saveInMap = true;

            for (ColumnInfo column : columns.values()) {
                if (!column.isAutoIncrement()) {

                    if (column.isHasDefault()) {

                        saveInMap = false;

                        // Do not include if this column has a default and no value has been
                        // set on it's associated property.
                        if (properties.get(column.getColumnName()).getGetter().invoke(object) == null) {
                            continue;
                        }

                    }
                    sb.append(sep).append(sd).append(column.getColumnName()).append(ed);
                    sep = ", ";
                }
            }
            sb.append(") VALUES (");
            sep = "";
            for (ColumnInfo column : columns.values()) {
                if (!column.isAutoIncrement()) {

                    if (column.isHasDefault()) {
                        // Do not include if this column has a default and no value has been
                        // set on it's associated property.
                        if (properties.get(column.getColumnName()).getGetter().invoke(object) == null) {
                            continue;
                        }
                    }

                    sb.append(sep).append(" ? ");
                    sep = ", ";
                }
            }
            sb.append(") ");

            String insertStatement;
            insertStatement = sb.toString();

            if (log.isDebugEnabled()) {
                log.debug("determineInsertStatement for " + object.getClass() + " is " + insertStatement);
            }

            // Do not put this insert statement into the map if any columns have defaults
            // because the insert statement will vary by different instances of the data object.
            if (saveInMap) {
                insertStatementsMap.put(object.getClass(), insertStatement);
            } else {
                insertStatementsMap.remove(object.getClass()); // remove just in case
            }

            return insertStatement;

        } catch (Exception e) {
            throw new PersismException(e.getMessage(), e);
        }
    }

    String getDeleteStatement(Object object, Connection connection) {
        if (deleteStatementsMap.containsKey(object.getClass())) {
            return deleteStatementsMap.get(object.getClass());
        }
        return determineDeleteStatement(object, connection);
    }

    private synchronized String determineDeleteStatement(Object object, Connection connection) {
        if (deleteStatementsMap.containsKey(object.getClass())) {
            return deleteStatementsMap.get(object.getClass());
        }

        String tableName = getTableName(object.getClass(), connection);
        String sd = connectionType.getKeywordStartDelimiter();
        String ed = connectionType.getKeywordEndDelimiter();

        List<String> primaryKeys = getPrimaryKeys(object.getClass(), connection);

        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ").append(sd).append(tableName).append(ed).append(" WHERE ");
        String sep = "";
        for (String column : primaryKeys) {
            sb.append(sep).append(sd).append(column).append(ed).append(" = ?");
            sep = " AND ";
        }

        String deleteStatement = sb.toString();

        if (log.isDebugEnabled()) {
            log.debug("determineDeleteStatement for " + object.getClass() + " is " + deleteStatement);
        }

        deleteStatementsMap.put(object.getClass(), deleteStatement);

        return deleteStatement;
    }

    String getSelectStatement(Object object, Connection connection) {
        if (selectStatementsMap.containsKey(object.getClass())) {
            return selectStatementsMap.get(object.getClass());
        }
        return determineSelectStatement(object, connection);
    }

    private synchronized String determineSelectStatement(Object object, Connection connection) {

        if (selectStatementsMap.containsKey(object.getClass())) {
            return selectStatementsMap.get(object.getClass());
        }

        String sd = connectionType.getKeywordStartDelimiter();
        String ed = connectionType.getKeywordEndDelimiter();

        String tableName = getTableName(object.getClass(), connection);

        List<String> primaryKeys = getPrimaryKeys(object.getClass(), connection);

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");

        String sep = "";

        Map<String, ColumnInfo> columns = getColumns(object.getClass(), connection);
        for (String column : columns.keySet()) {
            ColumnInfo columnInfo = columns.get(column);
            sb.append(sep).append(sd).append(columnInfo.getColumnName()).append(ed);
            sep = ", ";
        }
        sb.append(" FROM ").append(sd).append(tableName).append(ed).append(" WHERE ");

        sep = "";
        for (String column : primaryKeys) {
            sb.append(sep).append(sd).append(column).append(ed).append(" = ?");
            sep = " AND ";
        }

        String selectStatement = sb.toString();

        if (log.isDebugEnabled()) {
            log.debug("determineSelectStatement for " + object.getClass() + " is " + selectStatement);
        }

        selectStatementsMap.put(object.getClass(), selectStatement);

        return selectStatement;
    }

    private String buildUpdateString(Object object, Iterator<String> it, Connection connection) throws PersismException {
        // todo STUPID UPDATE STATEMENT IS IN ALPHABETICAL ORDER FFS

        String tableName = getTableName(object.getClass(), connection);
        String sd = connectionType.getKeywordStartDelimiter();
        String ed = connectionType.getKeywordEndDelimiter();

        List<String> primaryKeys = getPrimaryKeys(object.getClass(), connection);

        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(sd).append(tableName).append(ed).append(" SET ");
        String sep = "";

        Map<String, ColumnInfo> columns = getColumns(object.getClass(), connection);
        while (it.hasNext()) {
            String column = it.next();
            ColumnInfo columnInfo = columns.get(column);
            if (!columnInfo.isAutoIncrement() && !columnInfo.isPrimary()) {
                sb.append(sep).append(sd).append(column).append(ed).append(" = ?");
                sep = ", ";
            }
        }
        sb.append(" WHERE ");
        sep = "";
        for (String column : primaryKeys) {
            sb.append(sep).append(sd).append(column).append(ed).append(" = ?");
            sep = " AND ";
        }
        return sb.toString();
    }

    Map<String, PropertyInfo> getChangedProperties(Persistable persistable, Connection connection) throws PersismException {

        try {
            Persistable original = (Persistable) persistable.getOriginalValue();

            Map<String, PropertyInfo> columns = getTableColumnsPropertyInfo(persistable.getClass(), connection);

            if (original == null) {
                // Could happen in the case of cloning or other operation - so it's never read so it never sets original.
                return columns;
            } else {
                Map<String, PropertyInfo> changedColumns = new HashMap<>(columns.keySet().size());
                for (String column : columns.keySet()) {

                    PropertyInfo propertyInfo = columns.get(column);

                    Object newValue = null;
                    Object orgValue = null;
                    newValue = propertyInfo.getGetter().invoke(persistable);
                    orgValue = propertyInfo.getGetter().invoke(original);

                    if (newValue != null && !newValue.equals(orgValue) || orgValue != null && !orgValue.equals(newValue)) {
                        changedColumns.put(column, propertyInfo);
                    }
                }
                return changedColumns;
            }

        } catch (Exception e) {
            throw new PersismException(e.getMessage(), e);
        }
    }

    <T> Map<String, ColumnInfo> getColumns(Class<T> objectClass, Connection connection) throws PersismException {
        // Realistically at this point this objectClass will always be in the map since it's defined early
        // when we get the table name but I'll double check it for determineColumnInfo anyway.
        if (columnInfoMap.containsKey(objectClass)) {
            return columnInfoMap.get(objectClass);
        }
        return determineColumnInfo(objectClass, getTableName(objectClass), connection);
    }

    <T> Map<String, PropertyInfo> getQueryColumnsPropertyInfo(Class<T> objectClass, ResultSet rs) throws PersismException {
        if (propertyInfoMap.containsKey(objectClass)) {
            return propertyInfoMap.get(objectClass);
        }

        return determinePropertyInfo(objectClass, rs);
    }

    <T> Map<String, PropertyInfo> getTableColumnsPropertyInfo(Class<T> objectClass, Connection connection) throws PersismException {
        if (propertyInfoMap.containsKey(objectClass)) {
            return propertyInfoMap.get(objectClass);
        }
        return determinePropertyInfo(objectClass, getTableName(objectClass), connection);
    }

    <T> String getTableName(Class<T> objectClass) {

        if (tableMap.containsKey(objectClass)) {
            return tableMap.get(objectClass);
        }

        return determineTable(objectClass);
    }

    // internal version to retrieve meta information about this table's columns
    // at the same time we find the table name itself.
    private <T> String getTableName(Class<T> objectClass, Connection connection) {

        String tableName = getTableName(objectClass);

        if (!columnInfoMap.containsKey(objectClass)) {
            determineColumnInfo(objectClass, tableName, connection);
        }

        if (!propertyInfoMap.containsKey(objectClass)) {
            determinePropertyInfo(objectClass, tableName, connection);
        }
        return tableName;
    }

    private synchronized <T> String determineTable(Class<T> objectClass) {

        if (tableMap.containsKey(objectClass)) {
            return tableMap.get(objectClass);
        }

        String tableName;
        Table annotation = objectClass.getAnnotation(Table.class);
        if (annotation != null) {
            tableName = annotation.value();
        } else {
            tableName = guessTableName(objectClass);
        }
        tableMap.put(objectClass, tableName);
        return tableName;
    }

    // Returns the table name found in the DB in the same case as in the DB.
    // throws PersismException if we cannot guess any table name for this class.
    private <T> String guessTableName(Class<T> objectClass) throws PersismException {
        Set<String> guesses = new LinkedHashSet<>(6); // guess order is important
        List<String> guessedTables = new ArrayList<String>(6);

        String className = objectClass.getSimpleName();

        addTableGuesses(className, guesses);
        for (String tableName : tableNames) {
            for (String guess : guesses) {
                if (guess.equalsIgnoreCase(tableName)) {
                    guessedTables.add(tableName);
                }
            }
        }
        if (guessedTables.size() == 0) {
            throw new PersismException("Could not determine a table for type: " + objectClass.getName() + " Guesses were: " + guesses);
        }

        if (guessedTables.size() > 1) {
            throw new PersismException("Could not determine a table for type: " + objectClass.getName() + " Guesses were: " + guesses + " and we found multiple matching tables: " + guessedTables);
        }
        return guessedTables.get(0);
    }

    private void addTableGuesses(String className, Collection<String> guesses) {
        // PascalCasing class name should make
        // PascalCasing
        // PascalCasings
        // Pascal Casing
        // Pascal Casings
        // Pascal_Casing
        // Pascal_Casings
        // Order is important.

        String guess;
        String pluralClassName;

        if (className.endsWith("y")) {
            pluralClassName = className.substring(0, className.length() - 1) + "ies";
        } else {
            pluralClassName = className + "s";
        }

        guesses.add(className);
        guesses.add(pluralClassName);

        guess = camelToTitleCase(className);
        guesses.add(guess); // name with spaces
        guesses.add(replaceAll(guess, ' ', '_')); // name with spaces changed to _

        guess = camelToTitleCase(pluralClassName);
        guesses.add(guess); // plural name with spaces
        guesses.add(replaceAll(guess, ' ', '_')); // plural name with spaces changed to _
    }

    List<String> getPrimaryKeys(Class<?> objectClass, Connection connection) throws PersismException {

        // ensures meta data will be available
        String tableName = getTableName(objectClass, connection);

        List<String> primaryKeys = new ArrayList<>(4);
        Map<String, ColumnInfo> map = getColumns(objectClass, connection);
        for (ColumnInfo col : map.values()) {
            if (col.isPrimary()) {
                primaryKeys.add(col.getColumnName());
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("getPrimaryKeys for " + tableName + " " + primaryKeys);
        }
        return primaryKeys;
    }
}
