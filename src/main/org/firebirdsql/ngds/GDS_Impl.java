/*
 * Firebird Open Source J2ee connector - jdbc driver
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
 * can be obtained from a CVS history command.
 *
 * All rights reserved.
 */

package org.firebirdsql.ngds;

import org.firebirdsql.gds.*;
import org.firebirdsql.logging.LoggerFactory;
import org.firebirdsql.logging.Logger;


import java.io.IOException;


/**
 * Type 2 GDS implementation.
 *
 */
public class GDS_Impl extends AbstractGDS implements GDS
    {
    private static Logger log = LoggerFactory.getLogger(GDS_Impl.class,false);

	 public GDS_Impl()
		 {

		 }

    public GDS_Impl(GDSType gdsType)
        {
		    super(gdsType);

        final boolean logging = log != null;
        if(logging) log.info( "Attempting to loadLibrary for \""+"jaybird"+"\"" );

        try
            {
            System.loadLibrary("jaybird");
            }
        catch( SecurityException ex )
            {
            if(logging) log.error( "Failed to load native library. SecurityException caught.", ex );
			throw ex;
            }
        catch( UnsatisfiedLinkError ex )
            {
            if(logging) log.error( "Failed to load native library. UnsatisfiedLinkError caught.", ex );
			throw ex;
            }
        if(logging) log.info( "loadLibrary for \""+"java_gds"+"\" returned OK." );


   if(logging) log.info( "Attempting to initilize native library." );

        if( this.getGdsType() == GDSType.NATIVE )
            attemptToLoadAClientLibraryFromList(LIST_OF_CLIENT_LIBRARIES_TO_TRY);
        else if( this.getGdsType() == GDSType.NATIVE_EMBEDDED )
            attemptToLoadAClientLibraryFromList(LIST_OF_EMBEDDED_SERVER_LIBRARIES_TO_TRY);
        else
            throw new RuntimeException("Unrecognized GDS type.");
        }

     private static final String[] LIST_OF_CLIENT_LIBRARIES_TO_TRY = {
        "fbclient.dll",
        "libfbclient.so",
        "gds32.dll",
        "libgds.so",
    };

     private static final String[] LIST_OF_EMBEDDED_SERVER_LIBRARIES_TO_TRY = {
        "fbembed.dll",
        "libfbembed.so",
     };

    private void attemptToLoadAClientLibraryFromList(String[] clientLibraryList)
        {
        final boolean logging = log != null;

        for( int i = 0, n = clientLibraryList.length; i<n; i++ )
            {
            final String currentClientLibraryToTry = clientLibraryList[i];
            try
                {
                nativeInitilize(currentClientLibraryToTry);
                }
            catch( Throwable th )
                {
                th.printStackTrace(); // Dont hide it completly

                if(logging) log.debug( "Failed to load client library # "+i+" - \""+currentClientLibraryToTry+"\".", th );

                // If we have just failed to load the last client library
                // then we need to throw an exception.
                if( i == clientLibraryList.length - 1 )
                    throw new RuntimeException("Failed to initilize jaybird native library. This is most likley due to a failure to load the firebird client library.");

                // Otherwise we continue to next client library
                continue;
                }

            if(logging) log.info( "Failed to load client library # "+i+" - \""+currentClientLibraryToTry+"\"." );

            // If we get here we have been loaded a client library so we stop here.
            break;
            }
        }

    public synchronized  Clumplet newClumplet(int type, String content) {
        return new StringClumplet(type, content);
    }

    public synchronized  Clumplet newClumplet(int type){
        return new ClumpletImpl(type, new byte[] {});
    }


    public synchronized  Clumplet newClumplet(int type, int c)
        {
        // Unfortuanatly firebird does not seem to read items in the blob parameter buffer
        // correclly if they are encoded in more then two bytes.

        // As a temporary work around I have only used two bytes to encode these values and
        // put a simple check in to catch most potential problems. I have made a note to myself
        // to come back and review this soon.
        if( c >= 65536 )
            throw new RuntimeException("Clumplet value out of range.");

        return new ClumpletImpl(type, new byte[] {(byte)(c>>8), (byte)c});
        }

    public synchronized  Clumplet newClumplet(int type, byte[] content) {
        return new ClumpletImpl(type, content);
    }

    public synchronized  Clumplet cloneClumplet(Clumplet c) {
        if (c == null) {
            return null;
        }
        return ((ClumpletImpl)c).cloneClumplet();
    }

      // Handle declaration methods
    public synchronized isc_db_handle get_new_isc_db_handle()
      {
      return new isc_db_handle_impl();
      }

    public synchronized isc_tr_handle get_new_isc_tr_handle()
        {
        return new isc_tr_handle_impl();
        }

    public synchronized isc_stmt_handle get_new_isc_stmt_handle()
        {
        return new isc_stmt_handle_impl();
        }


    public synchronized isc_blob_handle get_new_isc_blob_handle()
        {
        return new isc_blob_handle_impl();
        }

    private byte[] clupletToBytes(Clumplet bpb)
        {
        if(bpb == null)
            return null;
        try
            {
            return ((ClumpletImpl)bpb).toBytes();
            }
        catch (IOException e)
            {
            e.printStackTrace();  //To change body of catch statement use Options | File Templates.
            }

        return new byte[0];
        }

    private native void nativeInitilize(String firebirdDllName);

    // isc_create_database ---------------------------------------------------------------------------------------------
    public void isc_create_database(String file_name, isc_db_handle db_handle, Clumplet c) throws GDSException
        {
        if (db_handle == null)
            {
            throw new GDSException(ISCConstants.isc_bad_db_handle);
            }


        final byte[] dpbBytes = clupletToBytes(c);
        final byte[] arg = new byte[dpbBytes.length+1];
        arg[0] = 1;
        System.arraycopy( dpbBytes, 0, arg, 1, dpbBytes.length );



        synchronized(this)
            {
            native_isc_create_database( getServerUrl(file_name), db_handle, arg );
            }
        }

    private native void native_isc_create_database(String file_name, isc_db_handle db_handle, byte[] dpbBytes);

	private String getServerUrl(String file_name) throws GDSException
		{
    if( this.getGdsType() == GDSType.NATIVE )
        return getRemoteServerUrl(file_name);
    else if(this.getGdsType() == GDSType.NATIVE_EMBEDDED)
        return getEmbeddedServerUrl(file_name);
    else
        throw new RuntimeException("Unrecognized gds type.");
    }

    private String getRemoteServerUrl(String file_name) throws GDSException
        {
        if(log != null)
        log.debug("Original file name: "+file_name);

        DbAttachInfo dbai = new DbAttachInfo(file_name);

        final String fileName;
        if( dbai.getFileName().indexOf(':') == -1 && dbai.getFileName().startsWith("/") == false )
        {
         fileName = dbai.getServer() +"/"+ dbai.getPort() +":"+"/"  +dbai.getFileName();
        }
        else
        fileName = dbai.getServer() +"/"+ dbai.getPort() +":"+dbai.getFileName();

        if(log != null)
        log.debug("File name for native code: "+fileName);

        return fileName;
        }

    private String getEmbeddedServerUrl(String file_name) throws GDSException
            {
            if(log != null)
            log.debug("Original file name: "+file_name);

            DbAttachInfo dbai = new DbAttachInfo(file_name);

            final String fileName;
           // if( dbai.getFileName().indexOf(':') == -1 && dbai.getFileName().startsWith("/") == false )
           // {
           //  fileName = "/"  +dbai.getFileName();
            //}
            //else
            fileName = dbai.getFileName();

            if(log != null)
            log.debug("File name for native code: "+fileName);

            return fileName;
            }

    // isc_attach_database ---------------------------------------------------------------------------------------------
    public void isc_attach_database(String file_name, isc_db_handle db_handle, Clumplet c) throws GDSException
        {
        if (db_handle == null)
            {
            throw new GDSException(ISCConstants.isc_bad_db_handle);
            }





        final byte[] dpbBytes = clupletToBytes(c);



        final byte[] arg = new byte[dpbBytes.length+1];
        arg[0] = 1;
        System.arraycopy( dpbBytes, 0, arg, 1, dpbBytes.length );




        synchronized(this)
            {
		    native_isc_attach_database( getServerUrl(file_name), db_handle, arg );
            }
        }

    private native void native_isc_attach_database(String file_name, isc_db_handle db_handle, byte[] dpbBytes);

    // isc_attach_database ---------------------------------------------------------------------------------------------
    public byte[] isc_database_info(isc_db_handle db_handle, byte[] items, int buffer_length) throws GDSException
        {
        synchronized(db_handle)
            {
            final byte[] returnValue = new byte[buffer_length];

            native_isc_database_info( db_handle, items.length,  items, buffer_length, returnValue );

            return returnValue;
            }
        }

    public native void native_isc_database_info(isc_db_handle db_handle, int item_length, byte[] items, int buffer_length, byte[] buffer) throws GDSException;

    // isc_detach_database ---------------------------------------------------------------------------------------------
    public void isc_detach_database(isc_db_handle db_handle) throws GDSException
        {
        isc_db_handle_impl db = (isc_db_handle_impl) db_handle;

        if (db == null)
            {
            throw new GDSException(ISCConstants.isc_bad_db_handle);
            }

        synchronized(this)
            {
            if (db.hasTransactions())
                {
                throw new GDSException(ISCConstants.isc_open_trans, db.getOpenTransactionCount());
                }

            native_isc_detach_database(db_handle);
            }
        }

    public native void native_isc_detach_database(isc_db_handle db_handle) throws GDSException;

    // isc_drop_database ---------------------------------------------------------------------------------------------
    public void isc_drop_database(isc_db_handle db_handle) throws GDSException
        {
        if (db_handle == null)
            {
            throw new GDSException(ISCConstants.isc_bad_db_handle);
            }

        synchronized(this)
            {
            native_isc_drop_database(db_handle);
            }
        }

    public native void native_isc_drop_database(isc_db_handle db_handle) throws GDSException;


    // isc_expand_dpb ---------------------------------------------------------------------------------------------
    public byte[] isc_expand_dpb(byte[] dpb, int dpb_length,
                                 int param, Object[] params) throws GDSException {
        return dpb;
    }

    // isc_start_transaction ---------------------------------------------------------------------------------------------
    public void isc_start_transaction(isc_tr_handle tr_handle, isc_db_handle db_handle, byte[] tpb) throws GDSException
        {
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        isc_db_handle_impl db = (isc_db_handle_impl) db_handle;

        if (tr == null) {
            throw new GDSException(ISCConstants.isc_bad_trans_handle);
        }

        if (db == null) {
            throw new GDSException(ISCConstants.isc_bad_db_handle);
        }

        synchronized(db_handle)
            {
            if (tr.getState() != isc_tr_handle.NOTRANSACTION)
                    throw new GDSException(ISCConstants.isc_tra_state);

            tr.setState(isc_tr_handle.TRANSACTIONSTARTING);

            final byte[] arg = new byte[tpb.length+1];
            arg[0] = 3;
            System.arraycopy( tpb, 0, arg, 1, tpb.length );

            native_isc_start_transaction(tr_handle, db_handle, arg);

            tr.setDbHandle((isc_db_handle_impl)db_handle);

            tr.setState(isc_tr_handle.TRANSACTIONSTARTED);
            }
        }

    public native void native_isc_start_transaction(isc_tr_handle tr_handle,
                                      isc_db_handle db_handle,
//                                Set tpb) throws GDSException;
                                      byte[] tpb) throws GDSException;

    // isc_commit_transaction ---------------------------------------------------------------------------------------------
    public void isc_commit_transaction(isc_tr_handle tr_handle) throws GDSException
        {
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        isc_db_handle_impl db = (isc_db_handle_impl)tr.getDbHandle();

        synchronized(db)
            {
            if (tr.getState() != isc_tr_handle.TRANSACTIONSTARTED && tr.getState() != isc_tr_handle.TRANSACTIONPREPARED) {
                    throw new GDSException(ISCConstants.isc_tra_state);
                }

            tr.setState(isc_tr_handle.TRANSACTIONCOMMITTING);

            native_isc_commit_transaction(tr_handle);

            tr.setState(isc_tr_handle.NOTRANSACTION);

            tr.unsetDbHandle();
            }
        }


    public native void native_isc_commit_transaction(isc_tr_handle tr_handle) throws GDSException;

    // isc_rollback_transaction ---------------------------------------------------------------------------------------------
    public void isc_rollback_transaction(isc_tr_handle tr_handle) throws GDSException
         {
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        if (tr == null) {
        throw new GDSException(ISCConstants.isc_bad_trans_handle);
        }
        isc_db_handle_impl db = (isc_db_handle_impl)tr.getDbHandle();

         synchronized(db)
            {
            if (tr.getState() == isc_tr_handle.NOTRANSACTION)
            	{
                throw new GDSException(ISCConstants.isc_tra_state);
            	}


            tr.setState(isc_tr_handle.TRANSACTIONROLLINGBACK);

             native_isc_rollback_transaction(tr_handle);

             tr.setState(isc_tr_handle.NOTRANSACTION);
             tr.unsetDbHandle();
            }
         }

    public native void native_isc_rollback_transaction(isc_tr_handle tr_handle) throws GDSException;


    // isc_commit_retaining ---------------------------------------------------------------------------------------------
    public void isc_commit_retaining(isc_tr_handle tr_handle) throws GDSException
        {
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        if (tr == null) {
            throw new GDSException(ISCConstants.isc_bad_trans_handle);
        }
        isc_db_handle_impl db = (isc_db_handle_impl)tr.getDbHandle();

        synchronized(db)
            {
            if (tr.getState() != isc_tr_handle.TRANSACTIONSTARTED && tr.getState() != isc_tr_handle.TRANSACTIONPREPARED) {
                    throw new GDSException(ISCConstants.isc_tra_state);
                }

            tr.setState(isc_tr_handle.TRANSACTIONCOMMITTING);

            native_isc_commit_retaining(tr_handle);

            tr.setState(isc_tr_handle.TRANSACTIONSTARTED);
            }
        }

    public native void native_isc_commit_retaining(isc_tr_handle tr_handle) throws GDSException;

    // isc_prepare_transaction ---------------------------------------------------------------------------------------------
    public void isc_prepare_transaction(isc_tr_handle tr_handle) throws GDSException
        {
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        if (tr == null) {
            throw new GDSException(ISCConstants.isc_bad_trans_handle);
        }
        isc_db_handle_impl db = (isc_db_handle_impl)tr.getDbHandle();

        synchronized(db)
            {
            if (tr.getState() != isc_tr_handle.TRANSACTIONSTARTED) {
                    throw new GDSException(ISCConstants.isc_tra_state);
            }
            tr.setState(isc_tr_handle.TRANSACTIONPREPARING);

            native_isc_prepare_transaction(tr_handle);

            tr.setState(isc_tr_handle.TRANSACTIONPREPARED);
            }
        }

    public native void native_isc_prepare_transaction(isc_tr_handle tr_handle) throws GDSException;

    // isc_prepare_transaction2 ---------------------------------------------------------------------------------------------
    public void isc_prepare_transaction2(isc_tr_handle tr_handle, byte[] bytes) throws GDSException
        {
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        if (tr == null) {
            throw new GDSException(ISCConstants.isc_bad_trans_handle);
        }
        isc_db_handle_impl db = (isc_db_handle_impl)tr.getDbHandle();

        synchronized(db)
            {
            if (tr.getState() != isc_tr_handle.TRANSACTIONSTARTED) {
                    throw new GDSException(ISCConstants.isc_tra_state);
                }
            tr.setState(isc_tr_handle.TRANSACTIONPREPARING);

            native_isc_prepare_transaction2( tr_handle, bytes );

            tr.setState(isc_tr_handle.TRANSACTIONPREPARED);
            }
        }

    public native void native_isc_prepare_transaction2(isc_tr_handle tr_handle, byte[] bytes) throws GDSException;

    // isc_rollback_retaining ---------------------------------------------------------------------------------------------
    public void isc_rollback_retaining(isc_tr_handle tr_handle) throws GDSException
        {
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        if (tr == null) {
            throw new GDSException(ISCConstants.isc_bad_trans_handle);
        }
        isc_db_handle_impl db = (isc_db_handle_impl)tr.getDbHandle();

        synchronized(db)
            {
            if (tr.getState() != isc_tr_handle.TRANSACTIONSTARTED && tr.getState() != isc_tr_handle.TRANSACTIONPREPARED) {
                    throw new GDSException(ISCConstants.isc_tra_state);
                }
            tr.setState(isc_tr_handle.TRANSACTIONROLLINGBACK);

            native_isc_rollback_retaining( tr_handle );

            tr.setState(isc_tr_handle.TRANSACTIONSTARTED);
            }
        }

    public native void native_isc_rollback_retaining(isc_tr_handle tr_handle) throws GDSException;

    // isc_dsql_allocate_statement ---------------------------------------------------------------------------------------------
    public void isc_dsql_allocate_statement(isc_db_handle db_handle,
                                            isc_stmt_handle stmt_handle) throws GDSException
        {
        isc_db_handle_impl db = (isc_db_handle_impl) db_handle;
        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;

        if (db_handle == null) {
            throw new GDSException(ISCConstants.isc_bad_db_handle);
        }

        if (stmt_handle == null) {
            throw new GDSException(ISCConstants.isc_bad_req_handle);
        }

        synchronized(db)
            {
            native_isc_dsql_allocate_statement( db_handle, stmt_handle );

            stmt.setRsr_rdb( (isc_db_handle_impl)db_handle );
            stmt.setAllRowsFetched(false);
            }
        }

    public native void native_isc_dsql_allocate_statement(isc_db_handle db_handle,
                                            isc_stmt_handle stmt_handle) throws GDSException;


    // isc_dsql_alloc_statement2 ---------------------------------------------------------------------------------------------
    public void isc_dsql_alloc_statement2(isc_db_handle db_handle,
                                          isc_stmt_handle stmt_handle) throws GDSException
        {
        throw new GDSException(ISCConstants.isc_wish_list);
        }

    public native void native_isc_dsql_alloc_statement2(isc_db_handle db_handle,
                                          isc_stmt_handle stmt_handle) throws GDSException;



    // isc_dsql_describe ---------------------------------------------------------------------------------------------
    public XSQLDA isc_dsql_describe(isc_stmt_handle stmt_handle,
                                    int da_version) throws GDSException
        {
        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;


        if (stmt == null) {
            throw new GDSException(ISCConstants.isc_bad_req_handle);
        }

        synchronized(stmt.getRsr_rdb())
            {

            stmt.setInSqlda(native_isc_dsql_describe( stmt_handle, da_version ));    /* @todo    setInSqlda here ?? */

            return  stmt_handle.getInSqlda();
            }
        }

    public native XSQLDA native_isc_dsql_describe(isc_stmt_handle stmt_handle,
                                        int da_version) throws GDSException;


    // isc_dsql_describe_bind ---------------------------------------------------------------------------------------------
    public XSQLDA isc_dsql_describe_bind(isc_stmt_handle stmt_handle,
                                         int da_version) throws GDSException
        {
        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;

        synchronized(stmt.getRsr_rdb())
            {
            stmt.setInSqlda(native_isc_dsql_describe_bind( stmt_handle, da_version ));

            return stmt_handle.getInSqlda();
            }
        }

    public native XSQLDA native_isc_dsql_describe_bind(isc_stmt_handle stmt_handle,
                                             int da_version) throws GDSException;


    // isc_dsql_execute ---------------------------------------------------------------------------------------------
    public void isc_dsql_execute(isc_tr_handle tr_handle, isc_stmt_handle stmt_handle, int da_version, XSQLDA xsqlda) throws GDSException
        {
        isc_dsql_execute2( tr_handle, stmt_handle, da_version, xsqlda, null );
        }

//    public synchronized native void native_isc_dsql_execute(isc_tr_handle tr_handle, isc_stmt_handle stmt_handle, int da_version, XSQLDA xsqlda) throws GDSException;

    // isc_dsql_execute2 ---------------------------------------------------------------------------------------------
    public void isc_dsql_execute2(isc_tr_handle tr_handle,
                                  isc_stmt_handle stmt_handle,
                                  int da_version,
                                  XSQLDA in_xsqlda,
                                  XSQLDA out_xsqlda) throws GDSException
        {
        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;

        synchronized(stmt.getRsr_rdb())
            {
            native_isc_dsql_execute2( tr_handle, stmt_handle, da_version, in_xsqlda, out_xsqlda ); /* @todo Fetch Statements */

            if( out_xsqlda != null )
                    {
                    //this would be an Execute procedure
                    stmt.ensureCapacity(1);
                    readSQLData( out_xsqlda, stmt );
                    stmt.setAllRowsFetched(true);
                    stmt.setIsSingletonResult(true);
                    }
                else
                    {
                    stmt.setAllRowsFetched(false);
                    stmt.setIsSingletonResult(false);
                    }
            }
        }

    public native void native_isc_dsql_execute2(isc_tr_handle tr_handle,
                                  isc_stmt_handle stmt_handle,
                                  int da_version,
                                  XSQLDA in_xsqlda,
                                  XSQLDA out_xsqlda) throws GDSException;

    // isc_dsql_execute_immediateX ---------------------------------------------------------------------------------------------
    public void isc_dsql_execute_immediate(isc_db_handle db_handle,
                                          isc_tr_handle tr_handle,
                                          String statement,
                                          int dialect,
                                          XSQLDA xsqlda) throws GDSException
        {
        isc_dsql_exec_immed2(db_handle, tr_handle, statement, dialect, xsqlda, null);
        }

    public void isc_dsql_execute_immediate(isc_db_handle db_handle,
                                          isc_tr_handle tr_handle,
                                          String statement,
                                          String encoding,
                                          int dialect,
                                          XSQLDA xsqlda) throws GDSException
        {
        isc_dsql_exec_immed2(db_handle, tr_handle, statement, encoding, dialect, xsqlda, null);
        }


    public void isc_dsql_exec_immed2(isc_db_handle db_handle,
                                    isc_tr_handle tr_handle,
                                    String statement,
                                    int dialect,
                                    XSQLDA in_xsqlda,
                                    XSQLDA out_xsqlda) throws GDSException
        {
        isc_dsql_exec_immed2(db_handle, tr_handle, statement, "NONE", dialect, in_xsqlda, out_xsqlda);
        }

    public void isc_dsql_exec_immed2(isc_db_handle db_handle,
                                     isc_tr_handle tr_handle,
                                     String statement,
                                     String encoding,
                                     int dialect,
                                     XSQLDA in_xsqlda,
                                     XSQLDA out_xsqlda) throws GDSException
        {
        synchronized(db_handle)
            {
            native_isc_dsql_exec_immed2( db_handle, tr_handle, statement, encoding, dialect, in_xsqlda, out_xsqlda );
            }
        }

    public native void native_isc_dsql_exec_immed2(isc_db_handle db_handle,
                                     isc_tr_handle tr_handle,
                                     String statement,
                                     String encoding,
                                     int dialect,
                                     XSQLDA in_xsqlda,
                                     XSQLDA out_xsqlda) throws GDSException;



    // isc_dsql_fetch ---------------------------------------------------------------------------------------------
    public void isc_dsql_fetch(isc_stmt_handle stmt_handle,
                              int da_version,
                              XSQLDA xsqlda, int fetchSize) throws GDSException
        {
        fetchSize = 1;

        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;
        isc_db_handle_impl db = stmt.getRsr_rdb();

         synchronized(db)
            {
            if (stmt_handle == null) {
                throw new GDSException(ISCConstants.isc_bad_req_handle);
            }

            if (xsqlda == null) {
                throw new GDSException(ISCConstants.isc_dsql_sqlda_err);
            }

            if (fetchSize <= 0) {
                throw new GDSException(ISCConstants.isc_dsql_sqlda_err);
            }
            // Apply fetchSize
                        //Fetch next batch of rows
            stmt.ensureCapacity(fetchSize);

            for( int i = 0; i < fetchSize; i++ )
                {
                boolean isRowPresent = native_isc_dsql_fetch( stmt_handle, da_version, xsqlda, fetchSize );
                if( isRowPresent )
                    {
                    readSQLData( xsqlda, stmt );
                    }
                else
                    {
                    stmt.setAllRowsFetched(true);
                    return;
                    }
                }
            }
        }

    public void readSQLData(XSQLDA xsqlda, isc_stmt_handle_impl stmt)
        {
        // This only works if not (port->port_flags & PORT_symmetric)
        int numCols = xsqlda.sqld;
        byte[][] row = new byte[numCols][];
        byte[] buffer;
        for (int i = 0; i < numCols; i++) {

       // isc_vax_integer( xsqlda.sqlvar[i].sqldata, 0, xsqlda.sqlvar[i].sqldata.length );

            row[i] = xsqlda.sqlvar[i].sqldata;
        }
        if (stmt != null)
            stmt.addRow(row);
        }

    public native boolean native_isc_dsql_fetch(isc_stmt_handle stmt_handle,
                               int da_version,
                               XSQLDA xsqlda, int fetchSize) throws GDSException;


    // isc_dsql_free_statement ---------------------------------------------------------------------------------------------
    public void isc_dsql_free_statement(isc_stmt_handle stmt_handle,
                                        int option) throws GDSException
        {
        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;
        isc_db_handle_impl db = stmt.getRsr_rdb();

        synchronized(db)
            {
            if (stmt_handle == null) {
                throw new GDSException(ISCConstants.isc_bad_req_handle);
            }

            //Does not seem to be possible or necessary to close
            //an execute procedure statement.
            if (stmt.getIsSingletonResult() && option == ISCConstants.DSQL_close)
            {
                return;
            }

            if (option == ISCConstants.DSQL_drop) {
                        stmt.setInSqlda(null);
                        stmt.setOutSqlda(null);
                    }

            native_isc_dsql_free_statement(stmt_handle, option);       /* @todo null out xsqlda ? */
            }
        }

    public native void native_isc_dsql_free_statement(isc_stmt_handle stmt_handle,
                                        int option) throws GDSException;

    // isc_dsql_free_statement ---------------------------------------------------------------------------------------------
    public XSQLDA isc_dsql_prepare(isc_tr_handle tr_handle,
                                   isc_stmt_handle stmt_handle,
                                   String statement,
                                   int dialect) throws GDSException
        {
        return isc_dsql_prepare(tr_handle, stmt_handle, statement, "NONE", dialect);
        }

    public native XSQLDA native_isc_dsql_prepare(isc_tr_handle tr_handle,
                                   isc_stmt_handle stmt_handle,
                                   String statement,
                                   int dialect) throws GDSException;


    // isc_dsql_free_statement ---------------------------------------------------------------------------------------------
    public XSQLDA isc_dsql_prepare(isc_tr_handle tr_handle,
                                   isc_stmt_handle stmt_handle,
                                   String statement,
                                   String encoding,
                                   int dialect) throws GDSException
        {
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;
        isc_db_handle_impl db = stmt.getRsr_rdb();

        synchronized(db)
            {
            if (tr_handle == null) {
                throw new GDSException(ISCConstants.isc_bad_trans_handle);
            }

            if (stmt_handle == null) {
                throw new GDSException(ISCConstants.isc_bad_req_handle);
            }

            stmt.setInSqlda(null);
            stmt.setOutSqlda(null);

            stmt.setOutSqlda(native_isc_dsql_prepare(tr_handle, stmt_handle, statement,  dialect ));

            return stmt_handle.getOutSqlda();
            }
        }

    public native XSQLDA native_isc_dsql_prepare(isc_tr_handle tr_handle,
                                   isc_stmt_handle stmt_handle,
                                   String statement,
                                   String encoding,
                                   int dialect) throws GDSException;

    // isc_dsql_free_statement ---------------------------------------------------------------------------------------------
    public void isc_dsql_set_cursor_name(isc_stmt_handle stmt_handle,
                                         String cursor_name,
                                         int type) throws GDSException
        {
        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;
        isc_db_handle_impl db = stmt.getRsr_rdb();

        synchronized(db)
            {
            if (stmt_handle == null) {
                throw new GDSException(ISCConstants.isc_bad_req_handle);
            }

            native_isc_dsql_set_cursor_name(stmt_handle, cursor_name, type);
            }
        }

    public native void native_isc_dsql_set_cursor_name(isc_stmt_handle stmt_handle,
                                         String cursor_name,
                                         int type) throws GDSException;


    // isc_dsql_sql_info ---------------------------------------------------------------------------------------------
    public byte[] isc_dsql_sql_info(isc_stmt_handle stmt_handle, byte[] items, int buffer_length) throws GDSException
        {
        synchronized(((isc_stmt_handle_impl)stmt_handle).getRsr_rdb())
            {
            return native_isc_dsql_sql_info(stmt_handle, items, buffer_length);
            }
        }

    public native byte[] native_isc_dsql_sql_info(isc_stmt_handle stmt_handle, byte[] items, int buffer_length) throws GDSException;
    // getSqlCounts ---------------------------------------------------------------------------------------------
    private static byte[] stmtInfo = new byte[]
        {ISCConstants.isc_info_sql_records,
         ISCConstants.isc_info_sql_stmt_type,
         ISCConstants.isc_info_end};
    private static int INFO_SIZE = 128;

    public void getSqlCounts(isc_stmt_handle stmt_handle) throws GDSException {
        isc_stmt_handle_impl stmt = (isc_stmt_handle_impl) stmt_handle;
        byte[] buffer = isc_dsql_sql_info(stmt, /*stmtInfo.length,*/ stmtInfo, INFO_SIZE);
        int pos = 0;
        int length;
        int type;
        while ((type = buffer[pos++]) != ISCConstants.isc_info_end) {
            length = isc_vax_integer(buffer, pos, 2);
            pos += 2;
            switch (type) {
            case ISCConstants.isc_info_sql_records:
                int l;
                int t;
                while ((t = buffer[pos++]) != ISCConstants.isc_info_end) {
                    l = isc_vax_integer(buffer, pos, 2);
                    pos += 2;
                    switch (t) {
                    case ISCConstants.isc_info_req_insert_count:
                        stmt.setInsertCount(isc_vax_integer(buffer, pos, l));
                        break;
                    case ISCConstants.isc_info_req_update_count:
                        stmt.setUpdateCount(isc_vax_integer(buffer, pos, l));
                        break;
                    case ISCConstants.isc_info_req_delete_count:
                        stmt.setDeleteCount(isc_vax_integer(buffer, pos, l));
                        break;
                    case ISCConstants.isc_info_req_select_count:
                        stmt.setSelectCount(isc_vax_integer(buffer, pos, l));
                        break;
                    default:
                        break;
                    }
                    pos += l;
                }
                break;
            case ISCConstants.isc_info_sql_stmt_type:
                stmt.setStatementType(isc_vax_integer(buffer, pos, length));
                pos += length;
                break;
            default:
                pos += length;
                break;
            }
        }
    }

    // isc_vax_integer ---------------------------------------------------------------------------------------------
    public int isc_vax_integer(byte[] buffer, int pos, int length) {
        int value;
        int shift;

        value = shift = 0;

        int i = pos;
        while (--length >= 0) {
            value += (buffer[i++] & 0xff) << shift;
            shift += 8;
        }
        return value;
    }

    // isc_create_blob2 ---------------------------------------------------------------------------------------------
    public void isc_create_blob2(isc_db_handle db_handle,
                                 isc_tr_handle tr_handle,
                                 isc_blob_handle blob_handle,
                                 Clumplet bpbClumpet) throws GDSException
        {
        isc_db_handle_impl db = (isc_db_handle_impl) db_handle;
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        isc_blob_handle_impl blob = (isc_blob_handle_impl) blob_handle;

        if (db == null) {
            throw new GDSException(ISCConstants.isc_bad_db_handle);
        }
        if (tr == null) {
            throw new GDSException(ISCConstants.isc_bad_trans_handle);
        }
        if (blob == null) {
            throw new GDSException(ISCConstants.isc_bad_segstr_handle);
        }

        final byte[] bpb = blobParameterClumpetToBuffer(bpbClumpet);

        synchronized(db)
            {
            native_isc_create_blob2( db_handle, tr_handle, blob_handle, bpb );


            blob.setDb(db);
            blob.setTr(tr);
            tr.addBlob(blob);
            }
        }


    private native void native_isc_create_blob2(isc_db_handle db, isc_tr_handle tr, isc_blob_handle blob, byte[] dpbBytes);



    // isc_open_blob2 ---------------------------------------------------------------------------------------------
    public void isc_open_blob2(isc_db_handle db_handle,
                               isc_tr_handle tr_handle,
                               isc_blob_handle blob_handle,
                               Clumplet bpbClumpet) throws GDSException
        {
        isc_db_handle_impl db = (isc_db_handle_impl) db_handle;
        isc_tr_handle_impl tr = (isc_tr_handle_impl) tr_handle;
        isc_blob_handle_impl blob = (isc_blob_handle_impl) blob_handle;

        if (db == null) {
            throw new GDSException(ISCConstants.isc_bad_db_handle);
        }
        if (tr == null) {
            throw new GDSException(ISCConstants.isc_bad_trans_handle);
        }
        if (blob == null) {
            throw new GDSException(ISCConstants.isc_bad_segstr_handle);
        }

        final byte[] bpb = blobParameterClumpetToBuffer(bpbClumpet);

        synchronized(db)
            {
            native_isc_open_blob2( db_handle, tr_handle, blob_handle, bpb );

            blob.setDb(db);
            blob.setTr(tr);
            tr.addBlob(blob);
            }
        }

    private native void native_isc_open_blob2(isc_db_handle db, isc_tr_handle tr, isc_blob_handle blob, byte[] dpbBytes);


    private byte[] blobParameterClumpetToBuffer(Clumplet bpbClumpet)
        {
        final byte[] bpbBytes = clupletToBytes(bpbClumpet);
        if(bpbBytes == null)
            return null;
        else
            {
            final byte[] bpb = new byte[bpbBytes.length+1];

            bpb[0] = 1;
            System.arraycopy( bpbBytes, 0, bpb, 1, bpbBytes.length );

            return bpb;
            }
        }


    // isc_get_segment ---------------------------------------------------------------------------------------------
    public byte[] isc_get_segment(isc_blob_handle blob, int maxread) throws GDSException
        {
        synchronized(((isc_blob_handle_impl)blob).getDb())
            {
            return native_isc_get_segment( blob, maxread );
            }
        }

    public native byte[] native_isc_get_segment(isc_blob_handle blob, int maxread) throws GDSException;

    // isc_put_segment ---------------------------------------------------------------------------------------------
    public void isc_put_segment(isc_blob_handle blob_handle, byte[] buffer) throws GDSException
        {
        synchronized(((isc_blob_handle_impl)blob_handle).getDb())
            {
         native_isc_put_segment( blob_handle, buffer );
            }
        }

    public native void native_isc_put_segment(isc_blob_handle blob_handle, byte[] buffer) throws GDSException;


    // isc_close_blob ---------------------------------------------------------------------------------------------
    public void isc_close_blob(isc_blob_handle blob_handle) throws GDSException
        {
//        isc_blob_handle_impl blob = (isc_blob_handle_impl) blob_handle;
//        isc_db_handle_impl db = blob.getDb();
//        if (db == null) {
//            throw new GDSException(ISCConstants.isc_bad_db_handle);
//        }
//        isc_tr_handle_impl tr = blob.getTr();
//        if (tr == null) {
//            throw new GDSException(ISCConstants.isc_bad_trans_handle);
//        }

        synchronized(((isc_blob_handle_impl)blob_handle).getDb())
            {
        native_isc_close_blob( blob_handle );
            }

//        tr.removeBlob(blob);
        }

    public native void native_isc_close_blob(isc_blob_handle blob) throws GDSException;

	public byte[] isc_blob_info(isc_blob_handle handle, byte[] items, int buffer_length)
		throws GDSException
		{
		isc_blob_handle_impl blob = (isc_blob_handle_impl) handle;
        synchronized (blob)
            {
            return native_isc_blob_info(blob, items, buffer_length);
            }
		}

    public native byte[] native_isc_blob_info(isc_blob_handle_impl handle, byte[] items, int buffer_length)
		throws GDSException;

	public void isc_seek_blob(isc_blob_handle handle, int position, int mode)
		throws GDSException
		{
		isc_blob_handle_impl blob = (isc_blob_handle_impl) handle;
        synchronized (handle)
            {
            native_isc_seek_blob(blob, position, mode);
		    }
        }

    public native void native_isc_seek_blob(isc_blob_handle_impl handle, int position, int mode)
		throws GDSException;



    protected static class DbAttachInfo {
        private String server = "localhost";
        private int port = 3050;
        private String fileName;

        public String getConnectionString()
            {
            if( getServer().compareToIgnoreCase("loopback") == 0 ||
                getServer().compareToIgnoreCase("localhost") == 0)
                {
                return getFileName();
                }
            else
                {
                return getServer()+"/"+getPort()+":"+getFileName();
                }
            }

        public DbAttachInfo(String connectInfo) throws GDSException {

            if (connectInfo == null) {
                throw new GDSException("Connection string missing");
            }

            // allows standard syntax //host:port/....
            // and old fb syntax host/port:....
            connectInfo = connectInfo.trim();
            String node_name;
            char hostSepChar;
            char portSepChar;
            if (connectInfo.startsWith("//")){
                connectInfo = connectInfo.substring(2);
                hostSepChar = '/';
                portSepChar = ':';
            }
                else {
                hostSepChar = ':';
                portSepChar = '/';
            }

            int sep = connectInfo.indexOf(hostSepChar);
            if (sep == 0 || sep == connectInfo.length() - 1) {
                throw new GDSException("Bad connection string: '"+hostSepChar+"' at beginning or end of:" + connectInfo +  ISCConstants.isc_bad_db_format);
            }
            else if (sep > 0) {
                server = connectInfo.substring(0, sep);
                fileName = connectInfo.substring(sep + 1);
                int portSep = server.indexOf(portSepChar);
                if (portSep == 0 || portSep == server.length() - 1) {
                    throw new GDSException("Bad server string: '"+portSepChar+"' at beginning or end of: " + server +  ISCConstants.isc_bad_db_format);
                }
                else if (portSep > 0) {
                    port = Integer.parseInt(server.substring(portSep + 1));
                    server = server.substring(0, portSep);
                }
            }
            else if (sep == -1)
            {
                fileName = connectInfo;
            } // end of if ()

        }

        public DbAttachInfo(String server, Integer port, String fileName) throws GDSException
        {
            if (fileName == null || fileName.equals(""))
            {
                throw new GDSException("null filename in DbAttachInfo");
            } // end of if ()
            if (server != null)
            {
                this.server = server;
            } // end of if ()
            if (port != null)
            {
                this.port = port.intValue();
            } // end of if ()
            this.fileName = fileName;
            if (fileName == null || fileName.equals(""))
            {
                throw new GDSException("null filename in DbAttachInfo");
            } // end of if ()

        }

        public String getServer() {
            return server;
        }

        public int getPort() {
            return port;
        }

        public String getFileName() {
            return fileName;
        }
    }
    }
