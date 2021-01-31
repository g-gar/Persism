package net.sf.persism;

/*
 * Created by IntelliJ IDEA.
 * User: DHoward
 * Date: 9/21/11
 * Time: 2:31 PM
 */

import net.sf.persism.dao.DAOFactory;
import net.sf.persism.dao.OracleBit;
import net.sf.persism.dao.OracleOrder;
import net.sf.persism.dao.Order;
import net.sf.persism.ddl.FieldDef;
import net.sf.persism.ddl.TableDef;

import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class TestOracle extends BaseTest {

    private static final Log log = Log.getLogger(TestOracle.class);

    protected void setUp() throws Exception {

        // Turn off SQLMode for next MSSQL Test so it uses JTDS
        BaseTest.mssqlmode = false;
        MSSQLDataSource.removeInstance();

        super.setUp();

        Properties props = new Properties();
        props.load(getClass().getResourceAsStream("/oracle.properties"));

        String driver = props.getProperty("database.driver");
        String url = props.getProperty("database.url");
        String username = props.getProperty("database.username");
        String password = props.getProperty("database.password");

        Class.forName(driver);

//        con = DriverManager.getConnection(url, username, password);
//        con = new net.sf.log4jdbc.ConnectionSpy(con);
        con = OracleDataSource.getInstance().getConnection();

        createTables();

        session = new Session(con);
    }


    protected void tearDown() throws Exception {
        super.tearDown();
    }


    public void testInsert() throws Exception {
        OracleOrder order = (OracleOrder) DAOFactory.newOrder(con);
        order.setName("MOO");
        order.setPaid(true);
        order.setBit2("ANYTHING?");
        order.setBit1(new BigDecimal(0));
        try {
            session.insert(order);
        } catch (PersismException e) {
            // net.sf.persism.PersismException: ORA-01722: invalid number
            // Anything is not a number. Really?
            assertTrue("should contain invalid number", e.getMessage().contains("invalid number"));
        }

        order.setBit2("1");
        session.insert(order);
        log.info("inserted? " + order);
        assertTrue("order # > 0", order.getId() > 0);

        order = (OracleOrder) DAOFactory.newOrder(con);
        order.setName("MOO2");
        order.setPaid(false);
        session.insert(order);

        order = (OracleOrder) DAOFactory.newOrder(con);
        order.setName("MOO3");
        session.insert(order);

        List<Order> list = session.query(Order.class, "select * from ORDERS");
        log.info("MOO:" + list);

    }

    public void testTimeStamp() {
        Statement st = null;
        java.sql.ResultSet rs = null;

        // TESTTIMESTAMP
        try {
            st = con.createStatement();
            st.executeUpdate("INSERT INTO TESTTIMESTAMP (NAME) VALUES ('TEST')");

            rs = st.executeQuery("SELECT * FROM TESTTIMESTAMP");
            ResultSetMetaData rsmd = rs.getMetaData();

            while (rs.next()) {
                log.info("testTimeStamp: TYPE: " + rsmd.getColumnType(2) + " " + Types.convert(rsmd.getColumnType(2))); // second column
                Date dt = rs.getDate("TS"); // loses time component
                Object obj = rs.getObject("TS"); // returns fucken oracle.sql.TIMESTAMP class
                Timestamp ts = rs.getTimestamp("TS");

                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                log.info("testTimeStamp: " + format.format(dt));
                log.info("testTimeStamp: " + format.format(ts));
                log.info("testTimeStamp: " + obj + " " + obj.getClass().getName());
            }

        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            fail(e.getMessage());
        } finally {
            UtilsForTests.cleanup(st, rs);
        }
    }


/*
YOU NEED THE TRIGGER PART
CREATE table "TEST" (
    "ID"         NUMBER(10) NOT NULL,
    "NAME"       VARCHAR2(20),
    constraint  "TEST_PK" primary key ("ID")
)
/

CREATE sequence "TEST_SEQ"
/

CREATE trigger "BI_TEST"
  before insert on "TEST"
  for each row
begin
  if :NEW."ID" is null then
    select "TEST_SEQ".nextval into :NEW."ID" from dual;
  end if;
end;
/
*/


    @Override
    protected void createTables() throws SQLException {

        List<String> commands = new ArrayList<String>(4);

        if (UtilsForTests.isTableInDatabase("ORDERS", con)) {
            commands.add("DROP TRIGGER BI_ORDERS");
            commands.add("DROP TABLE ORDERS");
            commands.add("DROP SEQUENCE ORDERS_SEQ");
        }

        if (UtilsForTests.isTableInDatabase("ORACLEBIT", con)) {
            commands.add("DROP TABLE ORACLEBIT");
        }

        commands.add("CREATE TABLE  \"ORACLEBIT\" " +
                "(\"ID\" INT, " +
                "\"NAME\" VARCHAR2(50), " +
                "\"ROW__ID\" VARCHAR2(10), " +
                "\"CUSTOMER_ID\" VARCHAR(10), " +
                "\"PAID\" NUMBER(3), " + // BIT TEST
                "\"CREATED\" DATE, " +
                "\"GARBAGE\" CHAR(1), " + // BIT TEST
                " CONSTRAINT \"ORACLEBIT_PK\" PRIMARY KEY (\"ID\") ENABLE" +
                "   ) ");


        // https://stackoverflow.com/questions/2426145/oracles-lack-of-a-bit-datatype-for-table-columns#2427016
        commands.add("CREATE TABLE  \"ORDERS\" " +
                "(\"ID\" INT, " +
                "\"NAME\" VARCHAR2(50), " +
                "\"ROW__ID\" VARCHAR2(10), " +
                "\"CUSTOMER_ID\" VARCHAR(10), " +
                "\"PAID\" NUMBER(3), " +
                "\"CREATED\" DATE DEFAULT CURRENT_TIMESTAMP, " +
                "\"DATE_PAID\" DATE, " +
                "\"BIT1\" CHAR(1), " + // BIT TEST
                "\"BIT2\" NUMBER(3), " + // BIT TEST
                " CONSTRAINT \"ORDERS_PK\" PRIMARY KEY (\"ID\") ENABLE" +
                "   ) ");

        commands.add("CREATE SEQUENCE   \"ORDERS_SEQ\"  MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 101 CACHE 20 NOORDER NOCYCLE");

        commands.add("CREATE trigger \"BI_ORDERS\" " +
                "  before insert on \"ORDERS\" " +
                "  for each row " +
                "begin " +
                "  if :NEW.\"ID\" is null then " +
                "    select \"ORDERS_SEQ\".nextval into :NEW.\"ID\" from dual; " +
                "  end if; " +
                "end;");


        if (UtilsForTests.isTableInDatabase("CUSTOMERS", con)) {
            commands.add("DROP TABLE CUSTOMERS");
        }

        commands.add("CREATE TABLE CUSTOMERS ( " +
                " Customer_ID VARCHAR(10) PRIMARY KEY NOT NULL, " +
                " Company_Name VARCHAR(30) NULL, " +
                " Contact_Name VARCHAR(30) NULL, " +
                " Contact_Title VARCHAR(10) NULL, " +
                " Address VARCHAR(40) NULL, " +
                " City VARCHAR(30) NULL, " +
                " Region VARCHAR(10) NULL, " +
                " Postal_Code VARCHAR(10) NULL, " +
                " Country VARCHAR(2) DEFAULT 'US' NOT NULL, " +
                " Phone VARCHAR(30) NULL, " +
                " Fax VARCHAR(30) NULL, " +
                " Date_Registered TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                " SomeDouble NUMBER(38,2) NULL," +
                " SomeInt NUMBER(38,2) NULL," +
                " STATUS CHAR(1) NULL, " +
                " Date_Of_Last_Order DATE " +
                ") ");


        if (UtilsForTests.isTableInDatabase("TESTTIMESTAMP", con)) {
            commands.add("DROP TABLE TESTTIMESTAMP");
        }

        commands.add("CREATE TABLE TESTTIMESTAMP ( NAME VARCHAR(10), TS TIMESTAMP DEFAULT CURRENT_TIMESTAMP ) ");
        executeCommands(commands, con);
    }

    public void testBits() {
        OracleBit bt1 = new OracleBit();
        bt1.setId(1);
        bt1.setName("1");
        bt1.setCustomerId("CUST ID");
        bt1.setCreated(new Date(System.currentTimeMillis()));
        bt1.setPaid(true);
        bt1.setGarbage(true);

        session.insert(bt1);

        OracleBit test = new OracleBit();
        test.setId(1);

        assertTrue(session.fetch(test));
        log.info(test);
        assertTrue(test.isPaid());
        assertTrue(test.isGarbage());

        test = new OracleBit();
        test.setId(2);
        test.setName("2");
        test.setCustomerId("CUST ID");
        test.setCreated(new Date(System.currentTimeMillis()));
        test.setPaid(false);
        test.setGarbage(false);

        session.insert(test);

        assertTrue(session.fetch(test));
        log.info(test);
        assertFalse(test.isPaid());
        assertFalse(test.isGarbage());

        test = new OracleBit();
        test.setId(3);
        test.setName("3");
        test.setCustomerId("CUST ID");
        test.setCreated(new Date(System.currentTimeMillis()));

        session.insert(test);

        assertTrue(session.fetch(test));
        log.info(test);
        assertNull(test.isPaid());
        assertNull(test.isGarbage());

        // count 3 after from query
    }

    public void testCreateTable() {
        Statement st = null;
        try {

            st = con.createStatement();

/*
            if (Util.isTableInDatabase("Employees", con)) {
                st.execute("DROP TABLE Employees");
            }

            String s= "CREATE TABLE Employees (\n" +
                    "Employee_ID INTEGER,\n" +
                    "Name VARCHAR(30)\n" +
                    ")";
            st.execute(s);
*/

            TableDef table = new TableDef();
            table.setName("Employees");
            table.addField(new FieldDef("ID", Integer.class, 10, 0));
            table.addField(new FieldDef("Name", String.class, 50, 0));
            table.addField(new FieldDef("HireDate", Date.class));
            table.addField(new FieldDef("Salary1", BigDecimal.class, 20, 3));
            table.addField(new FieldDef("Salary2", Double.class, 14, 3));
            table.addField(new FieldDef("Salary3", Float.class, 9, 3));

            UtilsForTests.createTable(table, con);

        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            fail(e.getMessage());
        } finally {
            UtilsForTests.cleanup(st, null);
        }
    }


}