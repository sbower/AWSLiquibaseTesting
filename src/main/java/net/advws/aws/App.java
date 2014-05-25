package net.advws.aws;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.FileSystemResourceAccessor;

public class App 
{
	private static final String CHANGE_LOG_PATH = "changeLogPath";	
	
	private String pathToChangeLog;

	public String getPathToChangeLog() {
		return pathToChangeLog;
	}

	public void setPathToChangeLog(String pathToChangeLog) {
		this.pathToChangeLog = pathToChangeLog;
	}

	public App() {
	}
	
  public static void main( String[] args ) throws Exception {
    App testApp = new App();
    GetSpotInstance si = new GetSpotInstance();
    	
    OptionParser parser = new OptionParser();
    parser.accepts(CHANGE_LOG_PATH, "Path to liquibase changelog to test").withRequiredArg();
  	
    OptionSet options = null;
    try {
  		options = parser.parse( args );
  	} catch (OptionException e) {
  		System.out.println(e.getMessage() + "\n");
  		System.exit (1); 
  	}
    	 
  	if (options.has(CHANGE_LOG_PATH) && options.hasArgument(CHANGE_LOG_PATH)) {
  		testApp.setPathToChangeLog(options.valueOf(CHANGE_LOG_PATH).toString());
  	}
  	else {
  		try {
  			parser.printHelpOn(System.out);
  			System.exit (1); 
  		} catch (IOException e) {
  			e.printStackTrace();
  		}
  	}
  	
  	si.makeSpotRequestAndWaitForInstnace();		
  	System.out.println("Created instance: " + si.getInstnaceInfo().getReservations().get(0).getInstances().get(0).getPublicDnsName());
  	System.out.println("Booting...");
  	
    // give everything a chance to boot
    try {
      Thread.sleep(60*1000);
    } catch (InterruptedException e1) {
      e1.printStackTrace();
    }
    
  	testApp.runLog(si.getInstnaceInfo().getReservations().get(0).getInstances().get(0).getPublicIpAddress());
  	
  	si.cancelSpotRequest();
  	si.terminateInstances();
  	
  	System.exit (0);
  
  }
    	    
  public boolean runLog (String URL) {
    
    java.sql.Connection c = null;
    Liquibase liquibase = null;
    String connString = "jdbc:mysql://" + URL + "/kuldev?user=kuldev&password=kuldev";
        
    try {
        c = DriverManager.getConnection(connString);
  
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
        liquibase = new Liquibase(getPathToChangeLog(), new FileSystemResourceAccessor(), database);
        liquibase.update("");
  
    } catch (DatabaseException e) {
      System.out.println(e.toString());          
      return false;
    } catch (LiquibaseException e) {
      System.out.println(e.toString());
      return false;
    } catch (SQLException e) {
      System.out.println(e.toString());
      return false;
    } finally {
        if (c != null) {
            try {
                c.rollback();
                c.close();
            } catch (SQLException e) {
                return false;
            }
        }
    }
    
    return true;
    
  }
}
