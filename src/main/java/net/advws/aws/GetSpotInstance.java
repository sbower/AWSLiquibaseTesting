package net.advws.aws;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.SpotPlacement;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

public class GetSpotInstance {
	private static final String MAX_PRICE = "0.01";
	private static final String DEFUALT_AMI = "ami-22db314a";
	private static final String INSTANCE_TYPE = "m3.medium";
	private static final String SECURITY_GROUP = "web/ssh";
	private static final String DEFUALT_ZONE = "us-east-1a";
	private static final String OPEN_REQUEST = "open";
	
	private AmazonEC2 ec2;
	private ArrayList<String> spotInstanceRequestIds;
	private ArrayList<String> instanceIds;
	
	public AmazonEC2 getEc2() {
		return ec2;
	}

	public void setEc2(AmazonEC2 ec2) {
		this.ec2 = ec2;
	}
	
	public GetSpotInstance() {
		// Retrieves the credentials from an AWSCredentials.properties file.
		AWSCredentials credentials;
		try {
		  credentials  = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
		  throw new AmazonClientException(
            "Cannot load the credentials from the credential profiles file. " +
            "Please make sure that your credentials file is at the correct " +
            "location (~/.aws/credentials), and is in valid format.",
            e);
		}
		
		this.ec2 = new AmazonEC2Client(credentials);
	    // Initialize variables.
	    instanceIds = new ArrayList<String>();
	}
	
	public RequestSpotInstancesRequest createReqeust() {
		RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();
		
		//Set the max price we are willing to pay and num of instances to start
    	requestRequest.setSpotPrice(MAX_PRICE);
    	requestRequest.setInstanceCount(Integer.valueOf(1));
    	
    	// Amazon Linux AMI id or another of your choosing.
    	LaunchSpecification launchSpecification = new LaunchSpecification();
    	launchSpecification.setImageId(DEFUALT_AMI);
    	launchSpecification.setInstanceType(INSTANCE_TYPE);
   
    	// Add the security group to the request.
    	ArrayList<String> securityGroups = new ArrayList<String>();
    	securityGroups.add(SECURITY_GROUP);
    	launchSpecification.setSecurityGroups(securityGroups); 
    	
    	//Preferred zone
    	SpotPlacement placement = new SpotPlacement(DEFUALT_ZONE);
    	launchSpecification.setPlacement(placement);
    	
    	requestRequest.setLaunchSpecification(launchSpecification);
    	
    	return requestRequest;

	}
	
	public void makeSpotRequestAndWaitForInstnace() {
		makeSpotRequestAndWaitForInstnace(this.createReqeust());
	}
	
    public void makeSpotRequestAndWaitForInstnace(RequestSpotInstancesRequest rpis) {
      	// Call the RequestSpotInstance API. 
    	RequestSpotInstancesResult requestResult = ec2.requestSpotInstances(rpis);        	
    	List<SpotInstanceRequest> requestResponses = requestResult.getSpotInstanceRequests();
    	
    	// Setup an arraylist to collect all of the request ids we want to watch hit the running state.
    	spotInstanceRequestIds = new ArrayList<String>();
    	
    	// Add all of the request ids to the hashset, so we can determine when they hit the active state.
    	for (SpotInstanceRequest requestResponse : requestResponses) {
    		System.out.println("Created Spot Request: "+requestResponse.getSpotInstanceRequestId());
    		spotInstanceRequestIds.add(requestResponse.getSpotInstanceRequestId());
    	}
    	
    	  // Create a variable that will track whether there are any requests still in the open state.
	    boolean anyOpen;

	    do {
	        // Create the describeRequest with tall of the request id to monitor (e.g. that we started).
	        DescribeSpotInstanceRequestsRequest describeRequest = new DescribeSpotInstanceRequestsRequest();    	
	        describeRequest.setSpotInstanceRequestIds(spotInstanceRequestIds);

	        // Initialize the anyOpen variable to false  which assumes there are no requests open unless
	        // we find one that is still open.
	        anyOpen=false;

	    	try {
	    		// Retrieve all of the requests we want to monitor. 
	    		DescribeSpotInstanceRequestsResult describeResult = ec2.describeSpotInstanceRequests(describeRequest);
	    		List<SpotInstanceRequest> describeResponses = describeResult.getSpotInstanceRequests();

	            // Look through each request and determine if they are all in the active state.
	            for (SpotInstanceRequest describeResponse : describeResponses) {      		
	            		// If the state is open, it hasn't changed since we attempted to request it.
	            		// There is the potential for it to transition almost immediately to closed or
	            		// cancelled so we compare against open instead of active.
	            		if (describeResponse.getState().equals(OPEN_REQUEST)) {
	            			anyOpen = true;
	            			break;
	            		}

	            		// Add the instance id to the list we will eventually terminate.
	        			instanceIds.add(describeResponse.getInstanceId());
	            }
	    	} catch (AmazonServiceException e) {
	            // If we have an exception, ensure we don't break out of the loop.
	    		// This prevents the scenario where there was blip on the wire.
	    		anyOpen = true;
	        }

	    	try {
		    	// Sleep for 30 seconds.
		    	Thread.sleep(30*1000);
	    	} catch (Exception e) {
	    		// Do nothing because it woke up early.
	    	}
	    } while (anyOpen);
    }

    public void cancelSpotRequest() {
	    try {
        	// Cancel requests.
        	CancelSpotInstanceRequestsRequest cancelRequest = new CancelSpotInstanceRequestsRequest(spotInstanceRequestIds);
        	ec2.cancelSpotInstanceRequests(cancelRequest);
        } catch (AmazonServiceException e) {
        	// Write out any exceptions that may have occurred.
            System.out.println("Error cancelling instances");
            System.out.println("Caught Exception: " + e.getMessage());
            System.out.println("Reponse Status Code: " + e.getStatusCode());
            System.out.println("Error Code: " + e.getErrorCode());
            System.out.println("Request ID: " + e.getRequestId());
        }
    }
    
    public void terminateInstances() {
	    try {
        	// Terminate instances.
        	TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest(instanceIds);
        	ec2.terminateInstances(terminateRequest);
    	} catch (AmazonServiceException e) {
    		// Write out any exceptions that may have occurred.
            System.out.println("Error terminating instances");
    		System.out.println("Caught Exception: " + e.getMessage());
            System.out.println("Reponse Status Code: " + e.getStatusCode());
            System.out.println("Error Code: " + e.getErrorCode());
            System.out.println("Request ID: " + e.getRequestId());
        }
    }
    
    public DescribeInstancesResult getInstnaceInfo() {
	    DescribeInstancesRequest di = new DescribeInstancesRequest();
	    di.setInstanceIds(instanceIds);
	    DescribeInstancesResult dir = ec2.describeInstances(di);
	    
	    return dir;
    }
}
