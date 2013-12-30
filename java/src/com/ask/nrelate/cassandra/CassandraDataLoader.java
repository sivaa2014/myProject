package com.ask.nrelate.cassandra;
import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.NoHostAvailableException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Created with IntelliJ IDEA.
 * User: sivakumar
 * Date: 26/11/13
 * Time: 7:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class CassandraDataLoader {
    static String KEYSPACE = "nrelate";
    static String tableName;
    static Session session;
    private Logger logger = Logger.getLogger(CassandraDataLoader.class.getName()) ;


    public void initialize(String tableName,String[] seeds) {
        this.tableName = tableName ;
        try {
            if (session == null) {
                Cluster.Builder builder = Cluster.builder();
                for (String seed : seeds) {
                    builder.addContactPoint(seed);
                }
                Cluster cluster = builder.build();
                session = cluster.connect(KEYSPACE);
            }
        } catch (NoHostAvailableException e) {
            throw new RuntimeException(e);
        }
    }
    public void insert(String filename) {
        PreparedStatement ps = session.prepare("INSERT INTO "+tableName+" (key, pixeldate,impressions,paidimpressions,ads,internals,externals,gross_revenue,net_revenue,gross_rpm,net_rpm,ad_ctr,internal_ctr) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)");
        BatchStatement batch = new BatchStatement();
        BufferedReader reader = null;
        Statement statement = null;
        //batch.setConsistencyLevel(ConsistencyLevel.ONE);
        String line;
        try{
             reader = new BufferedReader(new FileReader(filename));
        }catch (Exception e){
            logger.info("Input file not found");
            logger.exiting(CassandraDataLoader.class.getName(),"insert method");
        }
        int lineNumber = 1;
        try{
            while ((line = reader.readLine()) != null) {
                CassandraProperties cassandraProperties = new CassandraProperties();
                if (parse(cassandraProperties,line, lineNumber++)) {
                    try{
                        batch = new BatchStatement();
                        statement = ps.bind(cassandraProperties.getKey(), cassandraProperties.getPixeldate(), cassandraProperties.getImpressions(), cassandraProperties.getPaidimpressions(),cassandraProperties.getAds(), cassandraProperties.getInternals(), cassandraProperties.getExternals(),cassandraProperties.getGrossRevenue(),
                                cassandraProperties.getNetRevenue(), cassandraProperties.getGrossRPM(), cassandraProperties.getNetRPM(), cassandraProperties.getAdCTR(), cassandraProperties.getInternalCTR());
                        long start = System.currentTimeMillis();
                        batch.add(statement);
                        session.execute(batch);
                        logger.info("line number "+lineNumber+"date "+cassandraProperties.getPixeldate()+"key "+cassandraProperties.getKey()+"Impressions "+cassandraProperties.getImpressions()+"Time "+ (System.currentTimeMillis()-start));
                    }catch (Exception e){
                        System.out.println(e.getMessage());
                    }  finally {
                        session.execute(batch);
                    }
                }
            }
        }catch (Exception e){
            logger.info("EXCEPTION: while reading the file content"+e.getMessage());
        }
    }

    boolean parse(CassandraProperties cassandraProperties,String line, int lineNumber) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-M-dd");

        String[] values = line.split("\\t");
        if (values.length != 13) {
            System.out.println(values.length + " " + String.format("Invalid input '%s' at line %d", line, lineNumber));
            return false;
        }
        try {
            int i=0;
            cassandraProperties.setKey(values[i++].trim());
            Date pixeldate = simpleDateFormat.parse(values[i++].trim());
            cassandraProperties.setPixeldate(pixeldate);
            if(tableName.equals("nrelate_data_hourly")){
                cassandraProperties.setPixelhour(Integer.parseInt(values[i++].trim()));
            }
            cassandraProperties.setImpressions(Long.parseLong(values[i++].trim()));
            cassandraProperties.setPaidimpressions(Long.parseLong(values[i++].trim()));
            cassandraProperties.setAds(Long.parseLong(values[i++].trim()));
            cassandraProperties.setInternals(Long.parseLong(values[i++].trim()));
            cassandraProperties.setExternals(Long.parseLong(values[i++].trim()));
            cassandraProperties.setGrossRevenue(Double.parseDouble(values[i++].trim()));
            cassandraProperties.setNetRevenue(Double.parseDouble(values[i++].trim()));
            cassandraProperties.setGrossRPM(Float.parseFloat(values[i++].trim()));
            cassandraProperties.setNetRPM(Float.parseFloat(values[i++].trim()));
            cassandraProperties.setAdCTR(Float.parseFloat(values[i++].trim()));
            cassandraProperties.setInternalCTR(Float.parseFloat(values[i++].trim()));

            return true;
        } catch (NumberFormatException e) {
            System.out.println(String.format("Invalid number in input '%s' at line %d", line, lineNumber));
            return false;
        } catch (ParseException e){
            System.out.println("Exception while parsing the date "+e.getMessage());
            return false;
        }
    }
    public void shutdown() {
        if (session != null) {
            System.out.println("Shutting down the session");
            session.shutdown();

        }
    }
    public static void main(String args[]) {
        if(args.length!=3){
            System.out.println("Usage: java CassandraDataLoad TableName SeedsSeperatedByComma InputFilePathSeparatedByTab");
            System.out.println("Example: java CassandraDataLoad nrelate_data 10.168.6.95,10.31.137.47 /home/nrelate/nrelate_data.tsv");
            System.out.println("Exiting the program");
            System.exit(1);
        }
        String tableName = args[0];
        String seeds[] = args[1].split("\\,");
        String filename = args[2];
        CassandraDataLoader cassandraDataLoader = new CassandraDataLoader();
        cassandraDataLoader.initialize(tableName,seeds);
        cassandraDataLoader.insert(filename);
        cassandraDataLoader.shutdown();
        System.out.println("Process over");
        System.exit(0);
    }

}