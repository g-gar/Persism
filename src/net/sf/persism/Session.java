package net.sf.persism;

import net.sf.persism.annotations.QueryResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Performs various read and write operations in the database.
 * @author Dan Howard
 * @since 1/8/2021
 */
public class Session {

    private static final Log log = Log.getLogger(Session.class);

    private Connection connection;

    private MetaData metaData;

    /**
     * @param connection db connection
     * @throws PersismException if something goes wrong
     */
    public Session(Connection connection) throws PersismException {
        this.connection = connection;
        init(connection);
    }

    private void init(Connection connection) {

        // place any DB specific properties here.
        try {
            metaData = MetaData.getInstance(connection);
        } catch (SQLException e) {
            throw new PersismException(e);
        }
    }

    /**
     * Updates the data object in the database.
     *
     * @param object data object to update.
     * @return usually 1 to indicate rows changed via JDBC.
     * @throws PersismException Indicating the upcoming robot uprising.
     */
    public int update(Object object) throws PersismException {
        List<String> primaryKeys = metaData.getPrimaryKeys(object.getClass(), connection);
        if (primaryKeys.size() == 0) {
            throw new PersismException("Cannot perform update. " + metaData.getTableName(object.getClass()) + " has no primary keys.");
        }

        PreparedStatement st = null;
        try {

            String updateStatement = metaData.getUpdateStatement(object, connection);
            if (updateStatement == null || updateStatement.trim().length() == 0) {
                log.warn("No properties changed. No update required for Object: " + object + " class: " + object.getClass().getName());
                return 0;
            }

            st = connection.prepareStatement(updateStatement);

            // These keys should always be in sorted order.
            Map<String, PropertyInfo> allProperties = metaData.getTableColumns(object.getClass(), connection);
            Map<String, PropertyInfo> changedProperties;
            if (object instanceof Persistable) {
                changedProperties = metaData.getChangedColumns((Persistable) object, connection);
            } else {
                changedProperties = allProperties;
            }

            List<Object> params = new ArrayList<Object>(primaryKeys.size());
            for (String column : changedProperties.keySet()) {
                if (!primaryKeys.contains(column)) {
                    Object value = allProperties.get(column).getter.invoke(object);

                    if (value != null) {
                        if (value.getClass().isEnum()) {
                            value = "" + value; // convert enum to string.
                        }

                        if (value instanceof java.util.Date) {
                            java.util.Date dt = (java.util.Date) value;
                            value = new Timestamp(dt.getTime());
                        }

                        if (value instanceof String) {
                            // check width
                            PropertyInfo propertyInfo = allProperties.get(column);
                            String str = (String) value;
                            if (str.length() > propertyInfo.length) {
                                str = str.substring(0, propertyInfo.length);
                                // todo Should Persism strict should throw this as an exception?
                                log.warn("TRUNCATION with Column: " + column + " for table: " + metaData.getTableName(object.getClass()) + ". Old value: \"" + value + "\" New value: \"" + str + "\"");
                                value = str;
                            }
                        }
                    }
                    params.add(value);
                }
            }

            for (String column : primaryKeys) {
                params.add(allProperties.get(column).getter.invoke(object));
            }
            Util.setParameters(st, params.toArray());
            int ret = st.executeUpdate();
            return ret;

        } catch (Exception e) {
            Util.rollback(connection);
            throw new PersismException(e);

        } finally {
            Util.cleanup(st, null);
        }
    }

    /**
     * Inserts the data object in the database.
     * The data object is refreshed with autoinc and other defaults that may exist.
     *
     * @param object the data object to insert.
     * @return usually 1 to indicate rows changed via JDBC.
     * @throws PersismException When planet of the apes starts happening.
     */
    public int insert(Object object) throws PersismException {
        String insertStatement = metaData.getInsertStatement(object, connection);

        PreparedStatement st = null;
        ResultSet rs = null;

        try {
            // These keys should always be in sorted order.
            Map<String, PropertyInfo> properties = metaData.getTableColumns(object.getClass(), connection);
            Map<String, ColumnInfo> columns = metaData.getColumns(object.getClass(), connection);

            List<String> generatedKeys = new ArrayList<String>(4);
            for (ColumnInfo column : columns.values()) {
                if (column.generated) {
                    generatedKeys.add(column.columnName);
                }
            }

            if (generatedKeys.size() > 0) {
                String[] keyArray = generatedKeys.toArray(new String[0]);
                st = connection.prepareStatement(insertStatement, keyArray);
            } else {
                st = connection.prepareStatement(insertStatement);
            }

            boolean tableHasDefaultColumnValues = false;

            List<Object> params = new ArrayList<Object>();
            for (ColumnInfo column : columns.values()) {

                PropertyInfo propertyInfo = properties.get(column.columnName);

                // todo is propertyInfo null ever? I don't think so. We only include columns where we know the property.
                if (!column.generated) {

                    // TODO This condition is repeated 3 times. We need to rearrange this code.
                    // See MetaData getInsertStatement - Maybe we should return a new Object type for InsertStatement
                    if (column.hasDefault) {
                        // Do not include if this column has a default and no value has been
                        // set on it's associated property.
                        if (properties.get(column.columnName).getter.invoke(object) == null) {
                            tableHasDefaultColumnValues = true;
                            continue;
                        }
                    }


                    Object value = propertyInfo.getter.invoke(object);

                    if (value != null) {

                        if (value.getClass().isEnum()) {
                            value = "" + value; // convert enum to string.
                        }

                        // sql.Date is a subclass so this would be true
                        if (value instanceof java.util.Date) {
                            java.util.Date dt = (java.util.Date) value;
                            value = new Timestamp(dt.getTime());
                        }


                        if (value instanceof String) {
                            // check width
                            String str = (String) value;
                            if (str.length() > propertyInfo.length) {
                                // todo should Persism strict throw this as an exception?
                                str = str.substring(0, propertyInfo.length);
                                log.warn("TRUNCATION with Column: " + column + " for table: " + metaData.getTableName(object.getClass()) + ". Old value: \"" + value + "\" New value: \"" + str + "\"");
                                value = str;
                            }
                        }
                    }
                    params.add(value);
                }
            }

            // https://forums.oracle.com/forums/thread.jspa?threadID=879222
            // http://download.oracle.com/javase/1.4.2/docs/guide/jdbc/getstart/statement.html
            //int ret = st.executeUpdate(insertStatement, Statement.RETURN_GENERATED_KEYS);
            Util.setParameters(st, params.toArray());
            int ret = st.executeUpdate();
            if (log.isDebugEnabled()) {
                log.debug("insert ret: " + ret);
            }
            if (generatedKeys.size() > 0) {
                rs = st.getGeneratedKeys();
            }

            // TODO for now we can only support a single auto inc - need to test out other possible generated columns
            for (String column : generatedKeys) {
                if (rs.next()) {

                    Method setter = properties.get(column).setter;

                    if (setter != null) {
                        // todo do we really need to type these? Maybe if the DB uses a GUID?
                        Object value = metaData.getTypedValue(setter.getParameterTypes()[0], rs, 1);
                        if (log.isDebugEnabled()) {
                            log.debug(column + " generated " + value);
                            log.debug(setter);
                        }
                        setter.invoke(object, value);

                    } else {
                        log.warn("no setter found for column " + column);
                    }
                }
            }

            if (tableHasDefaultColumnValues) {
                // Read the full object back to update any properties which had defaults
                fetch(object);
            }

            return ret;
        } catch (Exception e) {
            Util.rollback(connection);
            throw new PersismException(e);
        } finally {
            Util.cleanup(st, rs);
        }
    }


    /**
     * Deletes the data object object from the database.
     *
     * @param object data object to delete
     * @return usually 1 to indicate rows changed via JDBC.
     * @throws PersismException Perhaps when asteroid 1999 RQ36 hits us?
     */
    public int delete(Object object) throws PersismException {

        List<String> primaryKeys = metaData.getPrimaryKeys(object.getClass(), connection);
        if (primaryKeys.size() == 0) {
            throw new PersismException("Cannot perform delete. " + metaData.getTableName(object.getClass()) + " has no primary keys.");
        }

        PreparedStatement st = null;
        try {
            String deleteStatement = metaData.getDeleteStatement(object, connection);
            st = connection.prepareStatement(deleteStatement);

            // These keys should always be in sorted order.
            Map<String, PropertyInfo> columns = metaData.getTableColumns(object.getClass(), connection);

            List<Object> params = new ArrayList<Object>(primaryKeys.size());
            for (String column : primaryKeys) {
                params.add(columns.get(column).getter.invoke(object));
            }
            Util.setParameters(st, params.toArray());
            int ret = st.executeUpdate();
            return ret;

        } catch (Exception e) {
            Util.rollback(connection);
            throw new PersismException(e);

        } finally {
            Util.cleanup(st, null);
        }
    }

    /**
     * Execute an arbitrary SQL statement.
     *
     * @param sql sql string
     * @param parameters parameters
     */
    public void execute(String sql, Object... parameters) {

        Statement st = null;

        try {

            if (parameters.length == 0) {
                st = connection.createStatement();
                st.execute(sql);
            } else {
                st = connection.prepareStatement(sql);
                PreparedStatement pst = (PreparedStatement) st;
                Util.setParameters(pst, parameters);
                pst.execute();
            }

        } catch (SQLException e) {
            throw new PersismException(e);
        } finally {
            Util.cleanup(st, null);
        }
    }

    /**
     * Query for a list of objects of the specified class using the specified SQL query and parameters.
     * The type of the list can be Data Objects or native Java Objects or primitives.
     *
     * @param objectClass class of objects to return.
     * @param sql         query string to execute.
     * @param parameters  parameters to the query.
     * @param <T> Return type
     * @return a list of objects of the specified class using the specified SQL query and parameters.
     * @throws PersismException If something goes wrong you get a big stack trace.s
     */
    public <T> List<T> query(Class<T> objectClass, String sql, Object... parameters) throws PersismException {
        List<T> list = new ArrayList<T>(32);

        Result result = new Result();

        // If we know this type it means it's a primitive type. Not a DAO so we use a different rule to read those
        boolean readPrimitive = Types.getType(objectClass) != null;

        if (!readPrimitive && objectClass.getAnnotation(QueryResult.class) == null) {
            metaData.getTableColumns(objectClass, connection); // TODO Make sure columns are initialized properly if this is a table WHY?
        }

        try {

            exec(result, sql, parameters);

            while (result.rs.next()) {

                if (readPrimitive) {
                    list.add((T) readPrimitive(result.rs, 1, objectClass));
                } else {
                    // should be getDeclaredConstructor().newInstance() now.
                    T t = objectClass.newInstance();
                    t = (T) readObject(t, result.rs);
                    list.add(t);
                }
            }

        } catch (IllegalAccessException | InstantiationException | InvocationTargetException | IOException e) {
            throw new PersismException(e);
        } catch (SQLException e) {
            Util.rollback(connection);
            throw new PersismException(e);
        } finally {
            Util.cleanup(result.st, result.rs);
        }

        return list;

    }

    /**
     * Fetch an object from the database by it's primary key(s).
     * You should instantiate the object and set the primary key properties before calling this method.
     *
     * @param object Data object to read from the database.
     * @return true if the object was found by the primary key.
     * @throws PersismException if something goes wrong.
     */
    public boolean fetch(Object object) throws PersismException {
        Class objectClass = object.getClass();

        // If we know this type it means it's a primitive type. This method cannot be used for primitives
        boolean readPrimitive = Types.getType(objectClass) != null;
        if (readPrimitive) {
            throw new PersismException("Cannot read a primitive type object with this method.");
        }

        List<String> primaryKeys = metaData.getPrimaryKeys(objectClass, connection);
        if (primaryKeys.size() == 0) {
            throw new PersismException("Cannot perform readObjectByPrimary. " + metaData.getTableName(objectClass) + " has no primary keys.");
        }

        Map<String, PropertyInfo> properties = metaData.getTableColumns(object.getClass(), connection);
        List<Object> params = new ArrayList<>(primaryKeys.size());

        Result result = new Result();
        try {
            for (String column : primaryKeys) {
                PropertyInfo propertyInfo = properties.get(column);
                params.add(propertyInfo.getter.invoke(object));
            }

            exec(result, metaData.getSelectStatement(object, connection), params.toArray());

            if (result.rs.next()) {
                readObject(object, result.rs);
                return true;
            }
            return false;

        } catch (IllegalAccessException | InvocationTargetException | IOException e) {
            throw new PersismException(e);
        } catch (SQLException e) {
            Util.rollback(connection);
            throw new PersismException(e);
        } finally {
            Util.cleanup(result.st, result.rs);
        }
    }

    /**
     * Fetch an object from the database by primary key(s).
     *
     * @param objectClass class of objects to return
     * @param primaryKey primary key value parameters
     * @param <T> Return type
     * @return new instance of T or null if not found
     * @throws PersismException if something goes wrong
     */
    public <T> T fetch(Class<T> objectClass, Object... primaryKey) throws PersismException {

        String select = metaData.getSelectStatement(objectClass, connection);

        if (log.isDebugEnabled()) {
            log.debug("fetch Class<T> objectClass, Object... primaryKey: SQL " + select);
        }
        return fetch(objectClass, select, primaryKey);
    }

    /**
     * Fetch an object of the specified type from the database. The type can be a Data Object or a native Java Object or primitive.
     *
     * @param objectClass Type of returned value
     * @param sql         query - this would usually be a select OR a select of a single column if the type is a primitive.
     *                    If this is a primitive type then this method will only look at the 1st column in the result.
     * @param parameters  parameters to the query.
     * @param <T> Return type
     * @return value read from the database of type T or null if not found
     * @throws PersismException Well, this is a runtime exception so actually it could be anything really.
     */
    public <T> T fetch(Class<T> objectClass, String sql, Object... parameters) throws PersismException {
        // If we know this type it means it's a primitive type. Not a DAO so we use a different rule to read those
        boolean readPrimitive = Types.getType(objectClass) != null;

        if (!readPrimitive && objectClass.getAnnotation(QueryResult.class) == null) {
            // Make sure columns are initialized properly if this is a table  todo  why?
            metaData.getTableColumns(objectClass, connection);
        }

        Result result = new Result();
        try {

            exec(result, sql, parameters);

            if (result.rs.next()) {

                if (readPrimitive) {
                    return (T) readPrimitive(result.rs, 1, objectClass);

                } else {
                    T t = objectClass.newInstance();
                    readObject(t, result.rs);
                    return t;
                }
            }

            return null;

        } catch (IllegalAccessException | InvocationTargetException | InstantiationException | IOException e) {
            throw new PersismException(e);

        } catch (SQLException e) {
            Util.rollback(connection);
            throw new PersismException(e);
        } finally {
            Util.cleanup(result.st, result.rs);
        }
    }


    /**
     * Execute an arbut? Why bother? Why even put in Command?
     *
     * @param sql
     * @param parameters
     * @return
     */
    List<LinkedHashMap<String, Object>> query(String sql, Object... parameters) {

        Statement st = null;
        ResultSet rs = null;

        List<LinkedHashMap<String, Object>> results = new ArrayList<LinkedHashMap<String, Object>>(32);
        try {

            if (parameters.length == 0) {
                st = connection.createStatement();
                rs = st.executeQuery(sql);
            } else {
                st = connection.prepareStatement(sql);

                PreparedStatement pst = (PreparedStatement) st;
                int n = 1;
                for (Object o : parameters) {
                    pst.setObject(n, o);
                    n++;
                }
                rs = pst.executeQuery();
            }


            while (rs.next()) {
                LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>(32);
                int n = rs.getMetaData().getColumnCount();
                for (int j = 1; j <= n; j++) {
                    map.put(rs.getMetaData().getColumnLabel(j), rs.getObject(j));
                }
                results.add(map);
            }
        } catch (SQLException e) {
            throw new PersismException(e);
        } finally {
            Util.cleanup(st, rs);
        }

        return results;
    }

    /*
    Package private methods (usually for unit tests)
     */
    MetaData getMetaData() {
        return metaData;
    }

    /*
    Private methods
     */

    private Result exec(Result result, String sql, Object... parameters) throws SQLException {
        if (sql.toLowerCase().contains("select ")) {
            result.st = connection.prepareStatement(sql);

            PreparedStatement pst = (PreparedStatement) result.st;
            Util.setParameters(pst, parameters);
            result.rs = pst.executeQuery();
        } else {
            // todo unit tests need to cover this.
            result.st = connection.prepareCall("{call " + sql + "}");

            CallableStatement cst = (CallableStatement) result.st;
            Util.setParameters(cst, parameters);
            result.rs = cst.executeQuery();
        }
        return result;
    }

    private <T> T readObject(Object object, ResultSet rs) throws IllegalAccessException, SQLException, InvocationTargetException, IOException {

        Class objectClass = object.getClass();
        // We should never call this method with a primitive type.
        assert Types.getType(objectClass) == null;

        Map<String, PropertyInfo> properties;
        if (objectClass.getAnnotation(QueryResult.class) == null) {
            properties = metaData.getTableColumns(objectClass, connection);
        } else {
            properties = metaData.getQueryColumns(objectClass, rs);
        }

        // Test if all properties have column mapping and throw PersismException if not
        // This block verifies that the object is fully initialized.
        // Any properties not marked by NotMapped should have been set (or if they have a getter only)
        // If not throw a PersismException
        Collection<PropertyInfo> allProperties = MetaData.getPropertyInfo(objectClass);
        if (properties.values().size() < allProperties.size()) {
            Set<PropertyInfo> missing = new HashSet<PropertyInfo>(allProperties.size());
            missing.addAll(allProperties);
            missing.removeAll(properties.values());

            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (PropertyInfo prop : missing) {
                sb.append(sep).append(prop.propertyName);
                sep = ",";
            }

            throw new PersismException("Object " + objectClass + " was not properly initialized. Some properties not found in the queried columns (" + sb + ").");
        }


        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        List<String> foundColumns = new ArrayList<String>(columnCount);

        for (int j = 1; j <= columnCount; j++) {

            String columnName = rsmd.getColumnLabel(j);
            PropertyInfo columnProperty = properties.get(columnName);

            if (columnProperty != null) {
                Class getterType = columnProperty.getter.getReturnType();

                Object value = readPrimitive(rs, j, getterType);

                foundColumns.add(columnName);

                if (value != null) {
                    try {
                        columnProperty.setter.invoke(object, value);
                    } catch (IllegalArgumentException e) {
                        String msg = e.getMessage() + " Object " + objectClass + ". Column: " + columnName + " Type of property: " + getterType + " - Type read: " + value.getClass() + " VALUE: " + value;
                        throw new PersismException(msg, e);
                    }

                }
            }
        }

        if (foundColumns.size() < properties.keySet().size()) {

            Set<String> missing = new HashSet<String>(columnCount);
            missing.addAll(properties.keySet());
            missing.removeAll(foundColumns);

            // todo maybe strict mode off logs warn? Should we do this if this is Query vs Table?
            throw new PersismException("Object " + objectClass + " was not properly initialized. Some properties not found in the queried columns. : " + missing);
        }

        if (object instanceof Persistable) {
            // Save this object's initial state to later detect changed properties
            ((Persistable) object).saveReadState();
        }

        return (T) object;

    }

    private Object readPrimitive(ResultSet rs, int column, Class returnType) throws SQLException, IOException {
        ResultSetMetaData resultSetMetaData = rs.getMetaData();
        int sqlColumnType = resultSetMetaData.getColumnType(column);

        Types columnType = Types.convert(sqlColumnType); // note this could be null if we can't match a type
        // Since there is no specific SQL column type for UUID we will use the return type to detect it.
        if (returnType.equals(UUID.class)) {
            // Check the return type for UUID since resultSetMetaData.getColumnType(column) has no UUID type
            // it always returns a char or nvarchar so we'll just test and set it here. FFS.
            columnType = Types.UUIDType;
        }
        String columnName = resultSetMetaData.getColumnLabel(column);

        Object value;
        if (columnType != null) {
            switch (columnType) {

                case TimestampType:
                    // work around to Oracle reading a oracle.sql.TIMESTAMP class with getObject
                    value = rs.getTimestamp(column);
                    break;
                case ClobType:
                    value = rs.getClob(column);
                    InputStream in = ((Clob) value).getAsciiStream();
                    StringWriter write = new StringWriter();

                    int c = -1;
                    while ((c = in.read()) != -1) {
                        write.write(c);
                    }
                    write.flush();
                    value = write.toString();
                    break;
                case BlobType:
                    // todo BlobType
                case InputStreamType:
                    // todo InputStreamType
                case ReaderType:
                    // todo ReaderType
                case EnumType:
                    // todo EnumType?
                case UUIDType:
                    value = rs.getObject(column);
                    if (value != null) {
                        value = UUID.fromString("" + value);
                    }
                    break;
                case floatType:
                case FloatType:
                case doubleType:
                case DoubleType:
                    if (returnType.equals(java.math.BigDecimal.class)) {
                        value = rs.getBigDecimal(column);
                    } else if (returnType.equals(java.lang.Double.class)) {
                        value = rs.getDouble(column);
                    } else {
                        value = rs.getFloat(column);
                    }
                    break;

                default:
                    value = rs.getObject(column);
            }

        } else {
            log.warn("Column type not known for SQL type " + sqlColumnType);
            value = rs.getObject(column);
        }

        // If value is null or column type is unknown - no need to try to convert anything.
        if (value != null && columnType != null) {

            Types valueType = Types.getType(value.getClass());

            // try to convert or cast the value to the proper type.
            // todo do code coverage for each specific type
            switch (valueType) {

                case booleanType:
                case BooleanType:
                    break;

                case byteType:
                case ByteType:
                case shortType:
                case ShortType:
                case integerType:
                case IntegerType:
                    // int to bool
                    if (returnType == Boolean.class || returnType == boolean.class) {
                        value = (Integer.valueOf("" + value) == 0) ? false : true;
                    }
                    break;

                case longType:
                case LongType:
                    // long to date
                    if (returnType.isAssignableFrom(java.util.Date.class) || returnType.isAssignableFrom(java.sql.Date.class)) {
                        long lval = Long.valueOf("" + value);

                        if (returnType.equals(java.sql.Date.class)) {
                            value = new java.sql.Date(lval);
                        } else {
                            value = new java.util.Date(lval);
                        }
                    } else if (returnType == Integer.class || returnType == int.class) {
                        log.warn("Possible overflow column " + columnName + " - Property is INT and column value is LONG");
                        value = Integer.parseInt("" + value);
                    }

                    break;

                case floatType:
                case FloatType:
                    break;

                case doubleType:
                case DoubleType:
                    // float or doubles to BigDecimal
                    if (returnType == BigDecimal.class) {
                        value = new BigDecimal("" + value);
                    } else if (returnType == Float.class || returnType == float.class) {
                        // todo add tests for this
                        log.warn("Possible overflow column " + columnName + " - Property is FLOAT and column value is DOUBLE");
                        value = Float.parseFloat("" + value);
                    } else if (returnType == Integer.class || returnType == int.class) {
                        log.warn("Possible overflow column " + columnName + " - Property is INT and column value is DOUBLE");
                        String val = "" + value;
                        if (val.contains(".")) {
                            val = val.substring(0, val.indexOf("."));
                        }
                        value = Integer.parseInt(val);
                    }
                    break;

                case BigDecimalType:
                    // mostly oracle
                    if (returnType == Float.class || returnType == float.class) {
                        value = ((BigDecimal) value).floatValue();
                    } else if (returnType == Double.class || returnType == double.class) {
                        value = ((BigDecimal) value).doubleValue();
                    } else if (returnType == Long.class || returnType == long.class) {
                        value = ((BigDecimal) value).longValue();
                    } else if (returnType == Integer.class || returnType == int.class) {
                        value = ((BigDecimal) value).intValue();
                    } else if (returnType == Boolean.class || returnType == boolean.class) {
                        value = ((BigDecimal) value).intValue() == 1;
                    }
                    break;

                case StringType:

                    // Read a string but we want a date
                    if (returnType.isAssignableFrom(java.util.Date.class) || returnType.isAssignableFrom(java.sql.Date.class)) {
                        // This condition occurs in SQLite when you have a datetime with default annotated
                        // the format returned is 2012-06-02 19:59:49
                        java.util.Date dval = null;
                        try {
                            // Used for SQLite returning dates as Strings under some conditions
                            DateFormat df = new SimpleDateFormat("yyyy-MM-DD hh:mm:ss");
                            dval = df.parse("" + value);
                        } catch (ParseException e) {
                            String msg = e.getMessage() + ". Column: " + columnName + " Type of property: " + returnType + " - Type read: " + value.getClass() + " VALUE: " + value;
                            throw new PersismException(msg, e);
                        }

                        if (returnType.equals(java.sql.Date.class)) {
                            value = new java.sql.Date(dval.getTime());
                        } else {
                            value = dval;
                        }

                    } else if (returnType.isEnum()) {
                        // If this is an enum do a case insensitive comparison
                        Object[] enumConstants = returnType.getEnumConstants();
                        for (Object element : enumConstants) {
                            if (("" + value).equalsIgnoreCase(element.toString())) {
                                value = element;
                                break;
                            }
                        }
                    }

                    break;

                case characterType:
                case CharacterType:
                    break;

                case UtilDateType:
                    break;
                case SQLDateType:
                    break;
                case TimeType:
                    break;

                case TimestampType:
                    if (returnType.isAssignableFrom(java.util.Date.class) || returnType.isAssignableFrom(java.sql.Date.class)) {
                        if (returnType.equals(java.sql.Date.class)) {
                            value = new java.sql.Date(((Timestamp) value).getTime());
                        } else {
                            value = new java.util.Date(((Timestamp) value).getTime());
                        }
                    } else {
                        value = ((Timestamp) value).getTime();
                    }

                    break;

                case byteArrayType:
                    break;
                case ByteArrayType:
                    break;
                case charArrayType:
                    break;
                case CharArrayType:
                    break;
                case ClobType:
                    // Convert to string
                    if (value != null) {
                        value = "" + value;
                    }
                    break;
                case BlobType:
                    break;
                case InputStreamType:
                    break;
                case ReaderType:
                    break;
                case EnumType:
                    break;
            }
        }

        return value;
    }


}