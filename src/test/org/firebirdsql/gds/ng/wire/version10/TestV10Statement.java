/*
 * $Id$
 *
 * Firebird Open Source J2EE Connector - JDBC Driver
 *
 * Distributable under LGPL license.
 * You may obtain a copy of the License at http://www.gnu.org/copyleft/lgpl.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * LGPL License for more details.
 *
 * This file was created by members of the firebird development team.
 * All individual contributions remain the Copyright (C) of those
 * individuals.  Contributors to this file are either listed here or
 * can be obtained from a source repository history command.
 *
 * All rights reserved.
 */
package org.firebirdsql.gds.ng.wire.version10;

import org.firebirdsql.common.FBTestProperties;
import org.firebirdsql.encodings.EncodingFactory;
import org.firebirdsql.gds.ISCConstants;
import org.firebirdsql.gds.TransactionParameterBuffer;
import org.firebirdsql.gds.impl.jni.EmbeddedGDSImpl;
import org.firebirdsql.gds.impl.jni.NativeGDSImpl;
import org.firebirdsql.gds.impl.wire.TransactionParameterBufferImpl;
import org.firebirdsql.gds.ng.FbConnectionProperties;
import org.firebirdsql.gds.ng.FbStatement;
import org.firebirdsql.gds.ng.FbTransaction;
import org.firebirdsql.gds.ng.StatementType;
import org.firebirdsql.gds.ng.fields.FieldDescriptor;
import org.firebirdsql.gds.ng.fields.FieldValue;
import org.firebirdsql.gds.ng.fields.RowDescriptor;
import org.firebirdsql.gds.ng.wire.FbWireDatabase;
import org.firebirdsql.gds.ng.wire.ProtocolCollection;
import org.firebirdsql.gds.ng.wire.SimpleRowListener;
import org.firebirdsql.gds.ng.wire.WireConnection;
import org.firebirdsql.management.FBManager;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.firebirdsql.common.FBTestProperties.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 * @author <a href="mailto:mrotteveel@users.sourceforge.net">Mark Rotteveel</a>
 */
public class TestV10Statement {

    private final FbConnectionProperties connectionInfo;
    private FbWireDatabase db;
    FBManager fbManager;

    {
        connectionInfo = new FbConnectionProperties();
        connectionInfo.setServerName(FBTestProperties.DB_SERVER_URL);
        connectionInfo.setPortNumber(FBTestProperties.DB_SERVER_PORT);
        connectionInfo.setUser(DB_USER);
        connectionInfo.setPassword(DB_PASSWORD);
        connectionInfo.setDatabaseName(FBTestProperties.getDatabasePath());
        connectionInfo.setEncoding("NONE");
    }

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void verifyTestType() {
        // Test irrelevant for embedded
        assumeTrue(!FBTestProperties.getGdsType().toString().equals(EmbeddedGDSImpl.EMBEDDED_TYPE_NAME));
        // Test irrelevant for native
        assumeTrue(!FBTestProperties.getGdsType().toString().equals(NativeGDSImpl.NATIVE_TYPE_NAME));
    }

    @Before
    public void setUp() throws Exception {
        fbManager = defaultDatabaseSetUp();
        WireConnection gdsConnection = new WireConnection(connectionInfo, EncodingFactory.getDefaultInstance(), ProtocolCollection.create(new Version10Descriptor()));
        gdsConnection.socketConnect();
        db = gdsConnection.identify();
        assertEquals("Unexpected FbWireDatabase implementation", V10Database.class, db.getClass());

        db.attach();
    }

    @Test
     public void testSelect_NoParameters_Describe() throws Exception {
        final FbTransaction transaction = getTransaction();
        final FbStatement statement = db.createStatement();
        statement.setTransaction(transaction);
        statement.prepare(
                "SELECT RDB$DESCRIPTION AS \"Description\", RDB$RELATION_ID, RDB$SECURITY_CLASS, RDB$CHARACTER_SET_NAME " +
                        "FROM RDB$DATABASE");

        assertEquals("Unexpected StatementType", StatementType.SELECT, statement.getType());

        final RowDescriptor fields = statement.getFieldDescriptor();
        assertNotNull("Fields", fields);
        // Note that in the V10 protocol we don't have support for the table alias, so it is always null
        List<FieldDescriptor> expectedFields =
                Arrays.asList(
                        new FieldDescriptor(ISCConstants.SQL_BLOB | 1, 1, 3, 8, "Description", null, "RDB$DESCRIPTION", "RDB$DATABASE", "SYSDBA"),
                        new FieldDescriptor(ISCConstants.SQL_SHORT | 1, 0, 0, 2, "RDB$RELATION_ID", null, "RDB$RELATION_ID", "RDB$DATABASE", "SYSDBA"),
                        new FieldDescriptor(ISCConstants.SQL_TEXT | 1, 3, 0, 93, "RDB$SECURITY_CLASS", null, "RDB$SECURITY_CLASS", "RDB$DATABASE", "SYSDBA"),
                        new FieldDescriptor(ISCConstants.SQL_TEXT | 1, 3, 0, 93, "RDB$CHARACTER_SET_NAME", null, "RDB$CHARACTER_SET_NAME", "RDB$DATABASE", "SYSDBA")
                );
        assertEquals("Unexpected values for fields", expectedFields, fields.getFieldDescriptors());
        assertNotNull("Parameters", statement.getParameterDescriptor());
        assertEquals("Unexpected parameter count", 0, statement.getParameterDescriptor().getCount());

        statement.close();
    }

    @Test
    public void testSelect_NoParameters_Execute_and_Fetch() throws Exception {
        final FbTransaction transaction = getTransaction();
        final FbStatement statement = db.createStatement();
        statement.setTransaction(transaction);
        statement.prepare(
                "SELECT RDB$DESCRIPTION AS \"Description\", RDB$RELATION_ID, RDB$SECURITY_CLASS, RDB$CHARACTER_SET_NAME " +
                        "FROM RDB$DATABASE");

        final SimpleRowListener rowListener = new SimpleRowListener();
        statement.addRowListener(rowListener);

        statement.execute(Collections.<FieldValue>emptyList());

        assertEquals("Expected hasResultSet to be set to true", Boolean.TRUE, rowListener.hasResultSet());
        assertEquals("Expected hasSingletonResult to be set to false", Boolean.FALSE, rowListener.hasSingletonResult());
        assertNull("Expected allRowsFetched not set yet", rowListener.isAllRowsFetched());
        assertEquals("Expected no rows to be fetched yet", 0, rowListener.getRows().size());

        statement.fetchRows(10);

        assertEquals("Expected allRowsFetched to be set to true", Boolean.TRUE, rowListener.isAllRowsFetched());
        assertEquals("Expected a single row to have been fetched", 1, rowListener.getRows().size());

        statement.close();
    }

    @Test
    public void testSelect_WithParameters_Describe() throws Exception {
        final FbTransaction transaction = getTransaction();
        final FbStatement statement = db.createStatement();
        statement.setTransaction(transaction);
        statement.prepare(
                "SELECT a.RDB$CHARACTER_SET_NAME " +
                        "FROM RDB$CHARACTER_SETS a " +
                        "WHERE a.RDB$CHARACTER_SET_ID = ? OR a.RDB$BYTES_PER_CHARACTER = ?");

        assertEquals("Unexpected StatementType", StatementType.SELECT, statement.getType());

        final RowDescriptor fields = statement.getFieldDescriptor();
        assertNotNull("Fields", fields);
        // Note that in the V10 protocol we don't have support for the table alias, so it is always null
        List<FieldDescriptor> expectedFields =
                Arrays.asList(
                        new FieldDescriptor(ISCConstants.SQL_TEXT | 1, 3, 0, 93, "RDB$CHARACTER_SET_NAME", null, "RDB$CHARACTER_SET_NAME", "RDB$CHARACTER_SETS", "SYSDBA")
                );
        assertEquals("Unexpected values for fields", expectedFields, fields.getFieldDescriptors());

        final RowDescriptor parameters = statement.getParameterDescriptor();
        assertNotNull("Parameters", parameters);
        List<FieldDescriptor> expectedParameters =
                Arrays.asList(
                        new FieldDescriptor(ISCConstants.SQL_SHORT | 1, 0, 0, 2, null, null, null, null, null),
                        new FieldDescriptor(ISCConstants.SQL_SHORT | 1, 0, 0, 2, null, null, null, null, null)
                );
        assertEquals("Unexpected values for parameters", expectedParameters, parameters.getFieldDescriptors());

        statement.close();
    }

    @Test
    public void testSelect_WithParameters_Execute_and_Fetch() throws Exception {
        final FbTransaction transaction = getTransaction();
        final FbStatement statement = db.createStatement();
        statement.setTransaction(transaction);
        statement.prepare(
                "SELECT a.RDB$CHARACTER_SET_NAME " +
                        "FROM RDB$CHARACTER_SETS a " +
                        "WHERE a.RDB$CHARACTER_SET_ID = ? OR a.RDB$BYTES_PER_CHARACTER = ?");

        RowDescriptor descriptor = statement.getParameterDescriptor();
        FieldValue param1 = new FieldValue(descriptor.getFieldDescriptor(0), new byte[] { 0, 0, 0, 3 }); // int = 3 (id of UNICODE_FSS)
        FieldValue param2 = new FieldValue(descriptor.getFieldDescriptor(1), new byte[] { 0, 0, 0, 1 }); // int = 1 (single byte character sets)

        final SimpleRowListener rowListener = new SimpleRowListener();
        statement.addRowListener(rowListener);

        statement.execute(Arrays.asList(param1, param2));

        assertEquals("Expected hasResultSet to be set to true", Boolean.TRUE, rowListener.hasResultSet());
        assertEquals("Expected hasSingletonResult to be set to false", Boolean.FALSE, rowListener.hasSingletonResult());
        assertNull("Expected allRowsFetched not set yet", rowListener.isAllRowsFetched());
        assertEquals("Expected no rows to be fetched yet", 0, rowListener.getRows().size());

        // 100 should be sufficient to fetch all character sets
        statement.fetchRows(100);

        assertEquals("Expected allRowsFetched to be set to true", Boolean.TRUE, rowListener.isAllRowsFetched());
        // Number is database dependent (unicode_fss + all single byte character sets)
        assertTrue("Expected more than two rows", rowListener.getRows().size() > 2);

        statement.close();
    }

    // TODO Test with executable stored procedure

    @Test
    public void testAllocate_NotClosed() throws Exception {
        final V10Statement statement = (V10Statement) db.createStatement();

        statement.allocateStatement();

        expectedException.expect(SQLNonTransientException.class);
        expectedException.expectMessage("allocateStatement only allowed when current state is CLOSED");
        statement.allocateStatement();
    }

    private FbTransaction getTransaction() throws SQLException {
        TransactionParameterBuffer tpb = new TransactionParameterBufferImpl();
        tpb.addArgument(ISCConstants.isc_tpb_read_committed);
        tpb.addArgument(ISCConstants.isc_tpb_rec_version);
        tpb.addArgument(ISCConstants.isc_tpb_write);
        tpb.addArgument(ISCConstants.isc_tpb_wait);
        return db.createTransaction(tpb);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (db != null) {
                try {
                    db.detach();
                } catch (SQLException ex) {
                    // ignore (TODO: log)
                }
            }
        } finally {
            defaultDatabaseTearDown(fbManager);
        }
    }
}