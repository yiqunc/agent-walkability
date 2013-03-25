package org.mccaughey.pathGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlets.ProxyServlet;
import org.eclipse.jetty.servlets.ProxyServlet.Transparent;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultQuery;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.geotools.graph.path.Path;
import org.geotools.referencing.CRS;
import org.mccaughey.geotools.util.Pair;
import org.mccaughey.geotools.util.ShapeFile;
import org.mccaughey.pathGenerator.config.LayerMapping;
import org.mccaughey.service.impl.WFSDataStoreFactoryImpl;
import org.mccaughey.util.TemporaryFileManager;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;



@Controller
@RequestMapping("/agent-paths")
public class PathGeneratorResource {
  static final Logger LOGGER = LoggerFactory.getLogger(PathGeneratorResource.class);
  
//  @Autowired
//  private Environment env;
  // The Java method will process HTTP GET requests
  //@GET
  // The Java method will produce content identified by the MIME Media
  // type "text/plain"
  //@Produces("application/json")
  
  @RequestMapping(value = "/{easting}/{northing}", method = RequestMethod.GET)
  public void getPaths(HttpServletRequest request,HttpServletResponse response, @PathVariable String easting, @PathVariable String northing) throws Exception {
    // Hardcoded inputs for now
//    File networkShapeFile = new File("src/test/resources/graph.shp");
//    FileDataStore networkDataStore = FileDataStoreFinder
//        .getDataStore(networkShapeFile);
//    SimpleFeatureSource networkSource = networkDataStore.getFeatureSource();
	  request.getSession();
	  WFSDataStoreFactoryImpl wfsDataStoreFactoryImpl  = new WFSDataStoreFactoryImpl();
	  DataStore dataStore  = wfsDataStoreFactoryImpl.getDataStore(LayerMapping.RANDOM_DESTINATION_LAYER);
//    SimpleFeatureSource networkSource = getDataSource(LayerMapping.ROAD_SAMPLE_LAYER);
	  SimpleFeatureSource networkSource=dataStore.getFeatureSource(LayerMapping.ROAD_SAMPLE_LAYER);
    //
//    File destinationsFile = new File("src/test/resources/random_destinations.shp");
//    FileDataStore destinationsDataStore = FileDataStoreFinder
//        .getDataStore(destinationsFile);
//    SimpleFeatureIterator features = destinationsDataStore.getFeatureSource()
//        .getFeatures().features();
    //
//    SimpleFeatureIterator features = dataStore.getFeatureSource(LayerMapping.RANDOM_DESTINATION_LAYER)
//            .getFeatures().features();
//    CoordinateReferenceSystem sourceCRS1 = CRS.decode("EPSG:4283");
//    CoordinateReferenceSystem targetCRS1 = CRS.decode("EPSG:28355");
    Query query = new DefaultQuery(LayerMapping.RANDOM_DESTINATION_LAYER);
    query.setCoordinateSystem(CRS.decode("EPSG:28355"));
//    query.setCoordinateSystemReproject(targetCRS1);
    SimpleFeatureIterator features = dataStore.getFeatureSource(LayerMapping.RANDOM_DESTINATION_LAYER)
            .getFeatures(query).features();
    //
    List<Point> destinations = new ArrayList<Point>();
    while (features.hasNext()) {
      SimpleFeature feature = features.next();
      destinations.add((Point) feature.getDefaultGeometry());
    }
    
    //
   // double eastingD= 285752.0;
   // double northingD = 5824386.0;
    GeometryFactory geometryFactory2 = new GeometryFactory(new PrecisionModel(0));
    //283308.0178542186 5902355.348786879
    GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(0));
    Point start = geometryFactory.createPoint(new Coordinate(Double.parseDouble(easting), Double.parseDouble(northing)));
   // Point comparison = geometryFactory2.createPoint(new Coordinate(eastingD, northingD));
   // LOGGER.info("Comparising Geom" + comparison.toString());
    CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:3857"); //same as 900913
    CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:28355");

    MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS);
    Point targetGeometry = (Point) JTS.transform( start, transform);
    
    LOGGER.info("Converted geom: " + targetGeometry.toString());
    
//    Random randomGenerator = new Random();    
//    File file = new File("all_path_nodes_"+randomGenerator.nextInt()+".json"); //TODO: create in memory
    System.out.println("sessionid:"+request.getSession().getId());
    
   synchronized (request.getSession()) {	
	    TemporaryFileManager.deleteAll(request.getSession());
	    File file =TemporaryFileManager.getNew(request.getSession(), "all_path_nodes_", ".json");
	    try{
	    	Pair<List<Path>,FeatureCollection> p =PathGenerator.shortestPaths(networkSource, targetGeometry, destinations,file);	
	    	//
	    	request.getSession().setAttribute("Generated_File_Location", file.getAbsolutePath());
	    	//
	    	File zipfile = ShapeFile.createShapeFileAndReturnAsZipFile(file.getName(),  (SimpleFeatureCollection) p.getTwo(), request.getSession());
	    	request.getSession().setAttribute("Generated_ZipFile_Location", zipfile.getAbsolutePath());
	    	//
		    LOGGER.info("Paths Generated");
		    FileCopyUtils.copy(new FileInputStream(file), response.getOutputStream());
	    } catch (GeneratedOutputEmptyException e) {
	    	TemporaryFileManager.deleteAll(request.getSession());
	    }
	    
   }
  }
  
  
  @RequestMapping(value = "downloadGeneratedOutput", method = RequestMethod.GET)
  public void downloadGeneratedOutput(HttpServletRequest request,HttpServletResponse response) throws Exception {
	  synchronized (request.getSession()) {	
		  if (request.getSession().getAttribute("Generated_File_Location")==null)
			  throw new Exception("No output is generated") ;
		  File file = new File((String) request.getSession().getAttribute("Generated_File_Location")); 
		  response.setContentType("application/x-download");
		  response.setHeader("Content-disposition", "attachment; filename=" + "agent_walkability_output.geojson");
		  FileCopyUtils.copy(new FileInputStream(file), response.getOutputStream());
	  }
  }
  
  @RequestMapping(value = "downloadGeneratedOutputAzShpZip", method = RequestMethod.GET)
  public void downloadGeneratedOutputAzShpZip(HttpServletRequest request,HttpServletResponse response) throws Exception {
	  synchronized (request.getSession()) {	
		  if (request.getSession().getAttribute("Generated_File_Location")==null)
			  throw new Exception("No output is generated") ;
		  File file = new File((String) request.getSession().getAttribute("Generated_ZipFile_Location")); 
		  response.setContentType("application/x-download");
		  response.setHeader("Content-disposition", "attachment; filename=" + "agent_walkability_output.zip");
//		  File zipfile = ShapeFile.createShapeFileAndReturnAsZipFile( file, request.getSession());
		  FileCopyUtils.copy(new FileInputStream(file), response.getOutputStream());
	  }
  }
  
  
  
 /* private SimpleFeatureSource getDataSource(String typeName) throws IOException {
      String getCapabilities = LayerMapping.GEOSERVER_URL+"/ows?service=wfs&version=1.0.0&request=GetCapabilities";
 

    Map<String, String> connectionParameters = new HashMap<String, String>();
    connectionParameters.put("WFSDataStoreFactory:GET_CAPABILITIES_URL", getCapabilities);
    connectionParameters.put("WFSDataStoreFactory:USERNAME", LayerMapping.AURIN_CSDILA_GEOSERVER_USERNAME);
    connectionParameters.put("WFSDataStoreFactory:PASSWORD",  LayerMapping.AURIN_CSDILA_GEOSERVER_PASSWORD);// Step 2 - connection
    DataStore wfsStore = DataStoreFinder.getDataStore( connectionParameters );
    if (wfsStore == null)
	LOGGER.info("Null Data Store");
    String typeNames[] = wfsStore.getTypeNames();
    LOGGER.info(typeNames[0]);
    //typeName = typeNames[0];
    SimpleFeatureType schema = wfsStore.getSchema( typeName );

    // Step 4 - target
    SimpleFeatureSource source = wfsStore.getFeatureSource( typeName );
    System.out.println( "Metadata Bounds:"+ source.getBounds() );
    LOGGER.debug(wfsStore.toString());
  
    
    return source;
    //return source;

  }*/
}
