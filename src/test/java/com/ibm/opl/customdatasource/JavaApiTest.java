package com.ibm.opl.customdatasource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.Assert.*;

import org.junit.Test;

import ilog.concert.IloException;
import ilog.opl.IloOplDataSource;
import ilog.opl.IloOplErrorHandler;
import ilog.opl.IloOplException;
import ilog.opl.IloOplFactory;
import ilog.opl.IloOplModel;
import ilog.opl.IloOplModelDefinition;
import ilog.opl.IloOplRunConfiguration;

public class JavaApiTest {
  public static String OIL_DAT="models/oil.dat";
  public static String OIL_MOD="models/oil.mod";
  public static String CONFIG_RESOURCE = "models/oil_sqlite.xml";
  
  public static String CREATE_OIL_MOD="models/oil_create_db.mod";
  public static String CREATE_CONFIG_RESOURCE = "models/oil_create_db.xml";

  
  /**
   * Runs a model as a .mod file, using a set of .dat files, and a given jdbc configuration.
   * 
   * If connectionString is specified, it will be used instead of the url in the jdbc configuration,
   * allowing for tests with database which url is not static (ex: temporary test databases).
   * @param modFilename The .mod file
   * @param datFilenames An array of .dat files
   * @param jdbcConfigurationFile The jdbc configuration file
   * @param connectionString An override url
   * @throws IOException
   * @throws IloException
   */
  public final void runMod(String modFilename, String[] datFilenames, String jdbcConfigurationFile,
      String connectionString) throws IOException, IloException {
    // create OPL
    IloOplFactory.setDebugMode(true);
    IloOplFactory oplF = new IloOplFactory();
    IloOplErrorHandler errHandler = oplF.createOplErrorHandler(System.out);
    
    IloOplRunConfiguration rc = null;
    if (datFilenames == null || datFilenames.length == 0) {
      rc = oplF.createOplRunConfiguration(modFilename);
    }
    else {
      rc = oplF.createOplRunConfiguration(modFilename, datFilenames);
    }
    
    rc.setErrorHandler(errHandler);
    IloOplModel opl = rc.getOplModel();

    IloOplModelDefinition def = opl.getModelDefinition();

    //
    // Reads the JDBC configuration, initialize a JDBC custom data source
    // and sets the source in OPL.
    //
    JdbcConfiguration jdbcProperties = null;
    if (jdbcConfigurationFile != null) {
      jdbcProperties = new JdbcConfiguration();
      jdbcProperties.read(jdbcConfigurationFile);
      // we want to override connection string with conn string that has the actual temp db path
      if (connectionString != null)
        jdbcProperties.setUrl(connectionString);
      // Create the custom JDBC data source
      IloOplDataSource jdbcDataSource = new JdbcCustomDataSource(jdbcProperties, oplF, def);
      // Pass it to the model.
      opl.addDataSource(jdbcDataSource);
    }

    opl.generate();

    boolean success = false;
    if (opl.hasCplex()) {
      if (opl.getCplex().solve()) {
        success = true;
      }
    } else {
      if (opl.getCP().solve()) {
        success = true;
      }
    }
    if (success == true) {
      opl.postProcess();
      // write results
      if (jdbcProperties != null) {
        JdbcWriter writer = new JdbcWriter(jdbcProperties, def, opl);
        writer.customWrite();
      }
    }
  }
  
  /**
   * Tests the JdbcCustomReader/Writer by API.
   */
  @Test
  public final void testApiCall() {
    File tempdb = null;
    try {
      // create temp db
      tempdb = File.createTempFile("testApiCall", ".db");
      String connectionString = "jdbc:sqlite:" + tempdb.getAbsolutePath();
      Connection conn = null;
      try {
        // creates the db
        conn = DriverManager.getConnection(connectionString);
      } finally {
        if (conn != null)
          conn.close();
      }
      
      // use a .mod to create tmp database
      String createModFilename = new File(getClass().getResource(CREATE_OIL_MOD).getFile()).getAbsolutePath();
      String createJdbcConfigurationFile = new File(getClass().getResource(CREATE_CONFIG_RESOURCE).getFile()).getAbsolutePath();
      runMod(createModFilename, null, createJdbcConfigurationFile, connectionString);
      
      // now solve the oil model
      String modFilename = new File(getClass().getResource(OIL_MOD).getFile()).getAbsolutePath();
      String[] datFilenames = {new File(getClass().getResource(OIL_DAT).getFile()).getAbsolutePath()};
      String jdbcConfigurationFile = new File(getClass().getResource(CONFIG_RESOURCE).getFile()).getAbsolutePath();
      runMod(modFilename, datFilenames, jdbcConfigurationFile, connectionString);

    } catch (IloOplException ex) {
      ex.printStackTrace();
      fail("### OPL exception: " + ex.getMessage());
    } catch (IloException ex) {
      ex.printStackTrace();
      fail("### CONCERT exception: " + ex.getMessage());
    } catch (Exception ex) {
      ex.printStackTrace();
      fail("### UNEXPECTED UNKNOWN ERROR ...");
    } finally {
      if (tempdb != null) {
        tempdb.delete();
      }
    }
  }
}
