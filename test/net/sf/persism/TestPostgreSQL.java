package net.sf.persism;

import net.sf.persism.dao.Contact;
import net.sf.persism.dao.Customer;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Comments for TestPostgreSQL go here.
 *
 * @author Dan Howard
 * @since 6/21/12 6:05 AM
 */
public final class TestPostgreSQL extends BaseTest {

    private static final Log log = Log.getLogger(TestPostgreSQL.class);

    @Override
    protected void setUp() throws Exception {
        connectionType = ConnectionTypes.PostgreSQL;
        super.setUp();

        // https://jdbc.postgresql.org/documentation/head/connect.html

        Properties props = new Properties();
        props.load(getClass().getResourceAsStream("/postgresql.properties"));
        String driver = props.getProperty("database.driver");
        String url = props.getProperty("database.url");
        String username = props.getProperty("database.username");
        String password = props.getProperty("database.password");
        Class.forName(driver);
        con = DriverManager.getConnection(url, props);

        con = new net.sf.log4jdbc.ConnectionSpy(con);

        createTables();

        session = new Session(con);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    public void testContactTable() throws SQLException {

        super.testContactTable();

        // Insert specify GUID
        Contact contact = new Contact();
        //contact.setIdentity(UUID.randomUUID());
        contact.setFirstname("Fred");
        contact.setLastname("Flintstone");
        contact.setDivision("DIVISION X");
        contact.setLastModified(new Timestamp(System.currentTimeMillis() - 100000000l));
        contact.setContactName("Fred Flintstone");
        contact.setAddress1("123 Sesame Street");
        contact.setAddress2("Appt #0 (garbage can)");
        contact.setCompany("Grouch Inc");
        contact.setCountry("US");
        contact.setCity("Philly?");
        contact.setType("X");
        contact.setDateAdded(new Date(System.currentTimeMillis()));
        contact.setAmountOwed(100.23f);
        contact.setNotes("B:AH B:AH VBLAH\r\n BLAH BLAY!");
        contact.setWhatTimeIsIt(Time.valueOf(LocalTime.now()));
        session.insert(contact);

        log.info("contact after insert: " + contact);
        assertNotNull("should not be null identity", contact.getIdentity());

        session.fetch(contact);

        contact.setDivision("DIVISION Y");
        session.update(contact);

        session.delete(contact);
    }

    @Override
    protected void createTables() throws SQLException {

        List<String> commands = new ArrayList<String>(12);
        String sql;
//sql = "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";";
//executeCommand(sql, con);
        if (UtilsForTests.isTableInDatabase("Orders", con)) {
            sql = "DROP TABLE Orders";
            commands.add(sql);
        }

        sql = "CREATE TABLE Orders ( " +
                " ID SERIAL PRIMARY KEY, " +
                " NAME VARCHAR(30) NULL, " +
                " PAID BOOLEAN NULL, " +
                " Customer_ID VARCHAR(10) NULL, " +
                " Created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, " +
                " Date_Paid TIMESTAMP NULL, " +
                " Date_Something TIMESTAMP NULL " +
                ") ";

        commands.add(sql);

        if (UtilsForTests.isTableInDatabase("Customers", con)) {
            commands.add("DROP TABLE Customers");
        }

        commands.add("CREATE TABLE Customers ( " +
                " Customer_ID VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v1(), " +
                " Company_Name VARCHAR(30) NULL, " +
                " Contact_Name VARCHAR(30) NULL, " +
                " Contact_Title VARCHAR(10) NULL, " +
                " Address VARCHAR(40) NULL, " +
                " City VARCHAR(30) NULL, " +
                " Region VARCHAR(10) NULL, " +
                " Postal_Code VARCHAR(10) NULL, " +
                " Country VARCHAR(2) NOT NULL DEFAULT 'US', " +
                " Phone VARCHAR(30) NULL, " +
                " Fax VARCHAR(30) NULL, " +
                " STATUS CHAR(1), " +
                " Date_Registered TIMESTAMP with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL, " +
                " Date_Of_Last_Order DATE, " +
                " TestLocalDate DATE, " +
                " TestLocalDateTime TIMESTAMP  " +
                ") ");

        if (UtilsForTests.isTableInDatabase("Invoices", con)) {
            commands.add("DROP TABLE Invoices");
        }

        commands.add("CREATE TABLE Invoices ( " +
                " Invoice_ID SERIAL PRIMARY KEY, " +
                " Customer_ID varchar(10) NOT NULL, " +
                " Paid BOOLEAN NOT NULL, " +
                " Price NUMERIC(7,3) NOT NULL, " +
                " Quantity INT NOT NULL, " +
                " Total NUMERIC(10,3) NOT NULL " +
                ") ");


        if (UtilsForTests.isTableInDatabase("TABLEMULTIPRIMARY", con)) {
            commands.add("DROP TABLE TABLEMULTIPRIMARY");
        }

        commands.add("CREATE TABLE TABLEMULTIPRIMARY ( " +
                " CUSTOMER_NAME VARCHAR(30) NOT NULL, " +
                " Field4 VARCHAR(30), " +
                " Field5  TIMESTAMP with time zone, " +
                " ID INT NOT NULL " +
                ") ");

        commands.add("ALTER TABLE TABLEMULTIPRIMARY ADD PRIMARY KEY (ID, CUSTOMER_NAME)");

        if (UtilsForTests.isTableInDatabase("Contacts", con)) {
            sql = "DROP TABLE Contacts";
            commands.add(sql);
        }

        sql = "CREATE TABLE Contacts ( " +
                " identity uuid PRIMARY KEY DEFAULT uuid_generate_v1(), " +
                " PartnerID uuid NULL, " +
                " Type char(2) NOT NULL, " +
                " Firstname varchar(50) NULL, " +
                " Lastname varchar(50) NULL, " +
                " ContactName varchar(50) NULL, " +
                " Company varchar(50) NULL, " +
                " Division varchar(50) NULL, " +
                " Email varchar(50) NULL, " +
                " Address1 varchar(50) NULL, " +
                " Address2 varchar(50) NULL, " +
                " City varchar(50) NULL, " +
                " StateProvince varchar(50) NULL, " +
                " ZipPostalCode varchar(10) NULL, " +
                " Country varchar(50) NULL, " +
                " DateAdded Timestamp NULL, " +
                " LastModified Timestamp NULL, " +
                " Notes text NULL, " +
                " AmountOwed float NULL, " +
                " BigInt DECIMAL(20) NULL, " +
                " SomeDate Timestamp NULL, " +
                " TestINstant Timestamp NULL, " +
                " TestINstant2 Timestamp NULL, " +
                " WhatMiteIsIt time NULL, " +
                " WhatTimeIsIt time NULL " +
                ") ";
        commands.add(sql);

        executeCommands(commands, con);

        if (UtilsForTests.isTableInDatabase("DateTestLocalTypes", con)) {
            executeCommand("DROP TABLE DateTestLocalTypes", con);
        }

        sql = "CREATE TABLE DateTestLocalTypes ( " +
                " ID INT, " +
                " Description VARCHAR(100), " +
                " DateOnly DATE, " +
                " TimeOnly TIME," +
                " DateAndTime TIMESTAMP) ";

        executeCommand(sql, con);

        if (UtilsForTests.isTableInDatabase("DateTestSQLTypes", con)) {
            executeCommand("DROP TABLE DateTestSQLTypes", con);
        }

        sql = "CREATE TABLE DateTestSQLTypes ( " +
                " ID INT, " +
                " Description VARCHAR(100), " +
                " DateOnly DATE, " +
                " TimeOnly TIME," +
                " UtilDateAndTime TIMESTAMP," +
                " DateAndTime TIMESTAMP) ";

        executeCommand(sql, con);

    }


    public void testDefaultDate() throws SQLException {

        Customer customer = new Customer();
        customer.setCustomerId("MOO");
        session.delete(customer);

        customer.setCompanyName("Rock Quarry Ltd");
        customer.setContactName("FRED");
        customer.setStatus('1');
        customer.setTestLocalDate(LocalDate.now());
        customer.setTestLocalDateTime(LocalDateTime.now(ZoneId.systemDefault()));
        session.insert(customer);
        log.info(customer);

        session.fetch(customer);
        log.info("date " + customer.getTestLocalDate());
        log.info("datetime " + customer.getTestLocalDateTime());
        assertNotNull(customer.getDateRegistered());

        tryInsertReturnall();
    }

    private void tryInsertReturnall() throws SQLException {
        // this was a test to see if I could prepare a statement and return all columns. Nope.....

        // ensure metadata is there
        log.info(session.query(Contact.class, "select * from Contacts"));

//        String insertStatement = "INSERT INTO Customers (Customer_ID, Company_Name, Contact_Name) VALUES ( ?, ?, ? ) ";
        String insertStatement = "INSERT INTO Contacts (FirstName, LastName, Type) VALUES ( ?, ?, ? ) ";

        PreparedStatement st = null;
        ResultSet rs = null;

        // Map<String, ColumnInfo> columns = session.getMetaData().getColumns(Contact.class, con);
        List<String> keys = session.getMetaData().getPrimaryKeys(Contact.class, con);
//        String[] columnNames = columns.keySet().toArray(new String[0]);
        String[] columnNames = keys.toArray(new String[0]);
        st = con.prepareStatement(insertStatement, columnNames);
//        st.setString(1, "123");
        st.setString(1, "Slate Quarry");
        st.setString(2, "Fred");
        st.setString(3, "X");


        int ret = st.executeUpdate();
        log.info("rows insetred " + ret);
        rs = st.getGeneratedKeys();
        log.info("resultset? " + st.getResultSet());

        ResultSetMetaData rsmd = rs.getMetaData();
        while (rs.next()) {
            for (int j = 1; j <= rsmd.getColumnCount(); j++) {
                log.info(j + " " + rsmd.getColumnLabel(j) + " " + rsmd.getColumnTypeName(j) + " " + rs.getObject(j));
            }
        }

//        PreparedStatement pstmt = con.prepareStatement("insert into some_table (some_value) values (?)", new String[]{"id"});
//        pstmt.setInt(1, 42);
//        pstmt.executeUpdate();
//        ResultSet rs  = pstmt.getGeneratedKeys();
//        UUID id = null;
//        if (rs.next()) id = rs.getObject(1, UUID.class);


    }

    @Override
    public void testAllDates() {
        super.testAllDates();
    }
}
